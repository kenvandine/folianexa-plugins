# FlowerWatch

A **temporary, observation-only** diagnostic plugin for tracking down a
FoliaNexa cluster bug: flowers spawning very densely and getting
destroyed/regrown across every world, in a way nobody's been able to
explain by disabling plugins one at a time. It predates
[Solstice](../Solstice) (which never touches real blocks — it only does
client-side biome recoloring via PacketEvents packets — so it was never
the cause), and CoreProtect has block logging enabled the whole time.

The problem: CoreProtect records **that** a block changed, not **why**.
It doesn't distinguish a player breaking a flower from a `BlockGrowEvent`
tick, a bonemeal burst, a dispenser, or a plugin calling `Block#setType`
directly. FlowerWatch exists purely to capture the *cause* at the moment
each flower-material block changes, and to log CoreProtect's own record
for that same block right next to it — so an operator reading one log
file can see both "what FlowerWatch's own event listener saw" and "what
CoreProtect independently recorded" side by side.

FlowerWatch **never cancels or changes anything** — no auto-fix, no
auto-revert. It only logs. Uninstalling it has zero effect on whatever is
actually causing the bug; it's purely eyes on the problem.

## Install

Standard FoliaNexa plugin install — build the jar (`./gradlew build`,
output in `build/libs/flower-watch-<version>.jar`), install it on
whichever world(s) are showing the problem, restart. See "Cataloging
this plugin" below for adding it to the cluster's plugin catalog once
you've built a real release.

**This is meant to run temporarily.** Once you've found the cause, take
it back off — it isn't designed to run indefinitely (see the
performance notes on the density scanner below).

## Reading the log

FlowerWatch writes its own log, separate from the server's console/
`latest.log`, at `plugins/FlowerWatch/logs/flowerwatch.log` (rotated at
10MB × 5 files by default — `log.*` in `config.yml`). Every line is
pipe-delimited so it's easy to `grep`/`cut`/`awk`, or hand to someone
else without wading through unrelated server noise.

### `EVENT` lines — FlowerWatch's own observation

```
EVENT|<timestamp>|<world>|<chunkX,chunkZ>|<x,y,z>|<blockType>|<cause>|<player-or-->|<extra-or-->
```

- **cause** is the actual Bukkit event class that produced the change —
  `BlockGrowEvent`, `BlockSpreadEvent`, `BlockFormEvent`,
  `BlockFertilizeEvent` (or `BlockFertilizeEvent(batch)` for each
  individual flower placed by one bonemeal application),
  `StructureGrowEvent` (or `StructureGrowEvent(batch)`, same idea),
  `BlockPlaceEvent`, or `BlockBreakEvent`. This is the column that
  answers "why did this block change" — CoreProtect's own log never has
  this information at all.
- **player** is set for anything a specific player triggered directly
  (`BlockPlaceEvent`, `BlockBreakEvent`, and `BlockFertilizeEvent`/
  `StructureGrowEvent` when a player did the bonemealing); `-` for
  anything that happened without a player involved (natural growth, a
  dispenser).
- **extra** carries event-specific detail — e.g. `BlockSpreadEvent`
  records the source block it spread from; `BlockFertilizeEvent`/
  `StructureGrowEvent`'s *summary* line records how many total blocks
  were affected and how many of those were flowers, which is exactly
  the shape of the "sudden burst" you're likely looking for — a single
  bonemeal application on one grass block can place dozens of flowers
  at once, and can come from a player, a dispenser, or a plugin calling
  the fertilize API directly.

**If you're hunting a density burst, start by grepping for
`BlockFertilizeEvent` and `StructureGrowEvent` summary lines with a
large `flowersAmong=` count** — that's the most likely single mechanism
behind "flowers spawning very densely" (a lot of flowers appearing at
once from one trigger), as opposed to `BlockGrowEvent`/`BlockSpreadEvent`
which fire once per individual block.

### `COREPROTECT` lines — cross-reference

Logged immediately after the matching `EVENT` line for the same
block+coordinates (when CoreProtect is present and the per-minute lookup
budget isn't exhausted — see `coreprotect.max-lookups-per-minute`
below):

```
COREPROTECT|<timestamp>|<world>|<chunkX,chunkZ>|<x,y,z>|-|-|-|<CoreProtect's own record(s), or "no matching CoreProtect entries">
```

Read the `EVENT` and `COREPROTECT` line for the same block together:

- **CoreProtect agrees** (its record's player/action lines up with
  FlowerWatch's cause) — confirms both tools saw the same thing, useful
  for building confidence in whichever plugin/mechanism turns out to be
  the actual cause.
- **CoreProtect is silent** (`no matching CoreProtect entries`) for an
  event FlowerWatch did log — worth knowing on its own: it means
  whatever's causing this either isn't the kind of change CoreProtect's
  configuration logs, or is happening faster/differently than
  CoreProtect's own consumer thread is keeping up with.
- **CoreProtect disagrees** — its `type`/`action` don't match what
  FlowerWatch's `cause` column says — flags a real discrepancy worth
  digging into directly, rather than trusting either tool blindly.

If CoreProtect isn't installed, isn't enabled, or its API doesn't match
what FlowerWatch expects, this is logged once at startup as a
`COREPROTECT-STATUS` line explaining why, and every `EVENT` line still
gets logged on its own — the cross-reference is a bonus, not a
requirement.

### `DENSITY-ALERT` lines

```
DENSITY-ALERT|<timestamp>|<world>|<chunkX,chunkZ>|-|-|-|-|count=<N> delta=+<M>
```

A periodic scan (`density-scan.interval-seconds`, default 60s) counts
flower blocks in every loaded chunk and logs this when a chunk's count
grows by more than `density-scan.alert-threshold` (default 25) since its
previous scan — this is what catches a burst even if it happens faster
than anyone is tailing the live `EVENT` log, or via a mechanism this
plugin's listeners don't happen to cover.

## Config

See the comments in `config.yml` — every event type is independently
toggleable, the CoreProtect lookback window and per-minute lookup budget
are both configurable, and the density scanner can be restricted to
specific worlds and a bounded Y range to control its cost. Edit and run
`/flowerwatch reload` — no restart needed.

## Performance notes — read before running this on a live cluster

- The density scanner is genuinely **O(chunk volume) per loaded chunk
  per scan**. It's bounded to `density-scan.min-y`/`max-y` (default
  `-64`..`192`) and can be restricted to specific worlds via
  `density-scan.worlds`. If it's adding noticeable overhead, narrow
  those first, or raise `interval-seconds`, before turning it off
  entirely — it's often the fastest way to actually catch a burst.
- CoreProtect lookups happen synchronously on the block's owning region
  thread (the same thread the triggering event already fired on) —
  this matches how CoreProtect's own API expects to be called, but it
  does mean a slow CoreProtect DB query blocks that thread briefly.
  `coreprotect.max-lookups-per-minute` (default 60) caps how often this
  can happen across the whole plugin, specifically so a runaway flower
  burst can't also turn into a burst of DB queries — extra events past
  that budget in the same window still get logged by FlowerWatch itself,
  just without the CoreProtect cross-reference line.
- File writes use a plain `java.util.logging.FileHandler` — simple,
  reliable, appropriate for something meant to run for a few days while
  chasing a live bug, not indefinitely as a permanent addition.

## CoreProtect integration — how it actually works

`CoreProtectBridge` reaches CoreProtect's API entirely through
**reflection**, not a compile-time Gradle dependency like Solstice's
PacketEvents/PlaceholderAPI integrations. CoreProtect-CE (the fork this
cluster runs — catalog id `CoreProtect`, `github.com/PlayPro/CoreProtect`)
has no working JitPack build (every tagged version's build reports
`"Error"` as of when this was written) and isn't on Maven Central or
PaperMC's own repository, so there's no real coordinate to depend on.
Reflection against its documented `net.coreprotect.CoreProtectAPI`
class — `getAPI()`, `isEnabled()`, `blockLookup(Block, int)`,
`parseResult(String[])` — is the same consumption pattern CoreProtect's
own wiki has recommended to third-party plugins for years. Every failure
mode (CoreProtect missing, disabled, or an API shape that doesn't
match) degrades to "no cross-reference," logged once and then left
alone, never an exception that takes the rest of FlowerWatch down.

**This has not yet been verified against a live CoreProtect-CE 24.0
jar** — matching the rest of this cluster's own documentation habits
(see FoliaNexa's `CLAUDE.md`) about being explicit on what's actually
been run versus written against a documented contract. When you run
this for real: check the `COREPROTECT-STATUS` line at startup first
(it tells you plainly whether the bridge connected), and then sanity-
check that a few `COREPROTECT` lines actually contain something
sensible rather than just being non-empty.

## Cataloging this plugin

Not done yet, deliberately — same reasoning FoliaNexa's own
`docs/plugin-dev/03-submitting-for-review.md` gives: cut a real release
first, then add it to the catalog with the real URL/hash. Once you've
tagged a release and it's built:

```bash
sha256sum flower-watch-<version>.jar
```

then add an entry like this to `mgmt/src/folia_mgmt/catalog.yaml` in the
`FoliaNexa` repo (category `moderation`, matching CoreProtect, since
that's the closest fit — it's not really "gameplay" or "in-house
feature" the way Solstice/CampusLobby are):

```yaml
- id: FlowerWatch
  category: moderation
  source: in-house
  version: "0.1.0"
  download_url: null   # fill in with the real GitHub Releases asset URL
  sha256: null          # fill in with the real sha256sum output
  homepage: "https://github.com/kenvandine/folianexa-plugins/tree/main/flower-watch"
  verified: false
  notes: >
    Temporary, observation-only diagnostic plugin — logs the cause
    behind every flower-material block change and cross-references it
    against CoreProtect. Not meant to stay installed once the root
    cause of the flower-density bug is found; see its own README for
    the log format and how to read it.
```

This is intentionally left undone in this PR — only add it to the real
`catalog.yaml` once there's an actual tagged release with a real
`download_url`/`sha256` to put there.
