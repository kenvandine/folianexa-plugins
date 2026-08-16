# HungerGames

A configurable battle-royale minigame: tributes queue into an arena, and
once enough have joined a countdown kicks off a match — a grace period to
grab starting loot, then a continuously shrinking world border forcing
survivors together until one tribute remains (or a deathmatch is forced if
the clock runs out first). Optional randomized "twists"
(Quarter-Quell-style rule changes, e.g. no grace period, doubled loot, a
much tighter arena) can shake up any given match.

Follows the pattern of the open-source
[Hunger Games plugin on Modrinth](https://modrinth.com/plugin/hungergames)
but is an original, from-scratch implementation for this cluster — not a
port or reimplementation of it.

## Design: maps and twists are config-only

Both of this plugin's headline extensibility points are pure config.yml
edits — no code changes, no rebuild:

- **New maps.** Each arena in `arenas:` is just coordinates: its dedicated
  world's name, a border center/radius, a list of tribute spawn points, an
  optional set of deathmatch spawn points, and the exact locations of the
  (empty) chests to stock with loot. Build a new map in a fresh Bukkit
  world, place some empty chests, copy one `arenas:` block, done. See "One
  arena, one world" below for why each map needs its own world.
- **New twists.** Each entry in `twists.pool` is a set of optional rule
  overrides (grace period length, loot multiplier, game length, border
  sizes, starting potion effects) layered onto the base `game:` rules for a
  single match, plus a display name/description/weight. Every field is
  optional — set only the ones a given twist changes. Add a new twist, or
  retune an existing one's odds, purely in config.yml.

See config.yml's comments for the full field list on both.

## One arena, one world

Each arena is expected to be its own dedicated Bukkit world. That lets the
shrinking play area ride on Bukkit's real per-world `WorldBorder` API —
native warning fog, push-back, and damage, plus a smoothly animated shrink
— instead of a hand-rolled per-tick distance/damage loop. It also means
concurrent matches never collide on the same coordinate space and a map
can be reset for its next match just by reloading/regenerating its world
(not handled by this plugin — pair it with your world-management tooling
of choice).

## Building

Requires Java 21.

```bash
./gradlew build
# build/libs/hungergames-0.1.0.jar
```

## Installing (for local testing)

Drop the built jar into a Folia/Paper 1.21.4 server's `plugins/` directory
and (re)start the server. See
[the `FoliaNexa` plugin-dev environment setup guide](https://github.com/kenvandine/FoliaNexa/blob/main/docs/plugin-dev/01-environment-setup.md)
for running a local test server. You'll need at least one dedicated arena
world set up and listed under `arenas:` before a match can start — the
shipped config.yml ships one placeholder `example` arena pointing at a
`hg_example` world as a template to copy/edit.

## Usage

- `/hungergames join <arena>` (alias `/hg join <arena>`) — queue for an
  arena's next match.
- `/hungergames leave` — leave the queue, or forfeit (counts as an
  elimination, no kill credit to anyone) if a match is already underway.
- `/hungergames list` — show every configured arena, its current state,
  and how many tributes are queued/playing.
- `/hungergames twists` — show the configured twist pool and their odds.
- `/hungergames forcestart <arena>` *(op)* — skip the countdown and start
  immediately with whoever's queued.
- `/hungergames stop <arena>` *(op)* — cancel the arena's current match
  (any state) and return everyone to the lobby.
- `/hungergames reload` *(op)* — reload config.yml. A match already in
  progress on an arena keeps its old settings until it finishes; the new
  config applies starting that arena's *next* match.

## Configuration

`config.yml` is generated on first run. It controls the base match rules
(minimum/maximum players, countdown/grace-period/game lengths, border
sizes, chest loot count), the shared loot table, the twist pool, and the
list of arenas. See its comments for the full field-by-field breakdown.

## Not implemented (possible future additions)

- No sponsor/care-package drops, kill-streak announcements, or a
  scoreboard/tab-list HUD — this ships the core match loop and the two
  extensibility points (maps, twists) described above, not every feature
  of the plugin it's patterned after.
- `corner1`/`corner2` on an arena are informational only right now — a
  "no building/leaving the play area" enforcement could read them later.
- No automatic world reset between matches on the same arena; pair this
  plugin with your own world-regeneration tooling if you want a pristine
  map every match.

## License

MIT
