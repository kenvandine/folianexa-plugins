# 06 — Campaign packs

How a generated RPG is saved, loaded, replayed, and shared — and why Lemonade is
an authoring-time dependency rather than a runtime one.

## The requirement

A campaign takes minutes of inference to author and is, by construction, unique.
Losing it because a server restarted, or being unable to run it again because
the inference box is off, would be absurd. So:

- **Every generated campaign is saved automatically**, as a side effect of
  generation. There is no separate save step for an operator to forget.
- **A saved campaign replays with no LLM in the loop at all.** The Lemonade box
  can be switched off, moved, or never have existed on this server.
- **A saved campaign is portable.** It is a file. Hand it to another server and
  they play the same game.

## What a pack contains

```
plugins/Phqen1xRPG/campaigns/<campaign-id>/
  manifest.json      identity, provenance, compatibility, integrity
  campaign.json      the authored content — all nine stages' output
  layout.json        where things go in the world
  dialogue.json      every line, including baked improv
  schematics/
    <slug>.schem     a copy of every structure the campaign references
    <slug>.json      its sidecar, including the build script
```

Exported as a single zip named `<campaign-id>.rpgpack`. `import` accepts either
the zip or a directory.

### `manifest.json`

```json
{
  "packFormat": 1,
  "campaignId": "drowned-coast-8f31",
  "name": "The Drowned Coast",
  "createdAt": "2026-08-20T14:02:11Z",
  "authoredBy": "kenvandine",
  "masterSeed": 8412977341,
  "generator": {
    "plugin": "Phqen1xRPG 0.1.0",
    "lemonadeModel": "Qwen3-Coder-30B-A3B-Instruct-GGUF",
    "lemonadeBuild": "8.1.4"
  },
  "requires": {
    "minecraftDataVersion": 4189,
    "worldEditCraftApi": "1.0"
  },
  "contents": {
    "regions": 4, "sites": 14, "npcs": 23,
    "quests": 19, "classes": 3, "bestiary": 11, "bosses": 3
  },
  "checksum": "sha256:…"
}
```

`packFormat` drives migration. `requires` drives the compatibility check on
load. `checksum` covers every other file in the pack and detects a hand-edit or
a truncated transfer. `masterSeed` is what makes replay reproducible.

### `campaign.json`

The whole authored content tree, exactly as validated at generation time:
world bible, regions, sites (with their build briefs), NPC roster, quest graph,
classes, bestiary, items and loot tables, boss encounters. Immutable. Nothing
at play time writes to it.

### `layout.json`

Where everything goes. This is the file that decides whether a pack is portable,
so it stores **relative** placement by default:

```json
{
  "mode": "relative",
  "regions": [
    { "id": "saltmarsh", "anchorOffset": [0, 0, 0], "radius": 400 },
    { "id": "cliffs",    "anchorOffset": [900, 0, -300], "radius": 350 }
  ],
  "sites": [
    { "id": "keeper_hall", "region": "saltmarsh",
      "offset": [120, 0, -60], "rotation": 90, "schematic": "keeper_hall" }
  ]
}
```

A pack in `relative` mode has region anchors placed by `WorldLayout` against the
target world's own terrain, with sites laid out around them. Drop the pack into
a different world, or a different seed, and it rebuilds coherently — the
relationships survive even though the absolute coordinates do not.

`mode: "pinned"` instead records absolute coordinates, for re-creating a
campaign exactly where it was on the world it was authored in. `/rpg campaign
export --pinned` produces one.

### `dialogue.json`

Every dialogue tree, keyed by NPC and node. If a campaign used
`dialogue.live-improv` during authoring, `/rpg campaign bake` walks every
reachable improv path, generates the lines ahead of time, and freezes them in
here — after which the pack is fully offline even though it was authored with
live generation on.

### `schematics/`

The pack takes its **own copies** of every `.schem` it references, rather than
pointing at WorldEditCraft's library. A pack imported onto a fresh server needs
nothing pre-installed.

On import, those schematics are registered back into the local WorldEditCraft
library, deduped by checksum — so importing two packs that both used a generic
`wayside_shrine` stores it once.

The sidecars carry the **build scripts**, which matters more than it looks:
because the interpreter is deterministic (see
[`03-buildscript-dsl.md`](03-buildscript-dsl.md#determinism)), a build script
plus the master seed reproduces the `.schem` byte-for-byte. A pack can therefore
be shipped in `--slim` form with scripts only and no `.schem` files, at roughly
a hundredth of the size, and rebuilt on import. The full form is the default
because it is robust against an interpreter change; slim is there for sharing
over a chat client.

## Content and progress are separate

This is the part that makes "play it again" a real operation.

```
campaigns/<campaign-id>/     immutable authored content — the pack
runs/<run-id>/               mutable state for one playthrough
  run.json                   run id, campaign id, started, world, resolved layout
  players/<uuid>.json        quests, objectives, class, unlocks, inventory grants
  sites.json                 which sites are built, and their resolved markers
  world-state.json           boss defeats, one-shot events, faction standings
```

A run points at a campaign; a campaign knows nothing about runs.

That gives three things for free:

- **Replay.** `/rpg campaign start <id>` on a campaign you have already finished
  creates a new run against the same content. Identical world, identical quests,
  fresh progress.
- **Concurrent runs.** Two groups can play the same campaign independently on
  one server, in different worlds, without touching each other's state.
- **Safe iteration.** Deleting a run never touches the campaign. Re-importing a
  campaign never touches a run in progress.

`sites.json` living in the run rather than the pack is deliberate: the *resolved
world coordinates* of a site's markers belong to a playthrough, not to the
campaign. The same pack run in two worlds resolves to two different sets of
coordinates, and both are correct.

## Determinism

Everything stochastic derives from `masterSeed`:

| Draw | Derived from |
| --- | --- |
| Structure rasterization (`scatter`, `carve`, `noise_replace`) | Each build script's own seed, itself derived from `masterSeed` |
| Site placement offsets | `masterSeed ^ siteId.hashCode()` |
| Loot rolls on generation | `masterSeed ^ tableId.hashCode()` |
| Mob variant selection | `masterSeed ^ siteId ^ spawnIndex` |

So replaying a pack rebuilds identical structures, places them in the same
relative spots, and stocks the same chests. Loot rolled *during play* — a mob
drop — is not seeded, because a replay should still have live combat
randomness.

## Loading

Load-time validation is a real gate, not a formality. A half-loaded campaign
that fails three hours in is much worse than a refusal at load.

1. **Format.** `packFormat` ≤ the plugin's supported version. Older packs run
   through migration hooks; newer ones are refused with a clear message.
2. **Integrity.** Recompute the checksum. A mismatch is a warning listing the
   files that changed — hand-editing a pack is legitimate, silently loading a
   truncated one is not.
3. **Compatibility.** `requires.worldEditCraftApi` against the installed API
   version. `requires.minecraftDataVersion` against the running server: a newer
   pack on an older server is refused; an older pack on a newer server proceeds
   with a note.
4. **References.** Every quest's NPC, item, mob and site exists. Every site's
   schematic file is present (or its build script is, for a slim pack). Every
   objective's target resolves. Dangling references are listed and the load is
   refused.
5. **Block resolution.** Every palette entry in every schematic is checked
   against the running server's registry. Unresolvable IDs are reported once per
   schematic with a count, and handled per `paste.unknown-block`.

A pack that passes is marked loaded and never contacts Lemonade again.

## Offline mode

With `lemonade.required: false` (the default) and a loaded pack, the RPG plugin
opens no socket to the inference server. Not at enable, not at join, not at
dialogue, not at site building — because sites build from stored schematics and
dialogue reads from `dialogue.json`.

The only path that needs Lemonade is `/rpg campaign generate`, which is an
operator authoring action. Stated plainly, since it is the property most worth
knowing: **you need an inference box to write a campaign. You do not need one to
play it.**

If `lemonade.required: true`, the plugin refuses to enable without a reachable
server — appropriate on the machine that does the authoring, wrong everywhere
else.

## Sharing

A `.rpgpack` is a normal zip. There is no server-side registry, no account, no
network component — copy the file.

This turns campaigns into content. One person with a capable GPU authors a
campaign, plays it, exports it, and hands it to a friend running FoliaNexa on a
VPS with no accelerator at all. They get the identical game.

`/rpg campaign clone <id> <new-name>` makes an editable copy with a new id, for
hand-tuning a campaign before redistributing it — fix a typo in an NPC's
dialogue, rebalance a boss, swap a site's schematic for a better one.

## Commands

| Command | What |
| --- | --- |
| `/rpg campaign generate <theme…>` | Author and save. The pack is written incrementally as stages complete. |
| `/rpg campaign list` | Stored packs: name, size, when, contents. |
| `/rpg campaign info <id>` | Manifest, contents, compatibility, runs against it. |
| `/rpg campaign load <id>` | Validate and make active. |
| `/rpg campaign start <id>` | New run. |
| `/rpg campaign runs [id]` · `resume <run-id>` | |
| `/rpg campaign bake <id>` | Freeze live-improv dialogue. Needs Lemonade; makes the pack not need it. |
| `/rpg campaign export <id> [--slim] [--pinned]` | Write `<id>.rpgpack`. |
| `/rpg campaign import <file> [--rebuild]` | Import; `--rebuild` regenerates schematics from build scripts. |
| `/rpg campaign clone <id> <new-name>` | Editable copy. |
| `/rpg campaign delete <id> [--runs]` | Refuses if runs exist unless `--runs`. |
| `/rpg campaign verify <id>` | Run every load check without loading. |

## Test plan

| Test | Asserts |
| --- | --- |
| `PackRoundTripTest` | Campaign → pack → zip → import → campaign is identical. Every field survives. |
| `PackChecksumTest` | Checksum covers every file; a one-byte edit is detected and named. |
| `PackMigrationTest` | A `packFormat: 1` pack loads on a plugin supporting 2. A `packFormat: 3` pack is refused with a clear message. |
| `PackValidationTest` | Dangling NPC / item / site / marker references are each caught. Missing schematic caught. Unresolvable block IDs reported. |
| `SlimPackTest` | Export slim, import with `--rebuild`, and the rebuilt `.schem` is **byte-identical** to the original. This is the test that proves determinism actually holds. |
| `RunIsolationTest` | Two runs of one campaign advance independently. Deleting a run leaves the campaign untouched. Deleting a campaign with live runs is refused. |
| `OfflineModeTest` | With a loaded pack and a Lemonade base URL pointed at a closed port, a full load-start-play-quest-complete cycle succeeds and **no HTTP request is attempted** — asserted by a `HttpServer` on that port that fails the test if it receives anything. |
| `LayoutPortabilityTest` | A relative-mode pack resolves to sensible positions against two different synthetic terrains, respecting spacing and slope tolerance. |

`OfflineModeTest` is the important one. The offline claim is the pack format's
whole reason for existing, and it is exactly the sort of property that quietly
stops being true when someone adds a convenient lookup in a later release. A
test that fails the moment anything reaches for the network keeps it honest.

## Design notes

**Why save automatically rather than on request?** Because the failure mode of
forgetting is losing something that cost twenty minutes of inference and is
literally irreproducible — regenerating with the same prompt gives a different
campaign. Saving is not a feature here, it is a property.

**Why copy schematics into the pack instead of referencing the library?** A pack
that depends on the receiving server's library is not portable, and portability
is most of the point. The duplication is cheap, and `--slim` exists for when it
is not.

**Why relative layout by default?** Because the interesting case is a pack
moving between servers, and absolute coordinates from someone else's world are
meaningless — or worse, land a village inside a mountain. Relative layout keeps
the relationships, which is what the campaign actually depends on. `--pinned`
covers the "restore my world exactly" case.

**Why does `sites.json` live in the run?** Resolved marker coordinates are a
property of a playthrough in a world, not of the campaign. Putting them in the
pack would make the second run of a campaign in a different world subtly wrong
in a way that would take ages to diagnose.

## What's real vs. unverified

Not built. The format is designed and internally consistent, and the load-time
validation gates are specified against real failure modes rather than imagined
ones.

Unverified: pack sizes in practice (a fourteen-site campaign's `.schem` files
could be anywhere from a few hundred kilobytes to tens of megabytes, and nobody
has measured); whether relative layout genuinely produces a coherent world on a
terrain it was not authored against; and the determinism claim underpinning
slim packs, which holds only as long as the interpreter is never changed in a
way that alters output for an unchanged script. That last one deserves a note in
the implementation: **an interpreter change that alters output for an existing
script is a `packFormat` bump**, not a patch release.

## License

MIT
