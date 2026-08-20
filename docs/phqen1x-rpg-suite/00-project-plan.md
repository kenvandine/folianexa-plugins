# 00 — Project plan

The vision, the end-goal gameplay, how the two plugins interlock, and what
could go wrong. Read this before the technical documents.

## The problem

FoliaNexa's worlds are built by hand or by code. `campus-lobby` is the current
high-water mark: a genuinely nice scene, but every brick of it is a Java method
somebody wrote, and the only way to get a second scene is to write a second
plugin. Meanwhile a Lemonade Server sits on the LAN with a capable model
loaded, doing nothing for the game.

There are two separable opportunities here, and the plan deliberately keeps
them separable:

1. **Building is a translation problem.** "A ruined dwarven forge hall, mossy
   blackstone, lava channels down the middle" is a complete specification. The
   gap between that sentence and a structure in the world is mechanical, and a
   language model is good at exactly that translation — provided you never ask
   it to emit two million block coordinates.
2. **A role-playing game is mostly authored text hung off a small number of
   mechanics.** Quests, factions, NPCs, item names, boss flavour — that is
   writing, and it is the part that makes each server's world feel like
   *somewhere*. The mechanics underneath (kill ten of these, bring me that,
   the boss enrages at 30%) are a short, finite list.

## The two plugins

### Phqen1xWorldEditCraft

An operator with `worldeditcraft.generate` types:

```
/wec generate a ruined dwarven forge hall, 40x25x20, mossy stone brick and
     blackstone, lava channels down the centre, collapsed roof at the far end
```

The plugin asks Lemonade for a **build script** — compact JSON describing the
structure as geometric operations, not blocks — validates and clamps it,
rasterizes it deterministically into a voxel grid, and writes a genuine Sponge
v3 `.schem` file. That file lands in a permanent library, and can be pasted,
rotated, re-pasted, undone, refined (`/wec regen <name> "make it deepslate and
twice as tall"`), exported, and shared. Full design in
[`01-worldeditcraft-design.md`](01-worldeditcraft-design.md).

It is useful entirely on its own. An operator who never installs the RPG plugin
still gets a natural-language structure generator with a reusable asset library.

### Phqen1xRPG

Generates a whole campaign — world bible, regions, sites, NPC roster, quest
graph, classes, bestiary, loot, boss encounters — through nine small, separately
validated calls to the same Lemonade server. For each *site* the campaign
invents, it hands a natural-language build brief to WorldEditCraft and gets back
a real structure with named anchor points inside it, so a boss lair's boss
actually stands where the building intended. Full design in
[`02-rpg-design.md`](02-rpg-design.md).

Every generated campaign is written out as a **campaign pack** — a portable
bundle containing the authored content *and* the schematics it references — so
the same game can be played again, moved to another server, or run on hardware
with no LLM anywhere near it. See
[`06-campaign-pack-format.md`](06-campaign-pack-format.md).

## How they interlock

```
    operator                                    player
        │                                          │
   /wec generate                              /rpg start
        │                                          │
        ▼                                          ▼
┌───────────────────────────┐            ┌────────────────────────┐
│  Phqen1xWorldEditCraft    │            │      Phqen1xRPG        │
│                           │            │                        │
│  build-script DSL         │            │  campaign generator    │
│  voxel rasterizer         │            │  quest engine          │
│  .schem library           │◀───────────│  dialogue engine       │
│  Folia paste engine       │  Services  │  NPC / mob director    │
│                           │  Manager   │  campaign packs        │
│  InferenceService ────────┼────┐       │                        │
└───────────────────────────┘    │       └────────────────────────┘
                                 │                    │
                                 └────────┬───────────┘
                                          ▼
                             ┌────────────────────────┐
                             │  Lemonade Server       │
                             │  <host>:13305          │
                             │  /api/v1/...           │
                             └────────────────────────┘
```

Three things flow across that boundary, and only three:

1. **`generate(brief)`** — the RPG asks for a structure in words and gets back a
   `SchematicHandle`. It never sees geometry.
2. **`paste(handle, request)`** — the RPG asks for it to be placed and gets back
   a `PasteResult` containing the **world coordinates of every named marker**
   inside the structure (`spawn`, `boss`, `loot`, `npc`, `entrance`). That is
   the entire integration surface for placement. The RPG does not read blocks.
3. **`InferenceService`** — WorldEditCraft owns the one connection to Lemonade,
   including the JSON repair loop and, critically, a **single shared request
   queue**. One inference box serves both plugins; if they each opened their own
   pipeline they would thrash it. Details in
   [`05-shared-api.md`](05-shared-api.md).

Coupling is one-directional. WorldEditCraft has no idea the RPG exists.

## The end-goal gameplay

This is what all of it is for.

A player joins a fresh FoliaNexa world. Before they arrived, an operator ran
`/rpg campaign generate a drowned coastal kingdom, folk-horror, three acts` and
walked away for twenty minutes. In that time the server asked Lemonade for a
world nobody has played: a coast whose lighthouse-keepers worship something in
the water, three factions who disagree about what to do about it, a bestiary of
things that come ashore at night, and a quest chain that ends underneath the
lighthouse. WorldEditCraft built the villages, the flooded chapel, the cliff
fort and the lair, each from a `.schem` that now exists as a real, reusable,
WorldEdit-loadable asset.

The player picks one of the campaign's own classes — not a generic warrior, but
whatever this world's three archetypes are. They talk to an NPC whose dialogue
was written about *this* coast. They take a quest whose objective type is one of
eight the plugin has always supported, but whose text, giver, target and reward
belong to this campaign alone. They fight named things, find named loot, and end
up in a boss encounter whose phases are assembled from a fixed vocabulary and
whose flavour is unique.

At any point an operator can type `/wec generate a flooded lighthouse on a black
rock` and drop it into the world — and that lighthouse is now in the library,
available to the next campaign as a site.

When they are done, the campaign is a file. Hand it to somebody else's server
and they play the identical game, with the Lemonade box switched off.

## Repository and namespace

The plugins will be implemented by [Phqen1x](https://github.com/Phqen1x), most
likely in independent repositories rather than in `folianexa-plugins`. The
namespace is therefore his:

| | |
| --- | --- |
| Phqen1xWorldEditCraft | `io.github.phqen1x.worldeditcraft` |
| Phqen1xRPG | `io.github.phqen1x.rpg` |
| Shared API artifact | `io.github.phqen1x.worldeditcraft.api` |

This diverges from the `io.github.kenvandine.*` group used by the three plugins
currently in this repo, and that is intentional — these are a different author's
projects. Everything *else* about the build follows this repo's conventions
exactly, so that the plugins remain catalogue-compatible with FoliaNexa and so
that anyone who has worked on `hungergames` recognises the shape immediately:
standalone Gradle KTS project, Java 21, `compileOnly` paper-api
`1.21.4-R0.1-SNAPSHOT`, `com.gradleup.shadow` 8.3.5 with no relocation, legacy
`plugin.yml` with `folia-supported: true`, thin main class, and JUnit 5 with no
mocking library. [`07-folia-safety.md`](07-folia-safety.md) covers the
threading conventions specifically.

## Milestones

Detail and exit criteria in [`08-roadmap.md`](08-roadmap.md); the shape:

| Milestone | Delivers |
| --- | --- |
| **M1 — Schematic core** | NBT writer/reader, `.schem` round-trip, build-script DSL and interpreter, all offline and unit-tested. No Minecraft, no LLM. |
| **M2 — WorldEditCraft plugin** | Lemonade client, repair loop, library, Folia paste engine, command surface. First real `/wec generate`. |
| **M3 — Shared API** | The `-api` artifact, `ServicesManager` registration, shared inference queue. |
| **M4 — Campaign generator** | The nine-stage pipeline, validation, campaign packs written on generation. Generates content; does not yet run a game. |
| **M5 — RPG runtime** | Quest engine, dialogue, NPCs, mobs, classes, loot, boss controller. The game is playable. |
| **M6 — Replay and share** | Pack export/import, offline mode, baking, multiple runs per pack. |

M1 is deliberately first and deliberately has no dependencies on Minecraft or
on Lemonade. It is the part most likely to contain subtle bugs (varint packing,
YZX ordering, block-state round-tripping) and the part easiest to test in
isolation. Getting a `.schem` that WorldEdit will genuinely open, before any of
the interesting work starts, de-risks everything after it.

## Risks

**Local-model JSON reliability is the number one risk.** A 7B–30B model asked
for structured output without `response_format` support will sometimes produce
prose, fenced code, trailing commas, or a perfectly-formed script describing a
4000-block cube. The mitigations are layered and all of them are load-bearing:
keep each call small and single-purpose; specify hard caps in the prompt *and*
enforce them in code; extract-parse-validate rather than trust; feed specific
validation errors back in a repair prompt; cap retries; and degrade to reusing
an existing library asset rather than failing a whole campaign. See
[`04-lemonade-integration.md`](04-lemonade-integration.md).

**Inference latency makes campaign generation a background job, not a startup
step.** Nine staged calls plus one generation per site, on one box, is minutes
— possibly tens of minutes. Nothing in the design may block a game thread, a
join, or a world load on inference. Generation is an explicitly asynchronous,
resumable, progress-reporting job, and the server is fully playable while it
runs.

**Hand-rolling NBT is real work and a real correctness risk.** It is the right
call — this repo does not shade dependencies, and the alternative is a runtime
dependency on a WorldEdit fork with unofficial Folia support — but varint
bitpacking and YZX ordering are exactly the kind of thing that is silently
wrong. Round-trip tests plus one manual check against real WorldEdit.

**Pasting large structures on Folia is unmeasured.** A 128³ schematic is
~2 million blocks. The chunk-bucketed, per-tick-budgeted engine is the
mitigation, but the numbers in the config file are guesses until somebody
profiles them on real hardware.

**The RPG's fixed vocabulary might be too small to feel varied.** If every
campaign's quests bottom out in the same eight objective types, the third
campaign may feel like the first with new nouns. Mitigation is composition:
objectives chain into graphs, boss phases combine, classes mix primitives. This
is a design bet, and [`08-roadmap.md`](08-roadmap.md) makes "does campaign #3
still feel new?" an explicit exit criterion rather than something to discover
later.

**Two plugins, two release cadences.** Version skew between the `-api` artifact
and either plugin. Mitigation: the API artifact is versioned independently and
conservatively, and the RPG logs loudly and refuses to start rather than failing
strangely at runtime.

## What's real vs. unverified

None of this is built. The repository conventions cited throughout were read
from the real source in this repo. The Lemonade and Sponge specifications cited
are real and linked. Everything about performance, model reliability, and
WorldEdit interoperability is a prediction. See the root
[`README.md`](README.md#whats-real-vs-unverified) for the full statement.

## License

MIT
