# 02 — Phqen1xRPG

Generates a complete, unique role-playing campaign from a Lemonade Server, builds
its world through Phqen1xWorldEditCraft, and runs it as a playable game.

- Package: `io.github.phqen1x.rpg`
- Plugin name: `Phqen1xRPG`
- `depend: [Phqen1xWorldEditCraft]`, `compileOnly` the `-api` artifact
- Lemonade is required to **author** a campaign, not to **play** one

## The central principle

**The model fills slots in a fixed mechanical vocabulary. It never authors
behaviour.**

Everything the model produces is names, prose, relationships and composition.
Every mechanic — every quest objective type, every skill effect, every boss
phase, every loot modifier — is chosen from an enum the plugin already
implements and has already tested.

This is the decision the entire plugin rests on, so it is worth being blunt
about why. The alternative — having the model emit scripts, or conditions, or
anything the server executes — fails on every axis that matters. It cannot be
validated, because "is this script correct?" is undecidable in general and
"is this script safe?" is worse. It cannot be tested, because the space of
outputs is unbounded. It fails unpredictably at runtime, hours into someone's
session, in a way no repair loop can catch. And it is a code-execution surface
driven by a text generator.

Against that, a fixed vocabulary gives up surprisingly little. A quest that says
*"Bring seven tide-glass shards from the drowned chapel to Keeper Aslaug"* is
mechanically `COLLECT(item=tide_glass_shard, count=7) → DELIVER(npc=aslaug)`.
The mechanics are boring and finite; the campaign is not. What makes each world
feel new is which nouns exist, how the quests chain, who wants what and why —
and all of that is exactly what a language model is good at.

The practical test applied throughout this design: *if the model returned
garbage for this field, would the plugin crash, or would it just be a bad
campaign?* Every field must fall in the second category.

## Campaign generation

Nine sequential stages. Each is one Lemonade call, small enough that a local
model handles it well, individually validated, individually repairable, and
individually cached to disk — so a failure at stage 7 resumes at stage 7 rather
than restarting a twenty-minute job.

Each stage receives a compacted summary of prior stages, not their full text.
Stage 5 does not need the world bible's prose; it needs the region names, site
names, NPC names, and the act structure. Keeping the carried context small is
what keeps a 30B model coherent by stage 9.

| # | Stage | Produces | Model authors | Plugin fixes |
| --- | --- | --- | --- | --- |
| 1 | **World bible** | Setting, tone, pantheon, the central threat, three-act outline | All of it — this stage is pure prose | Nothing; this is reference material for later stages |
| 2 | **Region map** | 3–6 regions: name, biome affinity, danger tier, site list | Names, descriptions, which sites go where | Biome names come from a vocabulary of real `Biome` values; danger tier is 1–5 |
| 3 | **Sites** | Per site: name, kind, natural-language build brief | Names, briefs, kind selection | `kind` ∈ `VILLAGE, DUNGEON, SHRINE, FORT, CAMP, RUIN, LAIR` |
| 4 | **NPC roster** | Name, faction, role, personality, home site, dialogue seeds | All the character work | `role` ∈ `QUEST_GIVER, MERCHANT, TRAINER, LORE, GUARD, VICTIM` |
| 5 | **Quest graph** | A DAG of quests with objectives, prerequisites, rewards | Titles, text, who gives it, what it is about, how it chains | Objective types are the eight below; prerequisites must form a DAG |
| 6 | **Classes** | 3–5 classes: name, flavour, starting kit, three abilities each | Names, flavour, which primitives to combine | Abilities compose implemented primitives only |
| 7 | **Bestiary** | Named enemies: base type, stats, equipment, loot | Names, flavour, which vanilla type, stat emphasis | Base type is a real `EntityType`; stats are bounded attribute modifiers |
| 8 | **Items & loot** | Named items and loot tables | Names, lore text, rarity, what drops where | Material is a real `Material`; effects are enchantments and attribute modifiers |
| 9 | **Boss encounters** | Per act: boss, arena site, phases | Name, flavour, phase ordering and thresholds | Phase mechanics come from the fixed list below |

Stage 3's build briefs go straight to
`WorldEditCraftService.generate()` — this is where the two plugins meet, and
it is the only place the RPG causes a structure to exist.

### The objective vocabulary

Eight types. Every quest in every campaign the plugin will ever generate is
built from these.

| Type | Parameters | Tracked by |
| --- | --- | --- |
| `KILL` | bestiary entry or `EntityType`, count, optional region | `EntityDeathEvent` |
| `COLLECT` | item id, count | Inventory scan on pickup |
| `DELIVER` | item id, count, target NPC | NPC interaction |
| `ESCORT` | NPC, destination marker | Proximity tick on the NPC's region thread |
| `REACH` | marker or coordinates, radius | Movement event, throttled |
| `INTERACT` | NPC, block-entity marker, or site | Interaction event |
| `SURVIVE` | duration, optional region | Timer plus death check |
| `CRAFT` | item id, count | `CraftItemEvent` |

Depth comes from composition, not from more types: objectives sequence within a
quest, quests form a DAG across three acts, and a single `KILL` objective feels
different when the thing being killed is this campaign's invention and the
person asking has their own reasons.

### The boss phase vocabulary

| Mechanic | Parameters |
| --- | --- |
| `SUMMON_ADDS` | bestiary entry, count, interval |
| `ARENA_HAZARD` | hazard type, region marker, interval |
| `TELEPORT` | marker set, interval |
| `ENRAGE` | attribute multipliers, duration |
| `SHIELD_UNTIL` | condition (adds dead, hazard cleared, item used) |
| `AOE_BURST` | radius, damage, telegraph duration |
| `HEAL` | amount, condition |
| `FLEE_TO` | marker, health threshold |

A phase is a list of mechanics plus a health threshold to enter it. The model
picks and orders them and writes the flavour text; the controller executes them.

## Runtime systems

| Class | Responsibility |
| --- | --- |
| `Phqen1xRpgPlugin` | Wiring only. Service lookup, config, campaign load, command executor, listener registration. |
| `RpgCommand` | `/rpg` dispatch. |
| `CampaignGenerator` | Drives the nine stages. Async throughout, resumable, progress-reporting. |
| `StageValidator` ★ | One validator per stage. Same two-message `ValidationIssue` shape as the DSL validator. |
| `CampaignStore` | Reads and writes campaign packs. See [`06`](06-campaign-pack-format.md). |
| `Campaign` ★ | The whole immutable authored content tree. |
| `RunStore` | Per-run, per-player progress. Separate from the campaign. |
| `PlayerProgress` ★ | Quest states, completed objectives, class, unlocks. |
| `QuestEngine` | Objective trackers bound to Bukkit events. |
| `ObjectiveTracker` ★ | One per objective type. Pure state machines — given an event summary, advance or don't. |
| `DialogueEngine` | Serves pre-generated dialogue trees. Optional budgeted live improv with an LRU cache. |
| `NpcManager` | Spawns and maintains NPCs. `PersistentDataContainer` tags, `EntityScheduler` for mutation. |
| `MobDirector` | Spawns bestiary mobs at sites, applies attribute modifiers and equipment. |
| `SiteBuilder` | The WorldEditCraft consumer. Requests generation, requests pastes, records returned markers. |
| `WorldLayout` ★ | Decides where regions and sites sit. Pure — takes a seed and terrain probes, returns offsets. |
| `ClassManager` | Class selection, ability cooldowns, attribute application. |
| `AbilityPrimitive` ★ | The implemented ability building blocks. |
| `LootService` | Rolls loot tables, builds `ItemStack`s from item definitions. |
| `BossController` | Runs boss encounters phase by phase. |
| `PartyManager` | Grouping, shared quest credit. |
| `CodexGui` | Inventory-GUI journal: quests, bestiary, lore, class. |
| `RewardService` | Grants items, XP, unlocks, dialogue flags. |

★ = no `org.bukkit` imports, unit-tested. The generator, validators, campaign
model, objective trackers, world layout and ability primitives are all
Bukkit-free, which means the majority of this plugin's logic is testable without
a server.

## Site building

The one interesting integration, worth spelling out.

1. `SiteBuilder` takes a site from stage 3 — name, kind, and a build brief in
   English.
2. It calls `WorldEditCraftService.generate(new GenerateRequest(brief, sizeHint,
   tags, seed))`. It gets back a `SchematicHandle`. It does not look inside.
3. `WorldLayout` picks a world position: region anchor, plus an offset derived
   from the campaign seed, plus a terrain probe for a flat-enough spot at a
   sensible height.
4. `SiteBuilder` calls `paste(handle, request)` and awaits a `PasteResult`.
5. `PasteResult` carries **every marker in the schematic, in world
   coordinates**. `SiteBuilder` writes those into the run's site record.
6. `NpcManager` spawns this site's NPCs at the `npc` markers. `MobDirector`
   arms the `mob` markers. `LootService` fills chests at `loot` markers.
   `BossController` places the act boss at `boss`. Quest `REACH` objectives
   target `entrance`.

The RPG never reads a block. If WorldEditCraft's generator produces a building
with a different interior than last time, everything still works, because the
contract is a list of named points and not a floor plan.

Sites are built lazily by default (`generation.build-sites: lazy`) — a site is
pasted the first time a player enters its region, not all at once during
generation. `eager` builds everything up front, which is slower to start and
smoother to play.

## Commands

| Command | What |
| --- | --- |
| `/rpg campaign generate <theme…>` | Author a new campaign. Async, long, resumable, progress-broadcast. |
| `/rpg campaign list` · `info <id>` | Browse what is stored. |
| `/rpg campaign load <id>` | Make a campaign active. No Lemonade needed. |
| `/rpg campaign start <id>` | Begin a **new run** of a campaign. |
| `/rpg campaign runs` · `resume <run-id>` | Multiple runs of one campaign can coexist. |
| `/rpg campaign bake <id>` | Pre-generate any live dialogue so the pack replays fully offline. |
| `/rpg campaign export <id>` · `import <file>` | Portable `.rpgpack` bundles. |
| `/rpg campaign clone <id> <name>` · `delete <id>` | |
| `/rpg quests` · `/rpg quest <id>` | Journal. |
| `/rpg class [name]` | Pick or inspect your class. |
| `/rpg codex` | The GUI journal — quests, bestiary, lore. |
| `/rpg party <invite\|leave\|list>` | |
| `/rpg where` | Waypoint to your current objective. |
| `/rpg admin <give\|setquest\|rebuild\|status\|reload>` | |

Permissions: `rpg.play` (`default: true`), `rpg.party` (`true`),
`rpg.campaign` (`op`) for generate/load/start/import/export, `rpg.admin` (`op`).

## Configuration (`config.yml`)

```yaml
lemonade:
  required: false                 # false = play stored campaigns with no inference server
  # base-url etc. inherited from Phqen1xWorldEditCraft's InferenceService
  # unless overridden here

campaign:
  active: ""                      # campaign id loaded on enable; blank = none
  auto-start-run: true            # start a run for a player who joins with none
  directory: "campaigns"
  runs-directory: "runs"

generation:
  regions: [3, 6]                 # min, max
  sites-per-region: [2, 5]
  quests-per-act: [4, 8]
  classes: [3, 5]
  bestiary-size: [8, 16]
  build-sites: lazy               # lazy | eager
  site-size-hint: [48, 32, 48]
  stage-timeout-seconds: 300
  max-attempts-per-stage: 3

world:
  target-world: "world"
  region-spacing: 900             # blocks between region anchors
  site-min-spacing: 120
  max-site-slope: 6               # terrain flatness tolerance when siting

dialogue:
  live-improv: false              # ask Lemonade for unscripted lines at runtime
  improv-budget-per-hour: 40
  improv-cache-size: 500

difficulty:
  scaling: party                  # flat | party | level
  boss-health-multiplier: 1.0
```

| Key | Default | Why |
| --- | --- | --- |
| `lemonade.required` | `false` | The headline of [`06`](06-campaign-pack-format.md). A stored campaign plays with the inference box switched off. |
| `generation.build-sites` | `lazy` | Building forty structures up front is a long job. Lazy spreads it across play. |
| `dialogue.live-improv` | `false` | Off by default. It is a lovely feature and it makes every session depend on a reachable model, so it is opt-in and budgeted. |
| `world.max-site-slope` | `6` | Prevents a village being pasted half-inside a mountain. |
| `generation.stage-timeout-seconds` | `300` | A local model on a busy box is slow. Generous, and the stage is resumable if it blows. |

## Test plan

JUnit 5, no mocking library — the repo convention.

| Test | Asserts |
| --- | --- |
| `StageValidatorTest` | One per stage. Missing fields, out-of-vocabulary enum values, and dangling references (a quest naming an NPC that does not exist) are all caught with model-readable messages. |
| `QuestGraphTest` | Prerequisites form a DAG; cycles are rejected. Every quest is reachable from act 1. Every objective's target exists. |
| `ObjectiveTrackerTest` | One per objective type. Advance on the right event, ignore the wrong one, complete exactly once, survive a restart mid-progress. |
| `CampaignSerializationTest` | Campaign → JSON → campaign is identical. Unknown future fields are preserved, not dropped. |
| `WorldLayoutTest` | Given a fixed seed and synthetic terrain probes, site positions are deterministic, respect `site-min-spacing`, and reject slopes over tolerance. |
| `AbilityPrimitiveTest` | Each primitive's effect is computed correctly; cooldowns gate. |
| `LootServiceTest` | Fixed seed gives fixed rolls. Rarity weighting is right. Every generated item maps to a real `Material`. |
| `BossPhaseTest` | Phase transitions fire at the right thresholds, in order, once each. |
| `PackRoundTripTest` | Covered in [`06`](06-campaign-pack-format.md) — generate, export, import, replay. |
| `CampaignGeneratorTest` | Against a fake Lemonade `HttpServer` returning canned stage responses. Asserts stage ordering, that carried context stays under budget, that a stage failure resumes rather than restarts, and that `max-attempts-per-stage` is honoured. |

Not testable without a server, and needing manual play: NPC behaviour, dialogue
flow, boss encounters, and the actual feel of a generated campaign — which is
the thing that matters most and the thing no test will ever tell you.

## Design notes

**Why nine stages rather than one big call?** A local model asked for a whole
campaign in one response will produce something that is coherent for the first
third and drifts after. Nine small calls each fit comfortably in the model's
working memory, each validate independently, each repair independently, and each
cache — so the twenty-minute job survives a failure at minute eighteen. The
cost is nine round trips, which against a local server is fine.

**Why is progress stored separately from content?** Because "play it again"
must be a real operation. The campaign pack is immutable authored content; a
*run* is a mutable instance of it. Starting a second run of the same pack gives
a genuinely fresh game with identical content, which is exactly what you want
when a group wants to replay a campaign they liked, or when a new group arrives.

**Why lazy site building?** Forty structures at a few seconds of pasting each is
minutes of region-scheduler work. Doing it as players arrive spreads the cost
and means a campaign is playable almost immediately after generation finishes.

**Why is live dialogue off by default?** Because the moment it is on, a session
depends on a reachable inference server, and the plugin's best property — that
a stored campaign plays offline — quietly stops being true. When it is on it is
budgeted, cached, and bakeable into the pack.

## What's real vs. unverified

Not built. Not compiled. Not run.

The mechanical vocabularies (objectives, boss phases, ability primitives) are
designed against real Bukkit capabilities — `EntityDeathEvent`, `CraftItemEvent`,
attribute modifiers, `PersistentDataContainer` — and nothing in them requires an
API that does not exist. But none of it has been written.

Unverified and important: whether a locally-served model produces a *good*
campaign, not merely a valid one. Whether nine staged calls stay coherent across
stages. How long generation actually takes on real hardware. And the design bet
named in [`00`](00-project-plan.md#risks) — whether a fixed mechanical vocabulary
still feels varied by the third campaign. That last one cannot be answered by
testing; it needs three campaigns and an honest opinion.

## License

MIT
