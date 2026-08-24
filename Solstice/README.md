# Solstice

A from-scratch, Folia-native seasons, calendar, and temperature plugin for Minecraft: Java
Edition servers. Built against `dev.folia:folia-api:26.2.build.5-beta` and
`com.github.retrooper:packetevents-spigot:2.13.0`.

Solstice is **not** derived from, and is not affiliated with, any existing seasons plugin. Its
feature set was scoped from public documentation only (see `PLAN.md` §0 for the ground rules this
project was built under); nothing here is a port, translation, or reimplementation of anyone
else's code.

For the full feature list, every command/permission/placeholder, and the public API surface, see
[`docs/FEATURES.md`](docs/FEATURES.md). This README covers build/install, the Folia design
decisions, and how to verify all of it works on a real server.

## Requirements

- A Folia server, `26.2.build.x-beta` (or the `26.1.2` stable line — the plugin has no NMS and no
  version-bridge code, so it should work unmodified against either; only the beta line has been
  target-tested here).
- Java 21+.
- [PacketEvents](https://github.com/retrooper/packetevents) installed as a **separate plugin**,
  if you want seasonal biome recoloring (`visuals.biome-colors.enabled`, on by default). Without
  it, Solstice logs a warning and runs everything else normally.
- [PlaceholderAPI](https://www.spigotmc.org/resources/placeholderapi.6245/), optional, for the
  `%solstice_*%` placeholders.
- WorldGuard, optional — see "Claim protection" below.

## Building

```
./gradlew build
```

Produces `build/libs/Solstice-<version>.jar`, shaded (PacketEvents and PlaceholderAPI are **not**
shaded in — they must be installed as their own plugins; Solstice only depends on their API at
compile time).

## Feature status

### Fully implemented

- **Calendar & seasons** — configurable months/weekdays/day length (`calendar.yml`), season
  derivation purely from date (`config.yml`'s season start dates), sub-season color-blend phase
  tracking, variable day/night length driven by Solstice itself (vanilla's daylight cycle is
  disabled per managed world).
- **Temperature** — the full modifier model from season/weather/time-of-day/biome/altitude/water/
  sprinting/nearby-blocks/armor/food, configurable effect thresholds (freezing damage, slowness,
  hunger, sweating, burning, healing-disabled, cold breath), custom temperature items by material
  + optional CustomModelData, action-bar display, Celsius/Fahrenheit toggle.
- **World effects** — winter water freeze/thaw spreading from shorelines, snow accumulation during
  storms, winter crops requiring a roof to grow, summer's 2x exposed-crop growth, summer
  husk / winter stray mob replacement, autumn pumpkin-headed mobs, spring flowers / summer berry
  bushes with per-biome-category density (or disabled entirely) via `world-effects.flora.biome-density`
  in `config.yml`.
- **Visuals** — fireflies, shooting stars, falling leaves (spring/summer/autumn variants), winter
  night-sparks, cold-breath and sweat particles; client-side seasonal biome recoloring over
  PacketEvents (see "Biome colors" below).
- **Events** — the full generic contract for Christmas/New Year/Halloween/Easter (enabled, name,
  disabled-worlds, start/stop commands) plus custom dated/weekly/daily events from
  `custom-events.yml` with placeholder substitution and PlaceholderAPI passthrough. Christmas
  gift-loot and Halloween mob potion buffs are wired; see "Documented gaps" for the rest.
- **Commands & permissions** — all commands from the plan under `solstice.*`, `/solstice` (alias
  `/sol`) as the admin root.
- **PlaceholderAPI** — `season`, `next_season`, `days_until_next_season`, `day`, `weekday`,
  `month`, `month_asname`, `year`, `seasonlength`, `time`, `active_events`, `next_event`,
  `days_until_next_event`, `biome`, and the temperature family, under the `solstice` prefix.
- **Public API** — `SolsticeAPI.getInstance()`, documented threading contract (getters are
  lock-free from any thread; mutators hop to the correct Folia scheduler internally).

### Documented gaps

These were cut deliberately to avoid shipping half-working versions of large, separable features:

- **Spawn-rate/frequency tuning** — extra passive-mob babies in spring, "jungle animals
  everywhere" in summer, increased bee/bat/mooshroom/frog/fox spawns, day-length-based spawn
  changes. Needs a spawn-injection system beyond what a `CreatureSpawnEvent` listener can do well.
- **Event flavor mechanics** beyond the generic start/stop contract: procedural Christmas trees,
  New Year village-detection fireworks, Easter hidden eggs and killer bunnies.
- **`/solstice install <generator>`** — responds with a clear "not implemented" message. Wiring a
  custom `ChunkGenerator` for seasonal terrain-gen effects is its own subsystem.
- **Sub-season color blending at the packet layer.** The season/date engine computes sub-season
  blend phases correctly, but `BiomeColorManager` currently injects only the four full-season
  color sets, not all 20 (4 seasons × 5 phases) blend variants. Full-season colors cover ~66% of
  each season's duration; the transition windows currently snap instead of blending client-side.
- **Live-server verification of the biome-color packet pipeline.** The registry-injection and
  chunk-biome-remap code compiles against PacketEvents' exact verified API shapes (see `PLAN.md`
  §6), but has not been exercised against a real client connection. The two likeliest failure
  modes if something's wrong: known-packs negotiation still sending reference-only registry
  entries (fixed by the known-packs-clearing interceptor, but unverified live), or another plugin
  renumbering biome IDs and breaking the vanilla-id → custom-id map built here.
- **Claim protection** — `ClaimGuard` is a real interface every physical world edit (snow, ice,
  flora) already checks, but only the always-allow `NOOP` implementation is wired up. Add a
  WorldGuard-backed implementation and set `claim-guard.provider: worldguard` in `config.yml` to
  enable it (`Solstice.claimGuard()` is the injection point).
- **World-data / player-toggle persistence uses flat YAML files**, not a database. Fine at small
  scale; revisit if you're running this on a large network.

## Folia design notes

- `platform/Schedulers.java` is the **only** class allowed to touch a Folia scheduler API
  directly. Nothing in this codebase calls `Bukkit.getScheduler()` — the shaded jar is checked for
  this on every build (see below).
- Per-world season/calendar state is an immutable `WorldSeasonState` record behind an
  `AtomicReference`, swapped only by the global region scheduler. Every other thread reads it
  lock-free. This is also why the PacketEvents biome-color rewriter — which runs on Netty threads,
  not region threads — can read season state safely without any locking.
- World time and weather are global-region-owned; all `setTime`/gamerule changes happen on
  `Bukkit.getGlobalRegionScheduler()`.
- Physical block edits (snow, ice, flora) are scheduled per-chunk on `Bukkit.getRegionScheduler()`,
  anchored to a `Location` in that chunk, so each region handles its own chunks independently.
- Per-player state (temperature, particles) runs on `entity.getScheduler()` so it follows the
  player across regions instead of living on one region thread.

To re-verify the no-BukkitScheduler guarantee after changes:

```
./gradlew build
unzip -p build/libs/Solstice-*.jar '*.class' | strings | grep -i bukkitscheduler
```

Should print nothing.

## Manual test plan

There is no Folia server bundled with this repo, and none of this has been exercised against a
live client (see "Documented gaps" above). Compiling cleanly proves nothing about runtime
behavior — Folia's characteristic bugs are thread-affinity crashes that only show up when a real
region is ticking. Work through this whole list before trusting the plugin on a real server, and
especially before trusting the biome-color packet pipeline at all.

**Setup:**

1. Download a Folia server jar for your target version from
   <https://papermc.io/downloads/folia>, accept the EULA, and get it booting standalone first.
2. `./gradlew build`, then copy `build/libs/Solstice-<version>.jar` into `plugins/`.
3. Also install [PacketEvents](https://github.com/retrooper/packetevents) (needed for biome-color
   tests) and, optionally, PlaceholderAPI and a scoreboard/placeholder-viewer plugin.
4. Use at least **two players** (or one player plus an alt account) so you can spread them across
   distant chunks — loading terrain 1000+ blocks apart forces Folia to tick separate regions on
   separate threads, which is the whole point of testing on Folia instead of Paper.
5. Have `/solstice reload`, `/solstice setdate`, `/solstice set`, and `/solstice pausetime` handy
   throughout — real-time waiting for seasons or day/night to cycle is impractically slow, so the
   plan below fast-forwards with these instead.

### 1. Boot and load sanity

- [ ] Server boots with the jar installed and logs `Solstice enabled on ...` with no stack traces.
- [ ] `/plugins` shows Solstice as enabled (green). If PacketEvents isn't installed, the log should
      contain a *warning* about biome-colors being skipped, not an error, and the server should
      otherwise start normally.
- [ ] `plugins/Solstice/` was created with `config.yml`, `calendar.yml`, `temperature.yml`,
      `events.yml`, `custom-events.yml`, `biomes.yml`, and `lang/en_US.yml` all present with their
      shipped defaults.
- [ ] `/solstice` with no args (and `/sol`) prints the subcommand help list.
- [ ] Confirm the build still has no forbidden scheduler calls:
      `unzip -p build/libs/Solstice-*.jar '*.class' | strings | grep -i bukkitscheduler` prints
      nothing.

### 2. Calendar & time

- [ ] `/season` reports a season, date, and time that look sane for a fresh world (should match
      `calendar.yml`'s configured start date).
- [ ] Stand in the world and watch the sky over a couple of real minutes — the sun/moon should move
      continuously; the vanilla `doDaylightCycle` gamerule should read `false`
      (`/gamerule doDaylightCycle`).
- [ ] `/solstice pausetime`, then wait — the clock and `/season`'s date/time should freeze. Run it
      again to resume; time should continue from where it left off, not skip ahead.
- [ ] `/solstice setdate 15/07/1` then `/season` — date should read exactly 15 July, year 1, and
      the reported season should match whatever `config.yml`'s season-start dates say for that
      date.
- [ ] Restart the server (or `/stop` and reboot) after letting time pass, then `/season` again —
      the date should have persisted (via `plugins/Solstice/worlddata/<world>.yml`), not reset to
      the calendar's start date.

### 3. Seasons & sub-seasons

- [ ] `/solstice set spring`, `summer`, `fall` (verify `fall` is accepted as an alias for autumn),
      and `winter` each jump the date to that season's configured start and `/season` reflects it
      immediately.
- [ ] `/solstice nextseason` from any season advances to the *next* season in Spring → Summer →
      Autumn → Winter → Spring order, not an arbitrary one.
- [ ] `/solstice setdate` to a date a few days before a season boundary (e.g. one day before your
      configured summer start), then advance the date one day at a time — `/season`'s season
      should flip exactly on the boundary date, not before or after.
- [ ] Fire a listener (or a scratch plugin) on `SeasonChangeEvent` and `DayChangeEvent`, cross a
      season boundary via `/solstice setdate`, and confirm both fire with the correct
      old/new season and world. Cancel `SeasonChangeEvent` from that listener and confirm the
      season does *not* change even though the date did.

### 4. Temperature

- [ ] `/toggletemperature` shows/hides the action-bar readout; toggle it back on before continuing.
- [ ] `/solstice setdate` into winter, stand at sea level in a temperate/frozen biome — action bar
      should show a cold, blue-tinted reading. `/solstice setdate` into summer at the same spot —
      should flip to a hot, red-tinted reading.
- [ ] `/togglefahrenheit` — the displayed unit and number should convert correctly (spot-check the
      math: `F = C × 9/5 + 32`).
- [ ] Stand next to a lit `CAMPFIRE` or `LAVA` — apparent temperature should visibly rise; walk
      away and confirm it drops back down within a couple of recalculation cycles (2s default).
- [ ] Equip a full leather armor set — temperature should rise (~+20°C by default); swap to a full
      diamond set — smaller rise (~+5°C). Remove armor — reading drops back.
- [ ] Jump in water in winter, get out, and watch the action bar — should show a cold penalty that
      decays back to normal over roughly 20+ seconds, not disappear instantly.
- [ ] Sprint around — temperature should tick up slightly (+4°C by default) while sprinting, back
      down when you stop.
- [ ] Force extreme cold: `/solstice temperature modify <you> -30 60`, confirm you take periodic
      damage, get Slowness, and see cold-breath particles; wait 60s and confirm the effect expires
      on its own. Then `/solstice temperature modify <you> 40 60` and confirm sweat particles,
      then (pushing well past the burning threshold) that you catch fire.
- [ ] `/solstice temperature clear <you>` while a modifier is still active — apparent temperature
      should drop back to the unmodified value immediately.
- [ ] `/solstice temperature toggle` — action bar and all temperature effects should stop updating
      entirely; toggle again to resume.
- [ ] If you configured a `temperature.yml` → `custom-items` entry, hold/wear the matching item and
      confirm the modifier applies; change its CustomModelData (if configured) and confirm it stops
      applying.
- [ ] Set `disabled-dimensions: [NETHER]` in `temperature.yml`, `/solstice reload`, travel to the
      Nether — action bar should disappear there and reappear back in the overworld.

### 5. World effects

- [ ] **Snow/ice**: `/solstice set winter` near an open lake/river, wait a minute or two (chunks
      must stay loaded) — shoreline water should start freezing into ice and spreading inward;
      during a storm, exposed grass/dirt should accumulate snow layers. `/solstice set spring` (or
      any non-winter season) and wait — the ice/snow Solstice placed should revert on its own.
      Confirm blocks *outside* your loaded chunks are untouched (walk out and back to trigger
      re-processing) and that pre-existing vanilla ice/snow biomes aren't force-melted.
- [ ] **Crops**: `/solstice set winter`, plant wheat/carrots in the open (no roof) — it should not
      progress through growth stages over time. Place a block directly above the same crop — it
      should resume growing. `/solstice set summer`, plant an exposed (no-roof) crop and compare
      its growth speed against a manually-timed control if possible — should visibly grow faster
      than one square roofed indoors.
- [ ] **Flora**: `/solstice set spring`, wait a minute in a loaded area with grass blocks — flowers
      should sparsely appear. `/solstice set summer` — sweet berry bushes should start appearing
      instead, and the spring flowers should clear. `/solstice set autumn` — both should clear with
      nothing replacing them.
- [ ] **Flora density**: in `config.yml`, set your current biome's category under
      `world-effects.flora.biome-density` to `0`, `/solstice reload` — that biome should stop
      generating flowers/berry bushes entirely while other biomes are unaffected. Set it to a value
      well above `1.0` (e.g. `5.0`) instead — flowers/berries should visibly appear denser than the
      default rate in that biome, without suppressing generation in biomes still left at `1.0`.
- [ ] **Mob replacements**: `/solstice set summer`, find/force a zombie spawn (e.g. a monster
      spawner or dark area) — should spawn as a husk instead. `/solstice set winter`, same for a
      skeleton spawner — should spawn as a stray. `/solstice set autumn`, spawn/observe a batch of
      hostile mobs — roughly 1 in 5 (default 20%) should be wearing a carved pumpkin.
- [ ] All of the above should behave identically if you repeat them in a second world that Solstice
      manages, and should do **nothing** in a world listed under `worlds.disabled-worlds`.

### 6. Visuals

- [ ] **Particles**: `/toggleseasonparticles` off, confirm nothing below fires; turn back on.
      `/solstice set spring`, stand near birch/oak trees at night (`/solstice pausetime` once it's
      night to hold it there) — fireflies should appear occasionally (they're randomized, so give
      it a few minutes). `/solstice set summer` at night near midnight with client render distance
      ≥10 chunks — watch for occasional shooting stars. `/solstice set summer` in daytime under
      tree canopy — plain falling leaves. `/solstice set autumn`, same spot — colored falling
      leaves instead. `/solstice set winter` at night — roughly 1 night in 5 should show sky
      "sparks"; since it's a world-wide per-night roll, you may need several night cycles
      (`/solstice pausetime` off, let a night pass, repeat) to see it trigger.
- [ ] **Biome colors** (requires PacketEvents installed): `/toggleseasoncolors` on, `/solstice set
      winter`, then fully disconnect and reconnect (colors are applied at chunk-send time, so a
      reconnect is the reliable way to force a resend) — grass/foliage/sky/water should visibly
      shift toward winter's pale/blue palette. `/solstice set summer`, reconnect again — should
      shift to vivid green. This is the least-verified part of the plugin (see "Documented gaps"):
      if colors *don't* change at all, check the server log for PacketEvents errors first, then see
      the known-packs/registry-injection notes above before assuming it's a config problem.
      `/toggleseasoncolors` off, reconnect — should render fully vanilla again. Also test with a
      Bedrock/Floodgate client if you have one available — it should never receive recolored
      chunks (`skip-bedrock-clients: true`).

### 7. Events

- [ ] `/solstice setdate 25/12/1` in a world where Christmas is enabled — `/season`'s active-events
      line should list Christmas, every online player should receive gift items per
      `events.yml`'s `gift-loot` list, and any configured `start-commands` should run. Advance the
      date past 28/12 — Christmas should drop off the active-events line and `stop-commands`
      should fire.
- [ ] `/solstice setdate 31/10/1`, force/observe a hostile mob spawn — it should have Invisibility
      and Speed; spawn/observe a witch specifically — Blindness and Wither instead.
- [ ] Add a test entry to each of `custom-events.yml`'s `dated`, `weekly`, and `daily` lists (e.g.
      a chat broadcast with `%season%`/`%day%` placeholders), `/solstice reload`, then
      `/solstice setdate` to trigger the dated one, wait for/force a day change for the daily one,
      and set the date to match the configured weekday for the weekly one. Confirm the broadcast
      text has placeholders correctly substituted and that a `/`-prefixed action runs as a console
      command instead of being chatted.
- [ ] Configure a dated event on a day number that doesn't exist in some month (e.g. day 31), point
      the date at that month, and confirm it fires on the month's last valid day instead of never
      firing or throwing an error.

### 8. Commands & permissions

- [ ] As a non-op player without any Solstice permissions granted, confirm every command in
      `docs/FEATURES.md`'s permission table is denied with the "no permission" message, then grant
      each permission node individually (via LuckPerms or similar) and confirm the corresponding
      command starts working.
- [ ] `/toggletemperature SomePlayer` as a non-admin should fail (missing
      `solstice.toggletemperature.others`); as an admin it should toggle the *other* player's
      display, not your own.
- [ ] `/solstice reload`, immediately followed by `/season` — should not error or reset the current
      date/season (config reload must not clobber in-memory world state).
- [ ] `/solstice install anything` — should reply with the "not implemented" message, not silently
      do nothing or throw an error.
- [ ] `/solstice restoreworld` after letting winter place some snow/ice — should immediately clear
      all of it, independent of the current season.
- [ ] `/solstice disable` on a test world, then confirm `/season` in that world reports it as
      unmanaged and none of the world-effect/temperature systems still run there; add the world to
      `config.yml`'s `disabled-worlds` and reload/restart to confirm it stays disabled.

### 9. PlaceholderAPI

- [ ] With PlaceholderAPI installed, `/papi parse <player> %solstice_season%` (and a handful of
      others from the table in `docs/FEATURES.md`) return sensible values, not `%solstice_...%`
      literal text (which means the expansion failed to register) or blank.
- [ ] Test the world-scoped form, e.g. `%solstice_season_world%` for a non-default world name,
      confirms it resolves against that world regardless of the requesting player's location.
- [ ] `%solstice_temperature%` vs `%solstice_temperature_int_fahr%` — confirm the unit conversion
      matches what `/togglefahrenheit` shows in-game for the same player at the same moment.

### 10. Multi-region stress pass

- [ ] With two+ players in far-apart chunks (different regions), run the season/temperature/world-
      effects checks above simultaneously in both locations. Nothing should error, and effects in
      one region (snow forming, particles, temperature) should be entirely independent of the
      other region's state.
- [ ] Force rapid chunk loading in both regions at once (e.g. both players flying/elytra across
      terrain) while winter snow/ice or spring/summer flora generation is active — watch the server
      log and console for any `IllegalStateException`/thread-ownership errors, which would indicate
      a Folia region-affinity bug slipped through.
- [ ] Have both players cross a season boundary via `/solstice setdate` at the same time and
      confirm both regions' world-effect systems (snow, crops, mobs) pick up the new season
      correctly without needing a relog.
