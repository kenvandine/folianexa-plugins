# 01 — Phqen1xWorldEditCraft

Natural-language structure generation for Folia. An operator describes a
building; the plugin produces a real Sponge v3 `.schem`, files it in a permanent
library, and pastes it into the world.

- Package: `io.github.phqen1x.worldeditcraft`
- Plugin name: `Phqen1xWorldEditCraft`
- Depends on: nothing at runtime. Talks to a Lemonade Server over HTTP.
- Published artifact: `phqen1x-worldeditcraft-api` (see [`05`](05-shared-api.md))

## The pipeline

```
/wec generate <prompt>
        │
        │  async scheduler ─────────────────────────────────────────┐
        ▼                                                           │
  1. PromptBuilder      compose system prompt (DSL grammar,         │
                        block vocabulary, hard caps) + user brief   │
        ▼                                                           │
  2. InferenceQueue     one slot on the shared queue                │
        ▼                                                           │
  3. LemonadeClient     POST /api/v1/chat/completions               │
        ▼                                                           │
  4. JsonCoercion       strip prose and code fences, find the       │
                        outermost JSON object, parse with MiniJson  │
        ▼                                                           │
  5. BuildScriptValidator   schema, caps, block IDs, bounds         │
        │                                                           │
        ├── invalid ──▶ repair prompt with the specific errors ─────┤
        │              (up to lemonade.max-attempts, then fail)     │
        ▼                                                           │
  6. BuildScriptInterpreter   rasterize ops → VoxelGrid + palette   │
        ▼                        (pure, deterministic, seeded)      │
  7. SchematicWriter    GZip NBT → schematics/<slug>.schem          │
        ▼                                                           │
  8. SchematicLibrary   index it, write the sidecar                 │
        │  ─────────────────────────────────────────────────────────┘
        ▼
  9. PasteEngine        region scheduler, one task per chunk
                        (only if the operator asked for placement)
```

Steps 1–8 never touch a game thread. Step 9 never touches anything else. That
split is the whole Folia story for this plugin; [`07-folia-safety.md`](07-folia-safety.md)
covers it properly.

## Why not just depend on WorldEdit?

The obvious design is `depend: [WorldEdit]`, build a `Clipboard`, and let
`EditSession` do the pasting. It was rejected, for one reason: **WorldEdit and
FastAsyncWorldEdit do not officially support Folia.**

What exists today is a set of unofficial forks —
[`Euphillya/WorldEdit-Folia`](https://github.com/Euphillya/WorldEdit-Folia)
(self-described as "a minimalist version that does not support everything"),
and experimental FAWE branches pinned to old Minecraft versions with known
broken subsystems. Building a plugin that a FoliaNexa world *requires* on top of
that means inheriting somebody else's fork's bug list and release cadence.

So: **the files are the interop surface, not the code.** This plugin reads and
writes the same standard Sponge v3 `.schem` format WorldEdit uses, so anything
it produces can be loaded with `//schem load` on a Paper server, opened in
Amulet or a schematic viewer, or shared with people who have never heard of
this plugin. But pasting is done by our own engine, which is a few hundred lines
because it only ever has to do one thing well.

If WorldEdit *is* installed, the plugin may optionally delegate pastes to it
through a reflective adapter (`worldedit.delegate-paste: false` by default).
That is a convenience for Paper-not-Folia servers, never a requirement.

## Class layout

★ marks classes with no `org.bukkit` imports — these are the unit-tested ones.
The main class is wiring only, following the convention stated in every existing
plugin in this repo ("Keep this class thin — wiring only").

### Root — `io.github.phqen1x.worldeditcraft`

| Class | Responsibility |
| --- | --- |
| `WorldEditCraftPlugin` | `onEnable` wiring: config, library load, service registration, command executor. Nothing else. |
| `WorldEditCraftCommand` | `/wec` dispatch. Subcommand `switch`, permission check per branch, usage fallback. Mirrors `HungerGamesCommand`. |
| `WorldEditCraftConfig` ★ | Immutable record tree of the whole config. |
| `ConfigLoader` ★ | `FileConfiguration` → `WorldEditCraftConfig`. Static, package-private, private constructor — the shape used by `hungergames/ConfigLoader.java`. |

### `.llm`

| Class | Responsibility |
| --- | --- |
| `LemonadeClient` | JDK `HttpClient` against `/api/v1/chat/completions` and `/api/v1/models`. No `org.bukkit`, but does real I/O — async scheduler only. |
| `LemonadeSettings` ★ | Base URL, path prefix, model, timeouts, sampling parameters. |
| `LemonadeStatus` ★ | Reachable, model list, loaded model, last error, last latency. Backs `/wec status`. |
| `InferenceQueue` | Bounded-concurrency FIFO in front of `LemonadeClient`. Shared with the RPG plugin via the API artifact. |
| `PromptBuilder` ★ | Builds system + user messages. The DSL grammar in the system prompt is *generated from the op registry*, not hand-maintained. |
| `JsonCoercion` ★ | Pull a JSON object out of whatever the model actually returned. |
| `MiniJson` ★ | Ported from `folianexa-stats`. Parser + writer over `Map`/`List`/`String`/`Double`/`Boolean`. |

### `.dsl`

| Class | Responsibility |
| --- | --- |
| `BuildScript` ★ | `name`, `size`, `seed`, `palette`, `ops`, `markers`. |
| `BuildOp` ★ | Sealed interface; one record per operation. |
| `OpRegistry` ★ | Name → parser → validator → executor. Single source of truth for the op list; the prompt and the docs both derive from it. |
| `BuildScriptParser` ★ | `Map` → `BuildScript`. Structural errors only. |
| `BuildScriptValidator` ★ | Semantic checks: caps, bounds, palette references, block ID resolution. Returns a list of `ValidationIssue`, never throws. |
| `ValidationIssue` ★ | Severity, op index, human-readable message, and a **model-readable** message for the repair prompt. |
| `BuildScriptInterpreter` ★ | Executes ops against a `VoxelGrid`. Pure and deterministic. |

### `.voxel`

| Class | Responsibility |
| --- | --- |
| `VoxelGrid` ★ | `short[]` palette indices, YZX order, plus a sparse `Map<Integer, Map<String,Object>>` of block-entity payloads. |
| `VoxelPalette` ★ | Bidirectional `BlockStateRef` ↔ index, insertion-ordered. |
| `BlockStateRef` ★ | A parsed `minecraft:oak_stairs[facing=north,half=top]`: namespace, id, sorted property map. Canonical `toString`. |
| `Bounds` ★ | Integer AABB with clamping helpers. |
| `Transform` ★ | Rotate 90/180/270 about Y, flip X/Z. **Rewrites direction-valued block-state properties** — a rotated staircase must actually face the new way. |

### `.schem`

| Class | Responsibility |
| --- | --- |
| `NbtTag` ★ | Sealed interface: `Byte`, `Short`, `Int`, `Long`, `Float`, `Double`, `ByteArray`, `String`, `List`, `Compound`, `IntArray`, `LongArray`. |
| `NbtWriter` ★ | `NbtTag` → `DataOutput`, big-endian, Java modified UTF-8. |
| `NbtReader` ★ | The inverse, with a depth limit and a size cap. |
| `VarIntCodec` ★ | LEB128 write/read over a `ByteArrayOutputStream` / cursor. |
| `SchematicWriter` ★ | `VoxelGrid` + `SchematicMeta` → GZip'd NBT bytes. |
| `SchematicReader` ★ | The inverse. Accepts v2 as well as v3 on read; only ever writes v3. |
| `SchematicMeta` ★ | Name, author, date, original prompt, model, dimensions, tags, checksum. |

### `.library`

| Class | Responsibility |
| --- | --- |
| `SchematicLibrary` | Owns the directory. Save, load, list, rename, delete, import, export. File I/O — async scheduler only. |
| `SchematicIndex` ★ | In-memory catalogue with slug/tag/text search. Serialized to `index.json`. |
| `SchematicRecord` ★ | One index entry. |
| `SchematicQuery` ★ | Filter + sort + page. |

### `.paste`

| Class | Responsibility |
| --- | --- |
| `PastePlan` ★ | Pure. Applies a `Transform` and an origin, buckets placements by chunk key, splits buckets into per-tick batches, and separates pass 1 from pass 2. Fully unit-testable with no world. |
| `PasteEngine` | Executes a `PastePlan` on the region scheduler. |
| `PasteJob` | One in-flight paste: id, progress, cancel flag, undo journal handle. |
| `UndoJournal` | Records prior block states before each batch; replays in reverse. |
| `PasteProgress` ★ | Placed / total / elapsed / ETA. |

### `.api` — the published artifact

Covered in [`05-shared-api.md`](05-shared-api.md). Nothing outside `.api` is
part of the published surface.

## The `.schem` writer

Per the [Sponge Schematic v3 spec](https://github.com/SpongePowered/Schematic-Specification/blob/master/versions/schematic-3.md).
GZip-compressed NBT. The root compound is unnamed and holds a single `Schematic`
compound:

```
Schematic (Compound)
├─ Version        Int    = 3
├─ DataVersion    Int    = the Minecraft data version (1.21.4's)
├─ Metadata       Compound { Name: String, Author: String, Date: Long }
├─ Width          Short  (unsigned)
├─ Height         Short  (unsigned)
├─ Length         Short  (unsigned)
├─ Offset         IntArray[3]
└─ Blocks         Compound
   ├─ Palette       Compound  "minecraft:oak_stairs[facing=north,half=top]" → Int
   ├─ Data          ByteArray varint-packed palette indices
   └─ BlockEntities List of Compound { Pos: IntArray[3], Id: String, Data: Compound }
```

Three details are where the bugs will be, so they are stated explicitly:

**Ordering.** `Data` is indexed `y * Width * Length + z * Width + x` — YZX, X
varying fastest. Getting this wrong produces a structure that is transposed
rather than broken, which is exactly the sort of thing that survives a careless
eyeball check.

**Varint packing.** Each palette index is written as unsigned LEB128: seven bits
per byte, low group first, high bit set on every byte but the last. Indices
below 128 take one byte, which is the common case and the reason the format does
this at all. `VarIntCodec` is thirty lines and gets its own test class.

**Unsigned shorts.** `Width`/`Height`/`Length` are NBT `Short`s read as
unsigned, so the format's real dimension ceiling is 65535. Our own cap
(`generation.max-dimension`, default 128) is far below that and is about
generation sanity, not the format.

Palette strings are the same canonical block-state notation Bukkit's
`Bukkit.createBlockData(String)` accepts, which makes the read path a one-liner
plus a cache. Properties are **sorted alphabetically** when generating the
string, so the same block state always produces the same palette key and the
same schematic hashes identically across runs.

On paste, a palette entry that no longer resolves on the running server (a
renamed block, a modded ID in an imported file) is reported once per schematic
with its index and count, and substituted per `paste.unknown-block` — `air`,
`stone`, or `abort`.

## The paste engine

This is the piece that replaces WorldEdit, and it is a direct descendant of
`campus-lobby/src/main/java/io/github/kenvandine/campuslobby/SceneBuilder.java:74-87`,
which already does the core move: bucket placements into a
`Map<Long, List<Placement>>` keyed by `chunkKey(x >> 4, z >> 4)`, then submit one
`Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, …)` per bucket.

What this engine adds over `SceneBuilder`:

**A per-tick budget.** `SceneBuilder` places a whole chunk's worth of blocks in
one task, which is fine for a lobby and not fine for a 128³ structure. Buckets
are split into batches of `paste.blocks-per-tick` (default 2048); each batch
re-submits the next one on completion. A region tick is never held for an
unbounded amount of work.

**Two passes.** Pass 1 places solids with `setBlockData(data, false)` — physics
suppressed, which is what `SceneBuilder` already does with
`setType(Material.AIR, false)`. Pass 2 places everything that needs its
neighbours to already exist: torches, buttons, levers, doors, beds, banners,
rails, redstone, gravity blocks, and all block entities with their payloads.
Within a chunk, region tasks run FIFO in submission order — the property
`SceneBuilder` already relies on for its clear-then-build ordering — so pass 2 is
simply submitted after pass 1 for each chunk. Across chunks, pass 2 for a chunk
is submitted only once every pass-1 batch that could neighbour it has completed,
tracked with the same counter-based fan-in idiom as
`FoliaNexaStatsPlugin#runReportCycle`.

**An undo journal.** Before each batch runs, the blocks it is about to overwrite
are read on that region's own thread and appended to `undo/<jobId>.dat`. `/wec
undo` replays the journal in reverse through the same chunk-bucketed path.
`paste.undo-history` (default 10) journals are kept per world.

**Cancel and progress.** `/wec cancel` sets the job's cancel flag; batches check
it before running and the job unwinds. The requesting operator gets a boss bar
with placed/total and an ETA.

**Optional volume clear.** `paste.clear-volume-first` reuses `SceneBuilder`'s
`scheduleClear` approach — set the target volume to air and remove non-player
entities inside it — for the case where a structure is being dropped onto
terrain rather than into empty space.

## Commands

All operator-facing. Registered the classic way in `plugin.yml` with
`getCommand("wec").setExecutor(...)`; no Brigadier, matching every other plugin
in this repo.

| Command | What |
| --- | --- |
| `/wec generate <prompt…>` | Generate a schematic. `--size WxHxL`, `--name <slug>`, `--paste` to place it immediately at your position. |
| `/wec regen <name> <feedback…>` | Re-run generation with the *original build script* plus your feedback as context. Cheaper and far more coherent than starting over. |
| `/wec list [page] [--tag t] [--search s]` | Browse the library. |
| `/wec info <name>` | Dimensions, block count, the prompt that made it, the model that made it, when. |
| `/wec preview <name>` | Paste into a scratch area with `--temporary`, auto-undone after `paste.preview-seconds`. |
| `/wec paste <name> [--rot 90] [--flip x] [--no-air] [--at x y z]` | Place it. Defaults to your position, your facing. |
| `/wec undo` | Reverse your most recent paste in this world. |
| `/wec cancel` | Cancel your in-flight paste or generation. |
| `/wec rename <name> <new>` · `/wec tag <name> <tags…>` · `/wec delete <name>` | Library upkeep. |
| `/wec import <file>` · `/wec export <name>` | Move `.schem` files in and out. Import accepts anything WorldEdit wrote, v2 or v3. |
| `/wec status` | Lemonade reachability, model list, queue depth, last error. The first thing to run when generation misbehaves. |
| `/wec reload` | Reload `config.yml`. |

Permissions, all `default: op` — the plugin is explicitly an operator tool:

| Node | Grants |
| --- | --- |
| `worldeditcraft.generate` | `generate`, `regen` |
| `worldeditcraft.paste` | `paste`, `preview`, `undo`, `cancel` |
| `worldeditcraft.library` | `list`, `info`, `rename`, `tag`, `delete`, `import`, `export` |
| `worldeditcraft.admin` | `status`, `reload` |

## Configuration (`config.yml`)

```yaml
lemonade:
  base-url: "http://lemonade.local:13305"   # the LAN inference host
  api-path: "/api/v1"                       # Lemonade's canonical prefix; "/v1" also works
  model: ""                                 # blank = first model from /api/v1/models
  api-key: ""                               # sent as a bearer token when non-blank
  connect-timeout-seconds: 10
  request-timeout-seconds: 180
  temperature: 0.4
  top-p: 0.9
  max-tokens: 8192
  max-attempts: 3                           # 1 initial + 2 repair rounds
  max-concurrent-requests: 2                # shared with Phqen1xRPG
  queue-capacity: 32

generation:
  max-dimension: 128                        # per axis
  max-volume: 400000                        # W*H*L, checked before rasterizing
  max-ops: 400
  default-size: [32, 24, 32]
  block-policy: vanilla                     # vanilla | allowlist | any
  block-allowlist: []
  keep-failed-responses: true               # write rejected model output for debugging

paste:
  blocks-per-tick: 2048
  clear-volume-first: false
  unknown-block: air                        # air | stone | abort
  undo-history: 10
  max-concurrent-jobs: 2
  preview-seconds: 60

library:
  directory: "schematics"
  max-entries: 500

worldedit:
  delegate-paste: false                     # use WorldEdit's API if it happens to be installed
```

| Key | Default | Why it exists |
| --- | --- | --- |
| `lemonade.base-url` | `http://lemonade.local:13305` | The one thing every deployment must set. 13305 is Lemonade's own default port. |
| `lemonade.model` | *(blank)* | Blank means "whatever is loaded" — asks `/api/v1/models` and takes the first. Pin it once you know which model behaves. |
| `lemonade.temperature` | `0.4` | Low. Structured output wants determinism; creativity belongs in the prompt, not the sampler. |
| `lemonade.max-attempts` | `3` | The repair loop. Past three rounds a model that is going to fail keeps failing, and each round costs a full inference. |
| `lemonade.max-concurrent-requests` | `2` | One box serves both plugins. This is the guard against thrashing it. |
| `generation.max-*` | 128 / 400000 / 400 | Hallucination guards. A model will cheerfully specify a 4000-block cube. |
| `generation.block-policy` | `vanilla` | Reject anything outside the vanilla registry, so a made-up block ID fails validation and triggers a repair rather than becoming air at paste time. |
| `paste.blocks-per-tick` | `2048` | **A guess.** Profile it. This is the single most important number to tune on real hardware. |
| `paste.unknown-block` | `air` | What to do with a palette entry the server cannot resolve. |

## Storage layout

```
plugins/Phqen1xWorldEditCraft/
  config.yml
  index.json                     the catalogue; rebuildable by rescanning
  schematics/
    ruined_forge_hall.schem      the structure
    ruined_forge_hall.json       prompt, build script, model, author, tags, checksum
  undo/
    <jobId>.dat
  failed/
    <timestamp>-<slug>.txt       raw model output that failed validation
```

The sidecar keeping the **original build script** is what makes `/wec regen`
work well: refinement edits a structured document the model already produced,
rather than asking it to re-imagine the whole thing from a slightly different
sentence.

## Test plan

JUnit 5, no mocking library, no MockBukkit — the convention this repo states as
a virtue in `folianexa-stats/README.md`. Every ★ class above is reachable
without a server.

| Test | Asserts |
| --- | --- |
| `VarIntCodecTest` | Round-trips 0, 1, 127, 128, 255, 16383, 16384, `Integer.MAX_VALUE`. Byte-exact expected output for the boundary cases. |
| `NbtWriterTest` / `NbtReaderTest` | Every tag type round-trips. Nested compounds and lists. Modified-UTF-8 for non-ASCII names. Depth limit rejects a bomb. |
| `SchematicRoundTripTest` | Grid → bytes → grid is identical. Palette order stable. YZX indexing verified against a hand-computed asymmetric 3×4×5 fixture — *the* test that catches transposition. |
| `BlockStateRefTest` | Parses `minecraft:stone`, `minecraft:oak_stairs[facing=north,half=top]`. Sorts properties. Rejects malformed input. |
| `TransformTest` | Rotation is a bijection; four 90° rotations are identity. Direction properties rewrite correctly (`facing=north` + rot 90 → `facing=east`). Flips handle `axis=x`. |
| `BuildScriptParserTest` | Every op parses. Unknown op names are collected, not thrown. |
| `BuildScriptValidatorTest` | Over-cap dimensions, volumes and op counts are caught. Out-of-bounds ops clamp rather than fail. Unknown palette keys and block IDs are reported with a model-readable message. |
| `BuildScriptInterpreterTest` | Each op produces the expected voxels on a small grid. `scatter` with a fixed seed is reproducible across runs and JVMs. |
| `JsonCoercionTest` | Extracts JSON from a fenced block, from prose-then-JSON, from JSON-then-prose. Handles braces inside strings. Fails cleanly on no JSON at all. |
| `MiniJsonTest` | Ported wholesale from `folianexa-stats`. |
| `PastePlanTest` | Bucketing assigns each placement to the right chunk key. Batches respect the budget. Pass 2 contains exactly the attachment-sensitive blocks. Transform + origin compose correctly. |
| `SchematicIndexTest` | Search, tag filter, paging, dedupe by checksum. |
| `LemonadeClientTest` | Against a real `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0` — the pattern in `folianexa-stats/src/test/java/.../HttpMgmtClientTest.java`. Asserts the exact request body sent, handles 200 / 404 / 500 / malformed JSON / connection refused, and confirms failures are logged and surfaced rather than thrown into a scheduler. |
| `RepairLoopTest` | A fake server that returns garbage, then prose, then valid JSON. Asserts exactly three requests, that round 2 and 3 carry the prior validation errors, and that `max-attempts` is honoured. |

Not covered by tests, and must be checked by hand on a live server: the paste
engine's Folia behaviour, the boss-bar progress, and — the important one —
**opening a generated `.schem` in real WorldEdit**.

## Design notes

**Why hand-roll NBT?** Because this repo does not shade dependencies. The
rationale is written into `folianexa-stats`'s `MiniJson` javadoc: a hand-rolled
minimal implementation means "nothing here to shade or risk a classpath conflict
with another plugin's JSON library". NBT is a simple binary format —
thirteen tag types, big-endian, one string encoding quirk — and the alternative
is either shading a library or depending on WorldEdit for its NBT
implementation, which reintroduces exactly the dependency this design avoids.

**Why the DSL rather than block lists?** Covered properly in
[`03-buildscript-dsl.md`](03-buildscript-dsl.md), but the short version: a 40×25×20
hall is 20,000 blocks. No local model emits that coherently, and the ones that
try will exhaust their context. The same hall is about 25 DSL operations.

**Why is generation deterministic?** Every random draw in the interpreter comes
from the script's own `seed`. That means a build script is a complete, portable
description of a structure — the same script always produces the same `.schem`.
It makes the interpreter unit-testable, it makes `/wec regen` predictable, and
it is what lets a campaign pack rebuild byte-identical structures on another
server (see [`06-campaign-pack-format.md`](06-campaign-pack-format.md)).

**Why pin HTTP/1.1?** `folianexa-stats/src/main/java/io/github/kenvandine/folianexastats/HttpMgmtClient.java:44-63`
documents a live-confirmed failure: the JDK `HttpClient` prefers HTTP/2, which
over plaintext means attempting an `Upgrade: h2c` handshake on every request,
and against a uvicorn/h11 server that intermittently corrupted request framing
on reused connections — producing alternating "422 Field required, input: null"
and bare "400 Invalid HTTP request" rather than any clean failure. Lemonade
Server is also a uvicorn application. Pin `HttpClient.Version.HTTP_1_1` and skip
the upgrade attempt entirely. This is not speculative; it cost somebody a
debugging session already.

## What's real vs. unverified

Not built. Not compiled. Not run.

Grounded: the repository conventions and the two code patterns this design
leans on hardest — `SceneBuilder`'s chunk-bucketed region scheduling and
`HttpMgmtClient`'s HTTP client configuration — were read from the real source in
this repo and are cited by file and line. The `.schem` structure is from the
published Sponge v3 specification. Lemonade's endpoints, port and parameter
support are from its published API documentation.

Unverified: that a locally-served model produces usable build scripts at any
particular success rate; that `paste.blocks-per-tick: 2048` is a sensible number
on real hardware; that a 128³ paste completes in a reasonable time or at all;
and that the `.schem` files this design describes actually open in WorldEdit.
That last one is the highest-value early check and it needs nothing but a Paper
server with WorldEdit and one generated file.

## License

MIT
