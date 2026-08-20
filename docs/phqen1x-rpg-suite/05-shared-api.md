# 05 — The shared API

`phqen1x-worldeditcraft-api` — the dependency-free artifact Phqen1xRPG compiles
against, and the `ServicesManager` handshake that connects the two plugins at
runtime.

## Why an artifact rather than reflection

The two plugins will ship as separate jars from separate repositories on
separate release cadences. Three ways to bridge that:

**Reflection.** No build coupling at all. Also no type safety, no refactor
safety, no compiler help when a signature changes, and every mistake surfaces as
a runtime `NoSuchMethodException` on a scheduler thread at the worst moment.

**One merged plugin.** Simplest wiring, and it destroys the requirement that
the schematic generator is independently useful to any operator who does not
want an RPG.

**A small published API artifact.** Chosen. The RPG compiles against a stable,
tiny, dependency-free interface jar and looks the implementation up through
Bukkit's `ServicesManager` at runtime. Type-safe, versionable, and the
compiler catches breakage.

The artifact is deliberately minimal — interfaces and records only, no
implementation, no Bukkit-version-sensitive code beyond the handful of types it
must name. It changes rarely and slowly.

## The handshake

WorldEditCraft registers, in `onEnable()`:

```java
Bukkit.getServicesManager().register(
        WorldEditCraftService.class, impl, this, ServicePriority.Normal);
Bukkit.getServicesManager().register(
        InferenceService.class, inference, this, ServicePriority.Normal);
```

The RPG declares `depend: [Phqen1xWorldEditCraft]` in its `plugin.yml` — a hard
dependency, so load order is guaranteed — and looks both up:

```java
RegisteredServiceProvider<WorldEditCraftService> rsp =
        Bukkit.getServicesManager().getRegistration(WorldEditCraftService.class);
if (rsp == null) { /* disable with a clear message */ }
```

**Version check on lookup.** `WorldEditCraftService.apiVersion()` returns the
API version the implementation was built against. The RPG compares it to the
version it compiled against and, on a major mismatch, logs loudly and disables
itself rather than failing strangely three hours into someone's session. This is
the mitigation for the version-skew risk in
[`00-project-plan.md`](00-project-plan.md#risks).

## `WorldEditCraftService`

```java
package io.github.phqen1x.worldeditcraft.api;

public interface WorldEditCraftService {

    /** API version this implementation was built against, e.g. "1.0". */
    String apiVersion();

    /** Generate a structure from a natural-language brief. */
    CompletableFuture<SchematicHandle> generate(GenerateRequest request);

    /** Look up an existing library entry by slug. */
    Optional<SchematicHandle> find(String slug);

    /** Search the library. */
    List<SchematicHandle> list(SchematicQuery query);

    /** Place a schematic in the world. */
    CompletableFuture<PasteResult> paste(SchematicHandle handle, PasteRequest request);

    /** Reverse a completed paste. */
    CompletableFuture<Void> undo(UUID jobId);

    /** Cancel an in-flight generate or paste. */
    boolean cancel(UUID jobId);

    /** Import a .schem file into the library, returning its handle. */
    CompletableFuture<SchematicHandle> importSchematic(Path file, String slug);

    /** Write a library entry out to a file. */
    CompletableFuture<Path> exportSchematic(SchematicHandle handle, Path target);

    /** Inference server reachability, model, queue depth. */
    LemonadeStatus status();
}
```

Everything that can be slow returns a `CompletableFuture`. Nothing in this
interface blocks, and the RPG never has to think about which scheduler it is on
when it calls — completion callbacks are the caller's problem to route, which
[`07-folia-safety.md`](07-folia-safety.md) covers.

### Records

```java
public record GenerateRequest(
        String brief,              // natural language — the whole point
        Optional<int[]> sizeHint,  // [W,H,L]; the model may choose otherwise
        List<String> tags,         // library tags, e.g. ["campaign:drowned-coast", "kind:shrine"]
        OptionalLong seed,         // for reproducibility; absent = derived from the brief
        Optional<String> slug,     // preferred library name
        boolean reuseExisting      // if a tagged match exists, return it instead of generating
) {}

public record SchematicHandle(
        String slug,
        Path file,
        int width, int height, int length,
        int blockCount,
        List<String> markerIds,    // which anchors exist, without their positions
        String checksum,
        SchematicMeta meta         // prompt, model, author, created, tags
) {}

public record PasteRequest(
        String worldName,
        int x, int y, int z,       // the origin corner
        int rotationDegrees,       // 0, 90, 180, 270
        boolean flipX, boolean flipZ,
        boolean skipAir,
        boolean clearVolumeFirst,
        Optional<UUID> requester   // for progress reporting; absent = silent
) {}

public record PasteResult(
        UUID jobId,
        boolean completed,         // false if cancelled
        int blocksPlaced,
        Map<String, int[]> markers, // marker id → world [x,y,z], post-transform
        List<String> warnings       // unresolved blocks, clamps
) {}
```

`PasteResult.markers` is the single most important field in this document. It is
the entire mechanism by which Phqen1xRPG places a boss inside a lair it did not
design: it asks for a building in English, gets a handle, pastes it, and is told
`boss → [1043, 62, -288]`. It never parses geometry, never scans for a suitable
room, and never needs to understand what was built. See
[`03-buildscript-dsl.md`](03-buildscript-dsl.md#markers--the-entire-rpg-integration-surface).

`reuseExisting` on `GenerateRequest` is the graceful-degradation path: a campaign
that needs a shrine and finds a tagged shrine already in the library can use it
rather than spending inference — and, more importantly, rather than failing when
the inference server is unreachable.

## `InferenceService`

```java
public interface InferenceService {

    /** Free-form chat completion. */
    CompletableFuture<String> complete(InferenceRequest request);

    /**
     * Completion that must return a JSON object. Extracts, parses forgivingly,
     * runs the caller's validator, and repairs-and-retries on failure up to the
     * configured attempt limit.
     */
    CompletableFuture<Map<String, Object>> completeJson(
            InferenceRequest request, JsonValidator validator);

    LemonadeStatus status();
    int queueDepth();
}

@FunctionalInterface
public interface JsonValidator {
    /** Empty means valid. Each string is a model-readable instruction. */
    List<String> validate(Map<String, Object> json);
}
```

```java
public record InferenceRequest(
        String systemPrompt,
        List<Message> messages,
        OptionalDouble temperature,
        OptionalInt maxTokens,
        Priority priority       // INTERACTIVE or BACKGROUND
) {
    public record Message(String role, String content) {}
    public enum Priority { INTERACTIVE, BACKGROUND }
}
```

Two reasons this lives in the shared artifact rather than being reimplemented in
the RPG.

**The queue.** One inference box serves both plugins. If each opened its own
pipeline they would fight over one GPU and both would get slower than either
alone. `InferenceService` is backed by a single shared `InferenceQueue`, and
`Priority` is what stops a nine-stage background campaign generation from
starving an operator watching a progress bar.

**The repair loop.** Extraction, forgiving parsing, validation and repair
([`04-lemonade-integration.md`](04-lemonade-integration.md#getting-json-out)) is
subtle code that took real thought. Writing it twice means fixing its bugs
twice. `completeJson` takes the caller's validator and owns everything else, so
the RPG's nine stage validators plug straight in.

## Versioning

The artifact is versioned independently of both plugins and moves conservatively.

| Change | Version |
| --- | --- |
| New method with a default implementation, new optional record field | Minor. |
| New method without a default, changed signature, removed member | Major. |
| Documentation, javadoc | Patch. |

`apiVersion()` returns `major.minor`. The RPG refuses to start on a major
mismatch and warns on a minor one where the implementation is older than it
compiled against.

Records use canonical constructors and are extended by adding fields with
static factory overloads, so an added field does not break an existing caller.

## Consuming it

WorldEditCraft's build publishes the artifact:

```kotlin
// in phqen1x-worldeditcraft
sourceSets { create("api") }

tasks.register<Jar>("apiJar") {
    archiveBaseName.set("phqen1x-worldeditcraft-api")
    from(sourceSets["api"].output)
}
```

The RPG consumes it `compileOnly` — it is provided at runtime by the
WorldEditCraft plugin already on the classpath, and must not be shaded into the
RPG jar, or two incompatible copies of the interfaces end up loaded and the
service lookup returns something that fails an `instanceof` for no visible
reason:

```kotlin
// in phqen1x-rpg
dependencies {
    compileOnly("io.papermc.paper:paper-api:1.21.4-R0.1-SNAPSHOT")
    compileOnly("io.github.phqen1x:phqen1x-worldeditcraft-api:1.0")
}
```

This also keeps both plugins consistent with the repo's no-shaded-dependencies
stance — the shadow jar exists to produce a single plugin jar, not to bundle
third-party code.

## Test plan

| Test | Asserts |
| --- | --- |
| `ApiCompatibilityTest` | In the WorldEditCraft repo: the implementation satisfies every interface method, and `apiVersion()` matches the artifact's own version. |
| `ServiceLookupTest` | In the RPG repo: a stub `WorldEditCraftService` registered in a fake `ServicesManager` is found; a missing registration disables cleanly with a readable message; a major version mismatch refuses to start. |
| `MarkerTransformTest` | `PasteResult.markers` are correct under every rotation and flip combination, composed with a non-zero origin. This is the one that will actually catch bugs — an off-by-one or a wrong rotation here puts a boss inside a wall. |
| `InferenceQueuePriorityTest` | An `INTERACTIVE` request submitted behind twenty `BACKGROUND` ones runs next. |
| `ReuseExistingTest` | `generate(reuseExisting=true)` with a tagged library match returns the existing handle and makes no inference call. |

## Design notes

**Why is the API surface this small?** Because it is the thing that is hardest
to change once both plugins ship. Eight methods on one interface and four on
another is enough for everything in [`02-rpg-design.md`](02-rpg-design.md), and
anything speculative added now is something that has to be supported forever.

**Why does the RPG not get access to the library's internals?** Deliberately.
The RPG asks for buildings in English and is told where the anchors are. If it
could read voxels it would start depending on geometry, and then a change to the
generator would break the game rather than just changing how a building looks.

**Why expose `InferenceService` from the *WorldEditCraft* plugin?** It is
slightly odd that the structure plugin owns the general inference service. The
alternative — a third plugin whose only job is the Lemonade connection — is
cleaner in principle and worse in practice: another jar to version, release and
install, for one class. WorldEditCraft owns it because it is the plugin that
must exist for either feature to work.

## What's real vs. unverified

Not built. The `ServicesManager` pattern is standard Bukkit and used widely;
`folianexa-stats` already does the adjacent thing of consuming other plugins'
APIs through soft dependencies, so the mechanism is not novel here.

Unverified: whether this API surface is actually sufficient. It was designed
against the RPG design in [`02`](02-rpg-design.md), which is itself unbuilt, so
the honest expectation is one or two additions during M5. Better to add a method
then than to guess at five now.

## License

MIT
