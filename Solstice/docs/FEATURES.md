# Solstice — Feature & Command Reference

Everything Solstice does, and every command/permission/placeholder it exposes, in one place. For
build/install instructions and the list of known gaps, see the top-level `README.md`. For the
step-by-step verification procedure, see `README.md`'s "Manual test plan".

- [Seasons](#seasons)
- [Sub-seasons (color blending)](#sub-seasons-color-blending)
- [Calendar](#calendar)
- [Temperature](#temperature)
- [World effects](#world-effects)
- [Visuals](#visuals)
- [Events](#events)
- [Commands](#commands)
- [Permissions](#permissions)
- [PlaceholderAPI](#placeholderapi)
- [Public API](#public-api)
- [Config files](#config-files)

---

## Seasons

Four seasons — `SPRING`, `SUMMER`, `AUTUMN`, `WINTER` — derived purely from the current calendar
date. You cannot hold a season out of sync with its date; changing the season (via command or API)
works by moving the date to that season's configured start.

Default start dates (`config.yml` → `seasons`), day/month on Solstice's own calendar:

| Season | Default start |
|---|---|
| Spring | 4 March |
| Summer | 4 June |
| Autumn | 4 September |
| Winter | 4 December |

Each world Solstice manages gets its own independent season/date, tracked as an immutable snapshot
and persisted to `plugins/Solstice/worlddata/<world>.yml`.

## Sub-seasons (color blending)

Season progress (0–100% through the season) is split into five phases, used by the biome-color
system to blend rather than snap between seasons:

| Phase | Progress window | Blend |
|---|---|---|
| Sub-season 1 | 0–9% | 45% previous season / 55% current |
| Sub-season 2 | 9–18% | 25% previous / 75% current |
| Full season | 18–84% | 100% current |
| Sub-season 3 | 84–92% | 75% current / 25% next |
| Sub-season 4 | 92–100% | 55% current / 45% next |

The date engine computes the correct phase at all times (`/season` and the `days_until_next_season`
placeholder reflect it). **The packet-level biome recoloring currently only uses the four
full-season color sets** — see the README's documented gaps for why sub-season blending isn't
wired into the packet layer yet. Disable phase tracking entirely with `sub-seasons.enabled: false`
in `config.yml` (season display then just alternates full-color between the four seasons).

## Calendar

Configured in `calendar.yml`:

- 12 months by default, each with its own day count and its own **day length** and **night
  length** in ticks — they don't need to sum to the vanilla 24000. Solstice disables
  `doDaylightCycle` per managed world and drives `World#setTime` itself every tick, so variable
  day/night length works without any NMS.
- 7 configurable weekday names, cycling continuously from the calendar's epoch (year 1, day 1).
- A configurable calendar start date (`start.year` / `start.month` / `start.day`), used the first
  time a world is loaded; after that, elapsed days are persisted per world.

Default day/night split (in real minutes, converted to ticks at 1 real minute = 1200 ticks):

| Month(s) | Day | Night |
|---|---|---|
| January | 7 min | 13 min |
| February, December | 8 min | 12 min |
| March, November | 9 min | 11 min |
| April, October | 10 min | 10 min |
| May, September | 11 min | 9 min |
| June, August | 12 min | 8 min |
| July | 13 min | 7 min |

`/solstice pausetime` freezes date/time advancement for a world without affecting anything else.

## Temperature

Recalculated per player every `recalculate-interval-seconds` (default 2s), on that player's own
entity-scheduler thread. Configured in `temperature.yml`.

**Air temperature** (environmental, ignores the player) = season base + weather + time-of-day +
biome + altitude:

- **Season base** ramps linearly across season progress: Winter −12…0°C, Spring 0…20°C, Summer
  20…40°C, Autumn 5…20°C.
- **Weather**: clear 0, rain/snow −4, thunder −5.
- **Time of day**: a smooth curve, flat +3 across ticks 6000–12000 (midday), flat −5 across ticks
  14800–23500 (night), linearly ramping in the two gaps between.
- **Biome groups**: hot (badlands/desert) +15, jungle +12, savanna +10, cold (windswept
  hills/taiga) −4, frozen (snowy/ice biomes) −12, everything else (temperate) 0.
- **Altitude**: above y=64, −0.08°C/block (disabled in winter); below y=64 in winter only,
  +0.2°C/block.

**Apparent temperature** (what the player feels) = air temperature + these player-specific
modifiers:

- **Water/powdered snow**: −10°C (summer/winter) or −4°C (spring/autumn) while submerged, lingering
  and decaying 1°C every 2s after leaving.
- **Armor**: leather +5/piece (+20 full set, capped at +25 total); iron/gold/diamond +1.25/piece
  (+5 full set); netherite +0.75/piece (+3 full set).
- **Nearby blocks** (16-block radius): lava +22, fire +16, campfire +15, torch/lantern +7,
  soul fire −16, blue ice −15, soul campfire −10, soul torch/lantern −7, ice/packed ice −6.
- **Sprinting**: +4 while sprinting.
- **Food**: +5 if at full hunger and air temperature is below 25°C. Drinking a plain water bottle
  while apparent temperature is ≥25°C applies a −10°C timed effect for 5 minutes.
- **Custom items** (`temperature.yml` → `custom-items`): per-material, optionally narrowed by
  CustomModelData, modifier applied while held (hand) or worn (armor slot).
- **API-applied effects**: anything added via `SolsticeAPI#applyTimedTemperatureEffect` /
  `applyPermanentTemperatureEffect`, or `/solstice temperature modify`.

**Effects**, evaluated against apparent temperature (all thresholds configurable):

| Condition | Effect |
|---|---|
| ≤ −20°C | ½-heart damage every 2s (freezing) |
| ≤ −15°C or ≥ 60°C | Slowness |
| ≤ −10°C | Hunger |
| air ≤ 0°C | Cold-breath particles |
| 15–30°C | Off by default; optional configured potion effect |
| ≥ 40°C | Sweat particles |
| ≥ 50°C | Natural health regen cancelled |
| ≥ 65°C | On fire |

Display: action bar with a severity icon and colored temperature, in Celsius or Fahrenheit per
player (`/togglefahrenheit`). Chat warnings fire once per state transition, not every tick. Can be
disabled server-wide (`temperature.yml` → `enabled: false`) or per-dimension
(`disabled-dimensions: [NETHER, THE_END]`), and toggled live without a restart via
`/solstice temperature toggle`.

## World effects

- **Snow & ice** (winter only): exposed water source blocks with no block above freeze into ice,
  spreading outward from shorelines and existing ice rather than freezing every lake instantly.
  During storms, exposed solid ground gets a snow layer on top. Both revert automatically once
  winter ends. One region-scheduled task per loaded chunk, budgeted by
  `world-effects.snow-ice.blocks-per-tick-per-region` (default 8) so it can't spike a region tick.
- **Crops**: in winter, a crop only grows if it has a block directly above it (forces indoor/
  greenhouse farming); in summer, exposed crops grow with bonus growth stages applied to natural
  growth ticks (default 2x, `world-effects.crops.summer-growth-multiplier`).
- **Flora**: spring sparsely generates flowers on grass in loaded chunks; summer generates sweet
  berry bushes the same way. Both clear automatically once their season ends.
- **Mob replacements**: summer replaces natural zombie spawns with husks; winter replaces skeleton
  spawns with strays; autumn gives a configurable percentage (default 20%) of eligible hostile
  spawns a carved pumpkin helmet.

Every physical edit here checks `ClaimGuard` first (currently a permissive no-op — see the
README's documented gaps for wiring up WorldGuard).

## Visuals

- **Ambient particles** (per-player, checked every 5s): fireflies (spring/summer nights, near
  birch/oak forest), shooting stars (summer nights near midnight, requires client view distance
  ≥10), falling leaves (plain in summer daytime, colored in autumn, both "under trees"), winter
  night-sparks (20% chance per night, world-wide, lasts until dawn). Cold-breath and sweat
  particles are part of the temperature effects above, not this system.
- **Seasonal biome recoloring**: entirely client-side, over PacketEvents. Registers one extra
  biome registry entry per vanilla biome per season (fog/water/water-fog/sky/foliage/grass colors
  from `biomes.yml`, grouped by category — temperate/hot/jungle/savanna/cold/frozen), then rewrites
  the biome palette of outgoing chunk packets to point at the season-matched entry. World data is
  never touched, so removing the plugin (or a player disconnecting) instantly reverts to vanilla.
  Requires the PacketEvents plugin; skipped automatically for detected Bedrock (Floodgate) clients.
  Both are individually toggleable — see the README for the packet-pipeline's verification status.

Both particles and biome colors are globally toggleable (`config.yml` → `visuals`) and per-player
toggleable (`/toggleseasonparticles`, `/toggleseasoncolors`).

## Events

### Built-in (`events.yml`)

| Event | Default window | What fires |
|---|---|---|
| Christmas | 25–28 Dec | Start/stop commands; gift-loot (`gift-loot` list, `min-max:ITEM` notation) given to every online player in-world when the window opens |
| New Year | 1 Jan | Start/stop commands only (see README gaps re: fireworks) |
| Halloween | 31 Oct – 2 Nov | Start/stop commands; hostile mobs spawned during the window get Invisibility+Speed, witches get Blindness+Wither |
| Easter | 20–24 Apr | Start/stop commands only (see README gaps re: eggs/bunnies) |

Every built-in event supports: `enabled`, colored `name`, `display-event` (whether it shows in
`/season`'s active-events line), `disabled-worlds`, and `start-commands`/`stop-commands` (console
commands run once when the window opens/closes).

### Custom (`custom-events.yml`)

Three kinds, each a list of `{name, actions}` plus one selector field:

- **dated** — `date: dd/mm/yyyy` (one-off), `date: dd/mm` (annual), or a bare day number
  (monthly). A day that doesn't exist in the target month snaps to that month's last valid day.
- **weekly** — `weekday: <name>`, matching your configured weekday names.
- **daily** — fires every midnight, no selector.

`actions` is a list of strings: one starting with `/` runs as a console command; anything else is
broadcast to online players in that world. Placeholders available in action strings: `%day%`,
`%month%`, `%month_asname%`, `%year%`, `%weekday%`, `%season%`, `%world%`, plus any PlaceholderAPI
placeholder if that plugin is present.

## Commands

### Player commands

| Command | Permission | Description |
|---|---|---|
| `/season [world]` | `solstice.getinfo` | Season, date, time, days until next season, active events |
| `/toggleseasoncolors` | `solstice.toggleseasons` | Toggle seasonal biome recoloring for yourself |
| `/toggletemperature [player]` | `solstice.toggletemperature` (own) / `solstice.toggletemperature.others` (someone else) | Toggle the action-bar temperature display |
| `/toggleseasonparticles` | `solstice.toggleparticles` | Toggle ambient seasonal particles for yourself |
| `/togglefahrenheit` | `solstice.togglefahrenheit` | Switch your temperature display between °C and °F |
| `/currentbiome` | `solstice.getbiome` | Show the biome you're standing in |

### Admin commands — `/solstice` (alias `/sol`), all under `solstice.admin`

| Subcommand | Effect |
|---|---|
| `/solstice set <spring\|summer\|fall\|winter>` | Move sender's (or console's default) world to that season's start date |
| `/solstice setdate <dd/mm/yyyy>` | Set the world's date directly |
| `/solstice nextseason` | Advance the world to the start of the next season |
| `/solstice pausetime` | Toggle date/time advancement for the world |
| `/solstice temperature toggle` | Runtime on/off switch for temperature calculation, independent of config |
| `/solstice temperature modify <player> <delta> <seconds>` | Apply a timed temperature offset (°C) to a player |
| `/solstice temperature clear <player>` | Remove all active timed/permanent temperature modifiers from a player |
| `/solstice restoreworld` | Immediately clear every Solstice-placed snow/ice/flower/bush block in the world, regardless of season |
| `/solstice disable` | Stop ticking the world for the rest of this session (add it to `config.yml`'s `disabled-worlds` to persist) |
| `/solstice install <generator>` | Not implemented — replies with an explanatory message (see README gaps) |
| `/solstice getinfo` | Same as `/season` |
| `/solstice reload` | Reload every config file from disk |
| `/solstice help` (or no args) | Prints the subcommand list |

When run from console, world-scoped subcommands act on the first Solstice-managed world found;
when run by a player, they act on that player's current world.

## Permissions

| Node | Default | Grants |
|---|---|---|
| `solstice.*` | op | Everything below |
| `solstice.getinfo` | true | `/season` |
| `solstice.toggleseasons` | true | `/toggleseasoncolors` |
| `solstice.toggletemperature` | true | `/toggletemperature` (self) |
| `solstice.toggletemperature.others` | op | `/toggletemperature <player>` |
| `solstice.toggleparticles` | true | `/toggleseasonparticles` |
| `solstice.togglefahrenheit` | true | `/togglefahrenheit` |
| `solstice.getbiome` | true | `/currentbiome` |
| `solstice.admin` | op | All `/solstice` subcommands |

## PlaceholderAPI

Requires the PlaceholderAPI plugin. Prefix is **`solstice`** (deliberately not `rs`). Two forms:

- Player-scoped: `%solstice_<name>%` — resolves against the requesting player's current world.
- World-scoped: `%solstice_<name>_<world>%` — resolves against the named world regardless of who's
  asking, for scoreboards/signs that aren't tied to one player.

| Placeholder | Value |
|---|---|
| `season` | Current season name |
| `next_season` | Next season name |
| `days_until_next_season` | Days remaining in the current season |
| `day` / `month` / `year` | Numeric date components |
| `month_asname` | Configured month name |
| `weekday` | Configured weekday name |
| `seasonlength` | Total length of the current season, in days |
| `time` | `HH:MM`, Solstice's own clock |
| `active_events` | Comma-separated list of currently active, display-enabled built-in events |
| `next_event` | Name of the next upcoming built-in event |
| `days_until_next_event` | Days until that event starts |
| `biome` | Biome the requesting player is standing in |
| `temperature` | Apparent temperature, formatted with unit (`°C`/`°F` per player preference) |
| `temperature_int` / `temperature_int_celcius` | Apparent temperature, rounded, always °C |
| `temperature_int_fahr` | Apparent temperature, rounded, °F |
| `temperaturecolor` | `&`-color-code matching the player's current temperature severity |
| `air_temperature` | Ambient air temperature at the player's location (or a world's spawn, world-scoped) |
| `air_temperaturecolor` | Color code matching air temperature severity |
| `bottle_icon` | ❄ or ☀ depending on whether a water bottle would currently help |

## Public API

For other plugins, via `SolsticeAPI.getInstance()`:

```java
Season getSeason(World world);
void setSeason(World world, Season season);
SeasonDate getDate(World world);
void setDate(World world, SeasonDate date);
int getSeconds(World world) / getMinutes(World world) / getHours(World world);
int getDayOfWeek(World world);
String getCurrentMonthName(World world);
double getTemperature(Player player);
double getAirTemperature(Location location);
void applyTimedTemperatureEffect(Player player, double delta, int seconds);
TemperatureEffectHandle applyPermanentTemperatureEffect(Player player, double delta); // .cancel()
SeasonalBiomeColors getSeasonalColors(World world, Biome biome);
```

Fires `SeasonChangeEvent` (cancellable), `DayChangeEvent`, and `SeasonParticleStartEvent`
(cancellable). **Threading:** every getter reads a lock-free snapshot and is safe from any thread;
mutators hop internally to the correct Folia scheduler and return before the effect is guaranteed
applied. `getSeason`/`getDate`/etc throw `IllegalArgumentException` for a world Solstice doesn't
manage — check `SolsticeAPI` callers only target managed worlds.

## Config files

All under `plugins/Solstice/`, regenerated with defaults on first run if missing:

| File | Controls |
|---|---|
| `config.yml` | Managed worlds, season start dates, feature on/off switches, claim-guard provider |
| `calendar.yml` | Months, weekday names, day/night length, calendar start date |
| `temperature.yml` | The entire temperature model and its effect thresholds |
| `events.yml` | The four built-in events |
| `custom-events.yml` | Dated/weekly/daily custom events |
| `biomes.yml` | Per-category seasonal biome colors |
| `lang/en_US.yml` | All player-facing messages |
