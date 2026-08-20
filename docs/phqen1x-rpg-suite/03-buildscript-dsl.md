# 03 — The build-script DSL

**Normative.** This is the contract between the language model and
Phqen1xWorldEditCraft. The system prompt is generated from it, the parser
implements it, the validator enforces it, and the interpreter executes it. If
this document and the code disagree, that is a bug in one of them.

## Why a DSL

A 40×25×20 hall is 20,000 blocks. Asking a model to emit 20,000 coordinates
fails three ways at once: it exhausts the context window, it costs minutes of
inference, and — worst — a model generating its ten-thousandth coordinate has
long since lost track of what it was building. You get noise shaped like a
building.

The same hall is roughly 25 operations: a hollowed box, a floor, two rows of
columns, an arched entrance, a gabled roof, a lava channel, some scattered
rubble. That is well inside what a 7B model can hold in its head at once, and it
is *checkable* — a validator can tell you the roof op references a palette key
that does not exist, and you can hand that specific complaint back to the model.

The DSL is therefore deliberately at the altitude a person would describe a
building at. It is not a general-purpose language. It has no variables, no
conditionals and no arithmetic, because every one of those would be a thing the
model could get subtly wrong in a way no validator could catch.

## Document shape

A build script is a single JSON object.

```json
{
  "name": "ruined_forge_hall",
  "size": [40, 25, 20],
  "seed": 8471132,
  "palette": {
    "wall":   "minecraft:polished_blackstone_bricks",
    "trim":   "minecraft:deepslate_tiles",
    "floor":  "minecraft:stone_bricks",
    "mossy":  "minecraft:mossy_stone_bricks",
    "pillar": "minecraft:polished_blackstone",
    "lava":   "minecraft:lava",
    "light":  "minecraft:shroomlight"
  },
  "ops": [
    { "op": "box",         "from": [0, 0, 0],   "to": [39, 0, 19],  "block": "floor" },
    { "op": "hollow_box",  "from": [0, 0, 0],   "to": [39, 14, 19], "block": "wall", "thickness": 1 },
    { "op": "noise_replace", "region": [[0,0,0],[39,14,19]], "find": "wall", "block": "mossy", "chance": 0.22 },
    { "op": "column",      "at": [6, 1, 4],  "height": 12, "block": "pillar", "repeat": { "count": 4, "step": [9, 0, 0] } },
    { "op": "column",      "at": [6, 1, 15], "height": 12, "block": "pillar", "repeat": { "count": 4, "step": [9, 0, 0] } },
    { "op": "box",         "from": [18, 0, 2], "to": [21, 0, 17], "block": "lava" },
    { "op": "arch",        "at": [20, 1, 0], "width": 7, "height": 6, "axis": "x", "block": "trim" },
    { "op": "roof_gable",  "from": [0, 15, 0], "to": [39, 15, 19], "block": "trim", "pitch": 1 },
    { "op": "carve",       "region": [[30,10,0],[39,22,19]], "chance": 0.55, "seed_offset": 7 },
    { "op": "scatter",     "region": [[28,1,1],[38,1,18]], "block": "mossy", "density": 0.15 },
    { "op": "place_block", "at": [20, 6, 10], "block": "light" },
    { "op": "marker",      "at": [20, 1, 17], "id": "entrance" },
    { "op": "marker",      "at": [20, 1, 4],  "id": "boss" }
  ]
}
```

### Top-level fields

| Field | Type | Required | Rules |
| --- | --- | --- | --- |
| `name` | string | yes | Slugified on ingest: lowercase, `[a-z0-9_]`, ≤ 64 chars. Collisions get a numeric suffix. |
| `size` | `[W, H, L]` int | yes | Each ≤ `generation.max-dimension`; `W*H*L` ≤ `generation.max-volume`. |
| `seed` | int | no | Defaults to a hash of `name`. Every random draw derives from it. |
| `palette` | object | yes | Key → block-state string. Keys are `[a-z0-9_]`, ≤ 32 chars. |
| `ops` | array | yes | Length ≤ `generation.max-ops`. Applied strictly in order. |
| `description` | string | no | Free prose. Kept in the sidecar, ignored by the interpreter. |

### Coordinates

Local to the schematic. Origin is `[0, 0, 0]` at the minimum corner. `x` is
width, `y` is height (up), `z` is length. Every coordinate must fall inside
`size`; the validator clamps rather than rejecting, and reports the clamp.

### The palette indirection

Ops name **palette keys**, not block IDs. This is not decoration — it does three
jobs:

1. It cuts tokens. `"block": "wall"` beats
   `"block": "minecraft:polished_blackstone_bricks"` forty times over.
2. It keeps materials consistent. A model that has committed to `wall` meaning
   one thing will not drift to a near-miss block halfway down the op list.
3. It makes refinement trivial. `/wec regen forge_hall "make it all deepslate"`
   is a seven-line palette edit, not a re-imagining of the geometry — which is
   both far cheaper and far more likely to preserve what you liked about it.

An op may use a literal block-state string instead of a key, and the validator
accepts it, but the prompt asks the model not to.

### Block-state strings

Standard Minecraft notation, the same thing `Bukkit.createBlockData(String)`
takes:

```
minecraft:stone
minecraft:oak_stairs[facing=north,half=top]
minecraft:oak_log[axis=y]
```

The namespace may be omitted and defaults to `minecraft:`. Properties are sorted
alphabetically on ingest, so one block state always yields one canonical string
— which is what makes palettes stable and schematics hash reproducibly.

Under `generation.block-policy: vanilla` (the default) an ID outside the vanilla
registry is a validation error, not a silent air block. This matters: a
hallucinated `minecraft:dwarven_forge_block` should send the model back for a
repair round, not quietly punch a hole in the building.

## Operations

Each op is an object with an `op` field. Unknown ops are collected as validation
issues — never thrown, never silently dropped — so the repair prompt can tell
the model exactly which name it invented.

### Shared modifiers

Any op may carry these:

| Field | Effect |
| --- | --- |
| `repeat` | `{ "count": n, "step": [dx,dy,dz] }` — apply the op `n` times, offsetting by `step` each time. `count` ≤ 64. |
| `replace_only` | Palette key or block string. The op only writes where this block already is. |
| `skip_air` | Boolean. Only write where the target is currently air. |
| `chance` | 0.0–1.0. Each block writes with this probability, drawn from the script seed. |

`repeat` is the reason the example above places eight columns in two ops. It is
the single highest-leverage feature in the language and the prompt pushes it
hard.

### Solids

| Op | Fields | Effect |
| --- | --- | --- |
| `fill` | `block` | Fill the entire schematic. Usually the first op. |
| `box` | `from`, `to`, `block` | Solid axis-aligned box, inclusive. |
| `hollow_box` | `from`, `to`, `block`, `thickness` (default 1) | Shell only. |
| `sphere` | `at`, `radius`, `block`, `hollow` | Filled or shelled sphere. |
| `ellipsoid` | `at`, `radii` `[rx,ry,rz]`, `block`, `hollow` | As above, per-axis radii. |
| `cylinder` | `at`, `radius`, `height`, `axis` (`x`/`y`/`z`), `block`, `hollow` | |
| `cone` | `at`, `radius`, `height`, `axis`, `block` | Apex at `height`. |
| `pyramid` | `at`, `base`, `height`, `block`, `hollow` | Square base. |
| `prism` | `from`, `to`, `block`, `sides` | Regular n-sided prism inscribed in the box. |
| `line` | `from`, `to`, `block`, `thickness` | 3-D Bresenham. |

### Architecture

These exist because they are how people actually describe buildings, and
because expressing "a gabled roof" as nested `box` ops is exactly the kind of
arithmetic a model gets wrong.

| Op | Fields | Effect |
| --- | --- | --- |
| `wall` | `from`, `to`, `block`, `height` | Vertical wall along the `from`→`to` line. |
| `floor` | `region`, `block` | Fill the region's lowest layer. |
| `ceiling` | `region`, `block` | Fill the region's highest layer. |
| `column` | `at`, `height`, `block`, `radius` (default 0) | Vertical column. |
| `arch` | `at`, `width`, `height`, `axis`, `block` | Semicircular arch; `at` is the base centre. |
| `stairs` | `from`, `to`, `block`, `width` | A staircase. Emits proper stair block states, oriented along the run. |
| `ramp` | `from`, `to`, `block`, `width` | Solid sloped mass. |
| `roof_gable` | `from`, `to`, `block`, `pitch`, `axis` | Two-sided pitched roof over the footprint. Emits stair block states unless `block` is given a non-stair material. |
| `roof_hip` | `from`, `to`, `block`, `pitch` | Four-sided pitched roof. |
| `window_grid` | `region`, `block`, `spacing`, `size` | Punch a regular grid of openings through a wall. |
| `door` | `at`, `facing`, `block`, `width`, `height` | Opening plus, optionally, a real door block entity pair. |
| `torch_row` | `from`, `to`, `block`, `spacing` | Wall-attached light sources. Deferred to paste pass 2. |

### Texture and decay

The ops that stop a generated structure looking like it was extruded by a
machine. All of them draw from the script seed, so all of them are reproducible.

| Op | Fields | Effect |
| --- | --- | --- |
| `replace` | `region`, `find`, `block` | Straight substitution. |
| `noise_replace` | `region`, `find`, `block`, `chance`, `seed_offset` | Substitute a fraction of matching blocks. This is how you get mossy, cracked and weathered variants scattered convincingly. |
| `scatter` | `region`, `block`, `density`, `seed_offset` | Sparse random placement — rubble, foliage, debris. |
| `carve` | `region`, `chance`, `seed_offset` | Remove blocks. Ruins, collapse, erosion. |
| `gradient` | `region`, `blocks` (array), `axis` | Blend between materials along an axis. |

### Composition

| Op | Fields | Effect |
| --- | --- | --- |
| `mirror` | `region`, `axis`, `at` | Reflect a region across a plane. Rewrites direction-valued block states. |
| `array` | `region`, `count`, `step` | Copy a region repeatedly. |
| `rotate_group` | `region`, `degrees`, `at` | Rotate a region about a vertical axis. 90/180/270 only. |

### Detail and integration

| Op | Fields | Effect |
| --- | --- | --- |
| `place_block` | `at`, `block` | One block. For deliberate detail, not for bulk — the validator warns above 200 of these in a script. |
| `block_entity` | `at`, `id`, `data` | A chest, sign, spawner, banner or similar, with its NBT payload. |
| `marker` | `at`, `id`, `meta` | **Not a block.** A named anchor point. |

### Markers — the entire RPG integration surface

`marker` writes nothing. It records a named position in the schematic's
metadata, and `PasteResult` returns those positions translated into world
coordinates after transform and origin are applied.

This is how Phqen1xRPG places a boss inside a lair it did not design. It asks
for a building, gets back a handle, pastes it, and is told "`boss` is at
(1043, 62, -288)". It never parses geometry, never scans for a suitable room,
and never has to understand anything about what was built.

Reserved marker IDs, which the prompt asks the model to place where appropriate:

| ID | Means |
| --- | --- |
| `entrance` | Where a player walks in. Used for teleports and quest waypoints. |
| `spawn` | Safe standing position inside. |
| `boss` | Boss arena centre. |
| `loot` | Chest or reward position. Repeatable — `loot_1`, `loot_2`, … |
| `npc` | NPC standing position. Repeatable. |
| `mob` | Hostile spawn point. Repeatable. |
| `focus` | The thing the building is *about* — the altar, the forge, the throne. |

Any other ID is passed through and available to callers. `meta` is a free-form
object the RPG can use to carry its own hints.

## Validation

`BuildScriptValidator` returns a list of `ValidationIssue`. It never throws and
never stops at the first problem — a repair round that fixes one error at a time
would need ten rounds.

Each issue carries two messages: one for the operator's chat, and one written
for the model, phrased as an instruction. That second field is what makes the
repair loop work.

| Class | Severity | Handling |
| --- | --- | --- |
| Malformed JSON | fatal | Repair round with the parser error. |
| `size` over `max-dimension` / `max-volume` | fatal | Repair round quoting the actual limit. |
| `ops` over `max-ops` | fatal | Repair round. |
| Unknown op name | error | Op dropped, repair round listing the valid names. |
| Missing required field | error | Op dropped, repair round. |
| Unknown palette key | error | Op dropped, repair round listing the keys that do exist. |
| Unresolvable block ID | error | Repair round. Under `block-policy: any`, downgraded to a warning and substituted. |
| Coordinate out of bounds | warning | **Clamped**, reported. Does not by itself trigger a repair. |
| `repeat.count` over 64 | warning | Clamped. |
| Over 200 `place_block` ops | warning | Reported; suggests bulk ops in the repair message. |
| Empty result (nothing placed) | fatal | Repair round — a script that produces no blocks is the classic silent failure. |

Warnings alone never trigger a repair round. Any error or fatal does, up to
`lemonade.max-attempts`. On final failure the operator gets the accumulated
issues, and the raw model output is kept under `failed/` when
`generation.keep-failed-responses` is on.

## Determinism

The interpreter is a pure function of `(BuildScript) → (VoxelGrid, markers)`.

Every stochastic op (`scatter`, `carve`, `noise_replace`, `chance`) draws from a
`java.util.Random` seeded with `script.seed ^ opIndex ^ op.seed_offset`. Same
script, same result — same JVM, different JVM, different machine, next year.

This is load-bearing in three places. It makes the interpreter unit-testable
with exact expected output. It makes `/wec regen` behave predictably, because
an unchanged op produces unchanged voxels. And it is what lets a campaign pack
carry build scripts instead of megabytes of `.schem` and still rebuild a world
identically — see [`06-campaign-pack-format.md`](06-campaign-pack-format.md).

## Prompting

The system prompt is **generated from `OpRegistry`**, not hand-written. Adding
an op to the registry adds it to the prompt, the parser, the validator and this
document's generated table in one edit. A hand-maintained prompt would drift
from the implementation within a month, and the failure mode — a model
confidently emitting an op that no longer exists — would look like a model
problem rather than a documentation problem.

The prompt states, in this order: the JSON shape; the op list with fields; the
active hard caps as literal numbers; the instruction to prefer bulk ops and
`repeat` over `place_block`; the instruction to place markers; and the
instruction to emit nothing but the JSON object. Full text and the repair-prompt
construction in [`04-lemonade-integration.md`](04-lemonade-integration.md).

## Worked example

**Prompt:** *"a small wayside shrine, 9x7x9, weathered stone and birch, open
sides, a brazier in the middle"*

```json
{
  "name": "wayside_shrine",
  "size": [9, 7, 9],
  "seed": 4412,
  "palette": {
    "base": "minecraft:stone_bricks",
    "worn": "minecraft:cracked_stone_bricks",
    "post": "minecraft:stripped_birch_log[axis=y]",
    "roof": "minecraft:birch_stairs",
    "fire": "minecraft:campfire[lit=true]"
  },
  "ops": [
    { "op": "box",           "from": [0,0,0], "to": [8,0,8], "block": "base" },
    { "op": "noise_replace", "region": [[0,0,0],[8,0,8]], "find": "base", "block": "worn", "chance": 0.3 },
    { "op": "column", "at": [1,1,1], "height": 4, "block": "post",
      "repeat": { "count": 2, "step": [6,0,0] } },
    { "op": "column", "at": [1,1,7], "height": 4, "block": "post",
      "repeat": { "count": 2, "step": [6,0,0] } },
    { "op": "roof_hip", "from": [0,5,0], "to": [8,5,8], "block": "roof", "pitch": 1 },
    { "op": "place_block", "at": [4,1,4], "block": "fire" },
    { "op": "marker", "at": [4,1,6], "id": "entrance" },
    { "op": "marker", "at": [4,1,4], "id": "focus" },
    { "op": "marker", "at": [2,1,2], "id": "npc" }
  ]
}
```

Nine operations, about 350 tokens, and it produces a 567-block structure with
weathering, a hipped roof with correctly-oriented stairs, and three anchor
points the RPG plugin can build a scene around.

## What's real vs. unverified

The language is designed, not implemented. The op list is a considered guess at
what covers most Minecraft architecture without becoming a programming language;
expect it to gain three or four ops and lose one or two after the first week of
real use.

Entirely unverified: whether a locally-served model actually emits valid scripts
at a useful rate, and whether the geometry it chooses looks like the thing that
was asked for. Those are the two questions M1 and M2 exist to answer, and they
are best answered with a hundred prompts and a hard look at the results rather
than with more design.

## License

MIT
