# 08 — Roadmap

Six milestones, each with an exit criterion that is a demonstration rather than
a feeling.

## Sequencing principle

M1 has no dependency on Minecraft, on Folia, or on Lemonade. That is
deliberate. It is the part most likely to contain silent bugs — varint packing,
YZX ordering, block-state round-tripping — and the part easiest to test in
isolation. Producing a `.schem` that real WorldEdit genuinely opens, before any
of the interesting work starts, de-risks everything after it.

The second-riskiest unknown — whether a locally-served model actually produces
usable build scripts — is answered in M2, still before anything RPG-shaped
exists. If the answer there is bad, the DSL or the model choice changes and very
little work is lost.

## M1 — Schematic core

Pure Java. No Bukkit, no plugin, no network.

- `NbtTag` / `NbtWriter` / `NbtReader` / `VarIntCodec`
- `VoxelGrid`, `VoxelPalette`, `BlockStateRef`, `Bounds`, `Transform`
- `SchematicWriter` / `SchematicReader` (Sponge v3 write, v2+v3 read)
- The full DSL: `BuildOp`, `OpRegistry`, `BuildScriptParser`,
  `BuildScriptValidator`, `BuildScriptInterpreter`
- `MiniJson` and `JsonCoercion`, ported and extended

**Exit criteria**

1. A hand-written build script rasterizes, writes to `.schem`, and **opens in
   real WorldEdit on a Paper server** via `//schem load` + `//paste`, looking
   like what the script describes.
2. `SchematicRoundTripTest` passes on an asymmetric 3×4×5 fixture — the test
   that catches transposition.
3. Four 90° rotations are identity, and a rotated staircase faces the right way.
4. `scatter` with a fixed seed produces identical output across two JVM runs.

Criterion 1 is the milestone. Everything else is scaffolding for it.

## M2 — Phqen1xWorldEditCraft

The plugin, end to end.

- Plugin skeleton to this repo's conventions: Gradle KTS, Java 21, `compileOnly`
  paper-api `1.21.4-R0.1-SNAPSHOT`, shadow 8.3.5, `plugin.yml` with
  `folia-supported: true`, `version: '${version}'`
- `LemonadeClient`, `InferenceQueue`, `PromptBuilder`, the repair loop
- `SchematicLibrary`, `SchematicIndex`, sidecars, `index.json`
- `PastePlan` and `PasteEngine`, undo, cancel, progress
- The `/wec` command surface and permissions

**Exit criteria**

1. `/wec generate a small stone watchtower with a wooden roof` produces a
   schematic and pastes it, on a live Folia server, with no thread-check
   failures in the log.
2. **Twenty varied prompts, measured.** How many produce a valid script first
   try, how many after repair, how many fail. Write the number down — it is the
   input to every later decision about model choice and prompt design.
3. A 96×48×96 paste completes without holding a region tick long enough to be
   visible, and `/wec undo` restores the area exactly.
4. `/wec regen <name> "make it deepslate and twice as tall"` produces a
   recognisable variant rather than an unrelated building.
5. `/wec status` correctly reports a reachable server, an unreachable one, and a
   wrong `api-path`.

Criterion 2 is the real gate. If first-try validity is under about half and
post-repair success under about 80%, stop and fix that — with a different model,
a tighter prompt, or a smaller op vocabulary — before building anything on top.

## M3 — Shared API

- The `phqen1x-worldeditcraft-api` source set and jar
- `ServicesManager` registration and the version handshake
- `InferenceService` with priority queueing

**Exit criteria**

1. A throwaway consumer plugin looks up `WorldEditCraftService`, generates,
   pastes, and gets back correct marker world-coordinates.
2. `MarkerTransformTest` passes for all eight rotation/flip combinations
   composed with a non-zero origin.
3. An `INTERACTIVE` request submitted behind twenty `BACKGROUND` ones runs next.
4. A major API version mismatch makes the consumer refuse to start with a
   readable message.

## M4 — Campaign generator

Content generation. No gameplay yet.

- The nine stages, their prompts, their validators
- Context compaction between stages
- Resumable generation with per-stage caching
- `Campaign` model, `CampaignStore`, packs written incrementally
- `WorldLayout` and `SiteBuilder`

**Exit criteria**

1. `/rpg campaign generate a drowned coastal kingdom, folk-horror, three acts`
   completes and writes a valid pack.
2. The quest graph is a DAG, every quest is reachable from act 1, and every
   objective target exists — asserted by `QuestGraphTest` against the real
   generated output, not a fixture.
3. Every site's build brief produced a schematic, and every schematic has the
   markers its site kind requires.
4. Killing the server at stage 7 and re-running resumes at stage 7.
5. Generation time recorded end to end. If it is over about forty minutes,
   parallelise the independent stages before M5.

## M5 — RPG runtime

The game.

- `QuestEngine` and the eight `ObjectiveTracker`s
- `DialogueEngine`, `NpcManager`, `MobDirector`
- `ClassManager` and `AbilityPrimitive`s
- `LootService`, `RewardService`
- `BossController`
- `CodexGui`, `PartyManager`, `/rpg where`

**Exit criteria**

1. A player joins, picks a class, and plays a generated campaign from act 1 to
   the final boss without an operator intervening.
2. All eight objective types complete correctly, and survive a server restart
   mid-progress.
3. A boss encounter runs its phases in order, at the right thresholds, once
   each, with players in two different regions.
4. Two players in a party both get credit for a shared kill.
5. No thread-check failures across a full playthrough.

## M6 — Replay and share

- Pack export/import, slim and pinned modes
- Load-time validation and migration hooks
- Multiple runs per campaign
- `/rpg campaign bake`
- Offline mode

**Exit criteria**

1. Export a campaign, import it on a **different server with a different world
   seed**, and play it. The world is coherent; the quests work.
2. `OfflineModeTest` passes — a full load-start-play cycle with the Lemonade
   base URL pointed at a port that fails the test if anything connects.
3. `SlimPackTest` passes: export slim, import with `--rebuild`, and the rebuilt
   `.schem` is byte-identical to the original.
4. Two concurrent runs of one campaign advance independently.
5. A hand-edited pack is detected by checksum and named in the warning.

## The question that is not a milestone

**Does campaign #3 still feel new?**

Named as a risk in [`00-project-plan.md`](00-project-plan.md#risks) and repeated
here because it is the one thing on this page that no test will answer. If every
campaign bottoms out in the same eight objective types and the same eight boss
mechanics, the third one may read as the first with the nouns swapped.

Generate three campaigns with genuinely different themes after M5. Play enough
of each to have an opinion. If they feel samey, the fix is composition depth —
multi-objective quest chains, branching prerequisites, phase combinations, class
mixing — not more enum values. Adding a ninth objective type is the tempting
move and probably the wrong one.

Budget time for this. It is the difference between a technically impressive
plugin and one people want to play.

## Testing throughout

Per this repo's conventions: JUnit 5, no mocking library, no MockBukkit, and
HTTP tested against a real `com.sun.net.httpserver.HttpServer` on `127.0.0.1:0`
— the pattern in `folianexa-stats/src/test/java/.../HttpMgmtClientTest.java`.
Per-milestone test lists are in each design doc.

Two gaps worth planning around:

**Nothing Folia-threaded is unit-testable.** The response is architectural —
push every decision into a pure class (`PastePlan`, `WorldLayout`,
`ObjectiveTracker`) and keep the Bukkit-touching classes thin enough to review
by eye. [`07-folia-safety.md`](07-folia-safety.md) lists the live-server checks
that have to substitute.

**Nothing about model quality is unit-testable either.** A fake `HttpServer`
returning canned responses tests the client, the repair loop and the validators;
it says nothing about whether a real model produces good buildings. That is
what M2's twenty-prompt measurement is for, and it should be repeated whenever
the model or the prompt changes.

## CI and release

Both plugins should mirror `.github/workflows/release.yml` from this repo: tag
`<plugin>-v<semver>`, build with `-PreleaseVersion=<version>` using the
project's own wrapper, assert exactly one jar, attach it to a GitHub Release,
print the sha256.

Two improvements worth making in the new repos, since this one does not have
them: a **PR/push workflow that runs the tests** (this repo only runs them
during a release build), and publishing the `-api` artifact separately with its
own tag.

FoliaNexa catalogue submission — a PR adding a `catalog.yaml` entry with the
release `download_url` and `sha256` — comes after M2 for WorldEditCraft and
after M6 for the RPG. The RPG entry must record its hard dependency on
Phqen1xWorldEditCraft.

## What's real vs. unverified

This is a plan, not a record. No milestone has been started.

The exit criteria are written to be demonstrable rather than subjective, and
several of them are deliberately measurements rather than pass/fail gates — M2's
prompt success rate and M4's generation time in particular. Those numbers do not
exist yet, and the estimates elsewhere in these documents that depend on them
(paste throughput, campaign generation taking "tens of minutes") are guesses
that the milestones are designed to replace with facts.

## License

MIT
