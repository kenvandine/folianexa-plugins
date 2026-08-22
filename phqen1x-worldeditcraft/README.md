# Phqen1xWorldEditCraft

Natural-language structure generation for Folia. An operator describes a
building, a [Lemonade Server](https://lemonade-server.ai/) on the LAN
turns it into a compact JSON "build script", the plugin rasterizes that
deterministically into a voxel grid and writes a real Sponge v3
`.schem` file into a permanent library — no runtime dependency on
WorldEdit or FAWE (neither officially supports Folia; see the full
design doc's "Why not just depend on WorldEdit?").

Full design: `docs/phqen1x-rpg-suite/01-worldeditcraft-design.md` in the
[FoliaNexa](https://github.com/kenvandine/FoliaNexa) repo (and the rest
of that directory for the sibling Phqen1xRPG plugin this one is designed
to support, not yet started).

## What's implemented in this build

This is a first "get started" pass, not the finished plugin. Implemented
and unit-tested:

- **The schematic core (`.schem`)** — hand-rolled NBT (`NbtTag`/
  `NbtWriter`/`NbtReader`/`VarIntCodec`), a YZX-ordered `VoxelGrid` +
  `VoxelPalette`, `BlockStateRef` parsing/canonicalization, `Transform`
  (90/180/270 rotation + X/Z flip, rewriting `facing`/`axis` block-state
  properties), and `SchematicWriter`/`SchematicReader` (writes Sponge v3,
  reads v2 and v3).
- **The build-script DSL** — `BuildScriptParser`, `BuildScriptValidator`
  (caps, op/palette/block validation, coordinate clamping), and
  `BuildScriptInterpreter` (pure, deterministic rasterization), all
  driven by a single `OpRegistry`. **Sixteen ops** are implemented:
  `fill`, `box`, `hollow_box`, `sphere`, `cylinder`, `line`, `floor`,
  `ceiling`, `column`, `replace`, `noise_replace`, `scatter`, `carve`,
  `place_block`, `marker`, `block_entity` — one from every category in
  the normative spec, plus the shared modifiers (`repeat`,
  `replace_only`, `skip_air`, `chance`). **Not yet implemented**: arches,
  roofs, stairs, ramps, window grids, doors, torch rows, gradients,
  mirror/array/rotate_group, ellipsoid/cone/pyramid/prism — the spec
  itself expects this vocabulary to evolve after real use, and these
  sixteen are enough to exercise the whole pipeline first.
- **The Lemonade client** — `LemonadeClient` (HTTP/1.1 pinned, per the
  live-confirmed h2c bug documented in `folianexa-stats`'s
  `HttpMgmtClient`), `MiniJson` (ported from `folianexa-stats`, extended
  with a forgiving read mode: trailing commas, single-quoted strings,
  unquoted keys, `//` comments), `JsonCoercion` (pulls a JSON object out
  of fenced/prose-wrapped model output), and `PromptBuilder` (the system
  prompt is generated from `OpRegistry`, not hand-written).
- **`GenerationService`** — the full `/wec generate` pipeline: prompt →
  Lemonade → extract → parse → validate → repair-round-on-failure
  (up to `lemonade.max-attempts`) → rasterize → write `.schem` → save to
  the library. Exercised end to end in `GenerationServiceTest` against a
  real fake Lemonade server (a `com.sun.net.httpserver.HttpServer`) that
  returns garbage, then an invalid script, then a valid one — the
  repair loop's `RepairLoopTest` equivalent from the design doc's test
  plan.
- **The schematic library** — `SchematicLibrary`/`SchematicIndex`/
  `SchematicRecord`/`SchematicQuery`: real file I/O against a real temp
  directory in tests, slugging, collision suffixes, checksum dedupe,
  `index.json` persistence.
- **Config** — `config.yml` matches the design doc's shape exactly,
  including `lemonade.base-url` and `lemonade.model` (the two things
  every deployment must set). `ConfigLoader`/`WorldEditCraftConfig`
  mirror the pattern in this repo's `hungergames/ConfigLoader`.
- **The `/wec` command surface**: `generate`, `list`, `info`, `delete`,
  `rename`, `status`, `reload` are real. `regen`, `preview`, `paste`,
  `undo`, `cancel`, `tag`, `import`, `export` respond with a clear
  "not implemented yet" message rather than silently doing nothing.

## What's not implemented yet

**The paste engine.** `/wec generate` produces and saves a `.schem` file
but never places it — there is no `PastePlan`/`PasteEngine`/`PasteJob`/
`UndoJournal` in this build. This is the single biggest remaining piece:
the design doc's chunk-bucketed, per-tick-budgeted, two-pass region
scheduler (see `01-worldeditcraft-design.md`'s "The paste engine" and
`07-folia-safety.md`), modeled on `campus-lobby`'s `SceneBuilder`. Until
it exists, `/wec paste`/`preview`/`undo`/`cancel` and the `--paste` flag
on `generate` all report "not implemented".

Also not implemented: `/wec regen` (needs the sidecar's stored build
script, which `GenerationService` doesn't yet write — only the `.schem`
and the index record), `/wec import`/`export`, `/wec tag`, the shared
`InferenceQueue`/`-api` artifact for a future Phqen1xRPG (M3 in the
design doc's roadmap), the WorldEdit delegate-paste adapter, and the
`response_format` structured-output probe.

## Known gap: `block-policy: vanilla` isn't a real registry check

`BuildScriptValidator`'s vanilla-block check is syntax (does it parse as
`namespace:id[properties]`) plus namespace (`minecraft:`) only — **not**
membership in the real vanilla block registry, which would need a full
ID list this implementation doesn't carry yet. A model-hallucinated but
syntactically-plausible ID like `minecraft:dwarven_forge_block` will
currently pass validation and only fail at paste time (once there is a
paste engine) via `paste.unknown-block`. Documented here rather than
silently wrong; the real fix is bundling (or fetching) a real block-ID
list.

## Running the tests

```bash
./gradlew test
```

JUnit 5, no mocking library, no MockBukkit — this repo's stated
convention. Every DSL/schem/voxel/llm/library class has no
`org.bukkit` import and is reachable without a server; HTTP is tested
against a real `com.sun.net.httpserver.HttpServer`, matching
`folianexa-stats/src/test/java/.../HttpMgmtClientTest.java`.

## Configuration (`config.yml`)

The two things every deployment must set:

```yaml
lemonade:
  base-url: "http://lemonade.local:13305"   # your LAN inference host
  model: ""                                  # blank = first model from /api/v1/models
```

See `config.yml` for the full set of tunables (timeouts, sampling,
generation caps, paste tuning, library location) — every key mirrors the
design doc's config table exactly.

## What's real vs. unverified

Not built, not run against a real Folia server, not run against a real
Lemonade server. What's grounded:

- Every claim about this repo's conventions (Java 21, `paper-api
  1.21.4-R0.1-SNAPSHOT`, `com.gradleup.shadow` 8.3.5, the thin-main-class/
  `ConfigLoader` pattern, HTTP/1.1 pinning, no third-party dependencies)
  was read from `hungergames/`, `campus-lobby/`, and `folianexa-stats/`'s
  real source in this repo.
- The Sponge Schematic v2/v3 field shapes (`Blocks` nesting, `Data` vs.
  `BlockData` key, `BlockEntities` entry shape) were fetched from the
  published [Sponge Schematic Specification](https://github.com/SpongePowered/Schematic-Specification)
  for both versions, not guessed.
- Every pure class (`schem`, `voxel`, `dsl`, `llm/MiniJson`+`JsonCoercion`,
  `library`) is unit-tested, including the transposition-catching
  asymmetric-fixture round-trip test and a real repair-loop test against
  a real fake HTTP server.

What's unverified, matching every other plugin in this repo at this
stage: whether a `.schem` this writer produces actually opens in real
WorldEdit (the M1 exit criterion in the design doc's roadmap — "the
highest-value early check" per the design doc, and it needs nothing but
a Paper server with WorldEdit and one generated file); whether a real
Lemonade server + real model produces valid build scripts at a useful
rate; and everything about the (not yet written) paste engine's Folia
behavior under load. This plugin has not been installed or started
against a live server in this development environment.

## License

MIT
