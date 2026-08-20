# 07 — Folia safety

The threading rules both plugins must obey, and the precedents in this
repository they are drawn from.

## The rule

Folia ticks regions in parallel. Code running on one region's thread must not
touch another region's blocks or entities. Chunks, entities and world state each
have an owning thread, and reaching across is a thread-check failure at best and
silent corruption at worst.

Practically, for these two plugins:

| Touching | Scheduler |
| --- | --- |
| Blocks at a location | `Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, …)` |
| A player or entity | `Bukkit.getRegionScheduler().run(plugin, entity.getLocation(), …)` or `entity.getScheduler().run(…)` |
| World-scoped state (spawn, border, time, weather) | `Bukkit.getGlobalRegionScheduler()` |
| HTTP, file I/O, timers, anything slow | `Bukkit.getAsyncScheduler()` |
| Cross-region movement | `player.teleportAsync(dest)` |

Neither plugin may use `BukkitRunnable`, `runTaskLater`, or `runTaskTimer`.
None of the three existing plugins in this repo do.

## Precedents to copy

The idioms below are already in this repository and both new plugins should
match them rather than inventing variants.

### Chunk-bucketed block placement

`campus-lobby/src/main/java/io/github/kenvandine/campuslobby/SceneBuilder.java:74-87`
is the pattern the entire paste engine is built on:

```java
Map<Long, List<BlockPlacement>> byChunk = new HashMap<>();
for (BlockPlacement placement : scene.blocks()) {
    int worldX = originX + placement.dx();
    int worldZ = originZ + placement.dz();
    byChunk.computeIfAbsent(chunkKey(worldX >> 4, worldZ >> 4), key -> new ArrayList<>())
            .add(placement);
}

for (List<BlockPlacement> placements : byChunk.values()) {
    BlockPlacement first = placements.get(0);
    int chunkX = (originX + first.dx()) >> 4;
    int chunkZ = (originZ + first.dz()) >> 4;
    Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, task ->
            placements.forEach(p -> placeBlock(world, …)));
}
```

Group by chunk, submit one task per chunk. Never place a block from a thread
that does not own its chunk.

### FIFO ordering within a chunk

`SceneBuilder` relies on a property worth knowing: **a chunk's region tasks run
in submission order.** Its comment says so explicitly — the clear pass is
scheduled before the block placements "so it runs first within any chunk the two
share — each chunk's region task queue is FIFO in submission order."

The paste engine uses the same property for its two passes: pass 2 (torches,
doors, block entities) is simply submitted after pass 1 for the same chunk.

### Counter-based fan-in

`folianexa-stats/.../FoliaNexaStatsPlugin.java:138-149` shows how to wait for a
set of region tasks before doing something async: an `AtomicInteger remaining`,
decremented in a `finally` inside each region task, with the last one to hit
zero triggering the next step.

Both plugins need this. The paste engine uses it to know when pass 1 is done
across every chunk that could neighbour a pass-2 chunk. The RPG's `SiteBuilder`
uses it to know a site is fully built before spawning NPCs into it.

### World-scoped state

`SceneBuilder.java:170` sets spawn point and world border through
`Bukkit.getGlobalRegionScheduler()`, not a chunk-targeted one — those are
properties of the world, not of a chunk.

### Async I/O with a documented contract

`folianexa-stats/.../HttpMgmtClient.java` has no `org.bukkit` imports but does
real I/O, and its javadoc states the contract plainly: callers "must only ever
invoke it from `Bukkit.getAsyncScheduler()`, never a game-tick thread."

`LemonadeClient`, `SchematicLibrary`, `CampaignStore` and `UndoJournal` all
carry the same note. It is the only thing keeping a compile-clean class from
being called from the wrong place.

### Task handles

Stored as `Object`, cancelled with a pattern match:

```java
if (handle instanceof io.papermc.paper.threadedregions.scheduler.ScheduledTask task) {
    task.cancel();
}
```

And `onDisable()` stays near-empty — Paper cancels tasks scheduled through its
own schedulers automatically on plugin disable. All three existing plugins carry
that comment.

## Phqen1xWorldEditCraft

```
/wec generate
  ├─ async     prompt build, HTTP, JSON parse, validation, repair loop
  ├─ pure      rasterization (no scheduler — it is a function call)
  ├─ async     .schem write, library index update
  └─ region    paste, one task per chunk, budgeted
```

Everything up to the paste is async. The paste is the only part that touches the
world. There is no step that needs a game thread for anything else.

Specific obligations:

- **`PastePlan` is pure.** Bucketing, transform, batching and pass separation
  happen off-thread and produce a plan; `PasteEngine` only submits it. This is
  what makes the hard part unit-testable without a server.
- **Per-tick budget.** `SceneBuilder` places a whole chunk in one task, which is
  fine for a lobby and not fine for a 128³ structure. Batches of
  `paste.blocks-per-tick` re-submit the next batch on completion, so a region
  tick is never held for unbounded work.
- **Undo reads happen on the owning thread.** Prior block states are read inside
  the same region task that is about to overwrite them, then handed to the async
  journal writer. Never read blocks from the async side.
- **Progress reporting is a player mutation.** Boss bar updates go through the
  requesting player's region, not the region doing the placing.
- **`teleportAsync`** for any operator convenience teleport.

## Phqen1xRPG

More entity work than any existing plugin here, which means `EntityScheduler` —
a first for this repository.

```
/rpg campaign generate
  └─ async     nine stages, HTTP, validation, pack writes
                 └─ delegates every structure to WorldEditCraft

site building
  ├─ async     WorldEditCraftService.generate()
  ├─ region    paste (inside WorldEditCraft)
  └─ region    NPC spawn, mob arming, chest fill — at the resolved markers

gameplay
  ├─ event     objective tracking (already on the right thread)
  ├─ entity    NPC and mob mutation via entity.getScheduler()
  ├─ region    player mutation via the player's own region
  ├─ global    world state — time, weather, border
  └─ async     progress writes, dialogue cache, campaign I/O
```

Specific obligations:

- **NPC and mob mutation uses `entity.getScheduler()`**, not a region scheduler
  keyed on a stale location. An entity that moves between regions between your
  lookup and your task is a real race; `EntityScheduler` follows the entity and
  is the correct tool. This differs from `folianexa-stats`, which uses
  `getRegionScheduler().run(plugin, player.getLocation(), …)` — acceptable there
  because it reads a player who is by definition present, but not the right
  choice for mobs the plugin spawns and then mutates later.
- **Boss controllers are per-encounter, not global.** A boss's phase timer runs
  on the boss entity's own scheduler. Two bosses in two regions never share a
  thread.
- **Objective tracking runs on the event thread.** A Bukkit event is already
  delivered on the owning thread of the thing it concerns. `ObjectiveTracker`
  advances there and hands the persistence write to async. It must not schedule
  a region task to look at what it was just handed.
- **Progress writes are async and debounced.** Every objective tick writing a
  file would be pathological; batch and flush on an async interval, plus
  immediately on quit and on shutdown.
- **Party credit crosses regions.** Awarding a kill to four party members in
  four regions means four scheduled tasks, one per member's own region — never
  one task mutating four players.

## Configuration touching game state

Both plugins reload config with `reloadConfig()` on the async scheduler, then
apply any derived state through the appropriate scheduler — the shape
`hungergames` uses for its `reload<X>Config()` methods. A reload must never
mutate live world state from the command thread.

## Test implications

None of this is directly unit-testable — there is no Folia in a JUnit run, and
this repo does not use MockBukkit.

The response is architectural: **push everything schedulable into pure classes
and test those.** `PastePlan` decides all the bucketing and batching and is
testable; `PasteEngine` only submits what it is given and is not. `WorldLayout`
decides where sites go and is testable; `SiteBuilder` only executes and is not.
The Bukkit-touching classes stay thin enough to review by eye.

What remains must be checked on a live Folia server, and should be, before
either plugin is offered to the FoliaNexa catalogue:

- Paste a large schematic that spans many chunks; watch for thread-check
  failures in the log.
- Paste while players are in the target area.
- Cancel a paste mid-run; confirm it unwinds and undo still works.
- Run a boss encounter with players in two different regions.
- Reload config during a paste and during a generation.

`docs/plugin-dev/03-submitting-for-review.md` in the
[FoliaNexa](https://github.com/kenvandine/FoliaNexa) repo has the self-review
checklist this list is an extension of, and it is explicit that a passing build
does not replace a live smoke test.

## What's real vs. unverified

The precedents cited are real code in this repository, quoted by file and line,
and the rules derived from them are the rules those three plugins already
follow.

Unverified: everything about the new plugins, since they do not exist. In
particular the paste engine's behaviour under load is the single largest Folia
unknown in the project — chunk-bucketing is correct by construction, but whether
`paste.blocks-per-tick: 2048` keeps region ticks inside budget on real hardware
is a guess, and the `EntityScheduler` usage in the RPG has no precedent in this
repo to copy from.

## License

MIT
