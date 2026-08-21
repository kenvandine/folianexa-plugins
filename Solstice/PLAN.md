# Solstice — a Folia-native seasons plugin

**Status:** planning complete, no code written yet.
**Working dir:** `/home/ken/src/github/kenvandine/folianexa-plugins/Solstice/`

---

## 0. Scope and ground rules (read first)

**Do not decompile it. Do not port, translate, or reimplement from its bytecode or any decompiled
source.** That produces a derivative of proprietary code and substitutes for a product that is actively
sold. This holds even for a license holder who only wants Folia support.

**What this project is instead:** a new seasons plugin written from scratch. Seasons, temperature, and
calendar systems are game mechanics, not protected expression, and the feature set below was derived
entirely from the **public documentation wiki** (<https://wiki.realisticseasons.com/>, which publishes an
`llms.txt` index of all 54 pages). Reading public docs to decide *what* to build is fine. Reading the
jar to decide *how* is not.

Practical rules for whoever continues this:
- Never run a decompiler on the jar, and never open decompiled output.
- Inspecting `plugin.yml` / file listings for feature scope is fine; that is published interface metadata.
- Do not copy wiki prose verbatim into config comments or the README. Use the numbers (they are
  functional facts) but write your own wording.
- Ship under a name that cannot be confused with the original. Chosen name: **Solstice**.

**User decisions already made** (asked and answered):
- Scope: **full suite** — core seasons + calendar + temperature + visuals (biome recoloring, snow,
  particles) + crop growth.
- Target: **Folia only, latest API.** No Paper fallback, no NMS, no version-bridge modules.

---

## 1. Environment (already verified)

| Thing | State |
|---|---|
| Java | OpenJDK **25.0.3** at `/usr/lib/jvm/java-25-openjdk-amd64` |
| Maven | **not installed system-wide**; 3.9.16 unpacked in the session scratchpad |
| Gradle | not installed |
| Network | works (reaches repo.papermc.io) |
| Git | this directory is **not** a git repo |

Maven 3.9.16 was downloaded to the scratchpad and confirmed running on Java 25.
`https://dlcdn.apache.org/maven/maven-3/3.9.11/...` **404s** — only 3.9.16 is on the CDN mirror. If the
scratchpad is gone, re-fetch:

```
curl -sL https://dlcdn.apache.org/maven/maven-3/3.9.16/binaries/apache-maven-3.9.16-bin.tar.gz -o mvn.tgz
tar xzf mvn.tgz && ./apache-maven-3.9.16/bin/mvn -v
```

Consider committing a Maven wrapper (`mvnw`) into the project so the build is self-contained.

### Folia API versions (confirmed live from repo.papermc.io)

Minecraft has moved to year-based versioning. `dev.folia:folia-api`:

- latest stable: **`26.1.2.build.8-stable`**  ← target this
- latest beta: `26.2.build.5-beta`
- older scheme still published: `1.21.11-R0.1-SNAPSHOT` and earlier

Repo: `https://repo.papermc.io/repository/maven-public/`. The POM pulls adventure-bom 4.26.1, guava
33.5.0-jre, gson 2.13.2, snakeyaml 2.2, joml 1.10.8, fastutil 8.5.18.

**PacketEvents: `com.github.retrooper:packetevents-spigot:2.13.0`** (repo
`https://repo.codemc.io/repository/maven-releases/`), published 2026-06-22. Verified — see §6.

**Still unverified:** latest `me.clip:placeholderapi` version (repo
`https://repo.extendedclip.com/content/repositories/placeholderapi/`).

---

## 2. The Folia problem, stated plainly

Folia shards each world into independently-ticked **regions**, each on its own thread. This is not a
"add some locks" port — it changes what code is even allowed to run where.

Hard rules:

- **`Bukkit.getScheduler()` (BukkitScheduler) is unsupported and throws.** Nothing in this project may
  touch it. This is the single biggest reason a from-scratch build beats a port.
- You may only read or mutate a block, chunk, or entity **from the thread that owns its region**.
- World **time and weather are owned by the global region**, not by any location region. All
  `setTime` / `setFullTime` / `setStorm` / `setThundering` must run on the global region scheduler.
- Events fire on the region thread owning the relevant location. `PlayerJoinEvent` fires on the global
  region thread.
- `plugin.yml` must declare `folia-supported: true` or the server refuses to load the plugin.

The four schedulers:

| Scheduler | Use for |
|---|---|
| `Bukkit.getGlobalRegionScheduler()` | world time, weather, calendar advancement, global state |
| `Bukkit.getRegionScheduler()` | anything tied to a `Location` / chunk — block edits, snow, flora |
| `entity.getScheduler()` | anything tied to a player or mob; follows the entity across regions |
| `Bukkit.getAsyncScheduler()` | disk I/O, config parsing, persistence |

`EntityScheduler` callbacks take a `retired` runnable that fires if the entity despawns before the task
runs — always pass a real handler, never `null`-and-forget.

Useful guards: `Bukkit.isOwnedByCurrentRegion(Location|Entity)`, `Server#isGlobalTickThread()`.

**ProtocolLib does not support Folia.** The original depends on it (`depend: [ProtocolLib]`) for
packet-level biome recoloring. That path is closed. See §6.

### Concurrency design that follows from this

- Per-world season state lives in an `AtomicReference<WorldSeasonState>` holding an **immutable record**.
  Any thread reads the current season lock-free; only the global region scheduler ever swaps it.
- Config is parsed once into an immutable snapshot object behind a `volatile` field. `/reload` builds a
  new snapshot and swaps the reference. No thread ever reads a half-written config.
- Player data in `ConcurrentHashMap<UUID, PlayerData>`, persisted on the async scheduler.
- No `synchronized`, no shared mutable collections, no `HashMap` reachable from two region threads.

---

## 3. Feature set to match (from the public wiki)

### 3.1 Seasons

Four seasons, default start dates: Spring **Mar 4**, Summer **Jun 4**, Autumn **Sep 4**, Winter **Dec 4**.

**Spring** — forest leaves pink, oak→cherry leaves (1.20+, client-side, toggleable), cherry blossom
falls, light-blue water tint, light-blue sky; flowers generate everywhere; winter snow and ice removed
progressively (block-by-block early, accelerating later); heavy passive spawns (sheep, cows, pigs,
rabbits, chickens) each with 3–5 babies; increased bee spawn rate; fireflies at night; day ≈ night.
Temperature 5–23 °C.

**Summer** — vivid jungle-like green in most biomes; cold biomes keep plains coloring; hot biomes look
dry; light-blue sky and water; shooting stars at night; fireflies at night; leaves fall from trees;
spring flowers removed; berry bush patches generate; rain very rare; jungle animals spawn everywhere;
**husks replace zombies**; exposed crops grow **2×**; day ≈ 13 min / night ≈ 7 min. Temperature 25–40 °C.

**Autumn** — trees orange/blue/green/yellow/brown; grass light-brown muddy; grey sky; brown muddy water;
falling leaf particles under trees; frequent rain and storms; large mushroom patches; bats above ground at
night; mooshrooms, frogs, foxes everywhere; more spiders and cave spiders at night; **20% of mobs spawn
wearing a pumpkin**; summer berry bushes removed; day ≈ night. Temperature 5–25 °C.

**Winter** — light-grey leaves and grass; whiter sky; dark blue water; **snows instead of rain** using the
real snow texture, removed when the season ends; all water source blocks with no block above freeze over
time, spreading inward from lake/river edges; wolves, white foxes, polar bears, snowmen spawn everywhere;
**strays replace skeletons**; 20% chance per night of a white-spark sky; **crops will not grow without a
block above them** (forces indoor farms); day ≈ 7 min / night ≈ 13 min. Temperature −10–5 °C.

### 3.2 Sub-seasons (color transition)

Colors never snap. Four blend steps across each season's progress:

| Phase | Progress | Blend |
|---|---|---|
| Sub-season 1 | 0–9% | 45% previous / 55% current |
| Sub-season 2 | 9–18% | 25% previous / 75% current |
| Full season | 18–84% | 100% current |
| Sub-season 3 | 84–92% | 75% current / 25% next |
| Sub-season 4 | 92–100% | 55% current / 45% next |

### 3.3 Calendar

- Real-world structure: 12 months, 365 days/year, real month lengths — but counted in **Minecraft days**.
  At vanilla 20-min days a year is ≈ 5 real days.
- Days per month, month names, and weekday names all configurable.
- Season is **always derived from the date**. You cannot hold a season out of sync with its date unless
  you reconfigure the season start dates.
- 24-hour in-game clock that accounts for **variable day/night length** — this is why the plugin's time
  differs from other time plugins.
- Per-month day and night lengths, independently configurable, no requirement that they sum to 20 min:

| Month | Day | Night |
|---|---|---|
| Jan | 7 | 13 |
| Feb, Dec | 8 | 12 |
| Mar, Nov | 9 | 11 |
| Apr, Oct | 10 | 10 |
| May, Sep | 11 | 9 |
| Jun, Aug | 12 | 8 |
| Jul | 13 | 7 |

Implementation note: implementing variable day length means driving world time yourself on the global
region scheduler rather than letting `doDaylightCycle` run. Disable the gamerule per managed world and
advance `setFullTime` at a computed rate; the rate changes at dawn/dusk boundaries.

### 3.4 Temperature

Recalculated every **2 s** by default. Celsius default, Fahrenheit toggleable per player. Shown on the
action bar above the hunger bar, colored by severity; position and colors configurable; PlaceholderAPI
alternative. Can be disabled server-wide, and per-dimension for Nether/End.

**Base by season:** Winter −12…0 · Spring 0…20 · Summer 20…40 · Autumn 5…20 (°C)

**Weather:** clear 0 · rain/snow −4 · thunder −5

**Time of day:** gradual, −5 at night (ticks 14800–23500) up to +3 at midday (ticks 6000–12000)

**Biome:** badlands/desert +15 · jungle +12 · savanna +10 · temperate (beach, forest, plains, ocean…) 0 ·
cold (mountains, taiga) −4 · frozen −12

**Altitude:** above y=64, −0.08 °C per block (except in winter); below y=64 **in winter**, +0.2 °C per block

**Water / powdered snow:** summer & winter −10 · spring & autumn −4. Persists after exit, decaying 1 °C
every 2 s.

**Sprinting:** up to +4

**Nearby blocks (16-block radius):** lava +22 · fire +16 · campfire +15 · torch/lantern +7 ·
soul fire −16 · blue ice −15 · soul campfire −10 · soul torch/lantern −7 · ice/packed ice −6

**Armor:** leather +5/piece (+20 full set, capped at 25) · iron/gold/diamond +1.25/piece (+5 full) ·
netherite +0.75/piece (+3 full)

**Food:** full hunger below 25 °C → +5 · water bottle above 25 °C → −10 for 5 min

**Effects (all thresholds configurable):**

| Condition | Effect |
|---|---|
| < −20 °C | freezing + ½ heart damage every 2 s |
| < −15 °C | Slowness |
| < −10 °C | Hunger |
| air ≤ 0 °C | visible cold breath particles |
| 15–30 °C | off by default; any configured potion effect (e.g. Resistance) |
| > 40 °C | sweating particles |
| > 50 °C | healing disabled |
| > 60 °C | Slowness |
| > 65 °C | burning + screen damage indicator |

Warnings go to action bar and chat.

**Custom temperature items:** per-material, optionally narrowed by **CustomModelData**, applying a
modifier when held or worn. Enables e.g. a torch that gives +10 °C in hand, and ItemsAdder-style custom
armor that is really dyed leather.

### 3.5 Particles

fireflies (spring+summer nights, near birch/oak in forests, ~3 min per group) · shooting stars (summer
nights, peaking near midnight, needs render distance ≥ 10) · falling leaves (summer days, under trees) ·
falling leaf particles (autumn, under trees) · night sparks (winter, 20% chance at nightfall, lasts the
night) · cold breath (air < 0 °C; by default you see *others'* breath, own-breath configurable) ·
sweating (own temperature > 40 °C).

### 3.6 Events

Built-in, in `events.yml`:
- **Christmas** 25–28/12 — festive particles, procedurally spawned Christmas trees, randomized gift loot
- **New Year** 1/1 — fireworks in villages, configurable spacing
- **Halloween** 31/10–2/11 — mobs get potions, invisibility, speed, armor variants; witches get blindness
  and wither
- **Easter** 20–24/4 — hidden collectible eggs worldwide, killer bunnies at night

Each supports: `enabled`, colored `name`, `display-event`, `disabled-worlds`, start/stop command lists.

Custom events in `custom-events.yml`, three kinds:
- **dated** — `dd/mm/yyyy`, `mm/dd/yyyy`, or bare day number for monthly recurrence; omit year for annual
- **weekly** — fires on a named weekday
- **daily** — fires at midnight

Actions: a line starting with `/` runs as a console command, anything else is sent to players as a
message. Placeholders in event strings: `%day%`, `%month%`, `%month_asname%`, `%year%`, `%weekday%`,
`%season%`, `%world%`, plus any PlaceholderAPI placeholder.

Loot tables use weight notation `2-8:ITEM_NAME`. If a configured date does not exist on the configured
calendar, snap to the closest valid day.

### 3.7 Commands and permissions

Player:

| Command | Permission |
|---|---|
| `/season` | `getinfo` |
| `/toggleseasoncolors` | `toggleseasons` |
| `/toggletemperature` | `toggletemperature`, `.others` |
| `/toggleseasonparticles` | `toggleparticles` |
| `/togglefahrenheit` | `togglefahrenheit` |
| `/currentbiome` | `getbiome` |

Admin (all under one admin node):

`set <spring|summer|fall|winter>` · `setdate <dd/mm/yyyy>` · `nextseason` · `install <generator>` ·
`disable` · `restoreworld` · `temperature toggle` · `temperature modify <player> <delta> <duration>` ·
`temperature clear <player>` · `pausetime` · `getinfo` · `reload` · `help`

`/season` shows current season, date, time, days until next season, and active events.

Use **your own** namespace for permissions (`solstice.*`), and `/solstice` + `/sol` as the admin root.
`/season` and the toggles are generic enough to keep.

### 3.8 PlaceholderAPI

~35 placeholders. World-scoped form `%rs_<name>_<world>%` and player-scoped `%rs_<name>%`, covering:
`season`, `next_season`, `days_until_next_season`, `day`, `weekday`, `month`, `month_asname`, `year`,
`seasonlength`, `time`, `active_events`, `next_event`, `days_until_next_event`, `biome`, and the
temperature family: `temperature`, `temperature_int`, `temperature_int_celcius`, `temperature_int_fahr`,
`temperaturecolor`, `air_temperature`, `air_temperaturecolor`, `bottle_icon`.

Use prefix **`solstice`**, not `rs`. Optionally also register `rs` as an alias behind a config flag for
people migrating their scoreboards — but that is a compatibility shim, decide deliberately.

### 3.9 Public API

Mirror this shape (own package, own names):

- `SolsticeAPI.getInstance()`
- `getSeason(World)` / `setSeason(World, Season)` (setting a season also moves the date)
- `getDate(World)` / `setDate(World, SeasonDate)`
- `getSeconds/getMinutes/getHours(World)`, `getDayOfWeek(World)`, `getCurrentMonthName(World)`
- `getTemperature(Player)`, `getAirTemperature(Location)`
- `applyTimedTemperatureEffect(Player, int delta, int seconds)`
- `applyPermanentTemperatureEffect(Player, int delta)` → handle with `.cancel()`
- seasonal biome color lookup returning fog/water/waterFog/sky/foliage/grass hex

Events to fire: `SeasonChangeEvent` (cancellable; old/new/world) · `DayChangeEvent` (from/to/world) ·
`SeasonParticleStartEvent` (cancellable; player, location, particle kind) · event start/end (start
cancellable) · chunk-refresh (cancellable).

**Folia caveat for the API:** Bukkit events must be fired on the thread owning the relevant region, and
API consumers will call these from arbitrary threads. Document the threading contract explicitly, and
make getters safe from any thread (they read the atomic snapshot) while mutators hop to the correct
scheduler.

---

## 4. Soft-dependency integrations documented by the original

PlaceholderAPI, WorldGuard, GriefPrevention, Lands, FactionsUUID, Dynmap, BlueMap, MythicMobs, ItemsAdder,
ViaVersion/ViaBackwards, Terralith, DeadlyDisasters, RoseLoot, PyroFishingPro, CustomCrops,
EconomyShopGUI, RealisticSurvival, TimePauser, TimeBar, Floodgate/GeyserMC.

Most are out of scope for a first build. The ones that actually matter early:
- **Region plugins** (WorldGuard, GriefPrevention, Lands, Factions) — needed so seasonal block changes
  (snow, ice, flora) don't vandalize claims. Implement a small `ClaimGuard` interface with a no-op default.
- **Floodgate/Geyser** — Bedrock clients can't receive the biome-recolor packets; detect and skip.
- **Terralith / custom world generators** — the original auto-detects third-party custom biomes and picks
  seasonal treatment from the biome's temperature value. Worth copying that *approach*: classify unknown
  biomes by their registry temperature rather than requiring manual config.

---

## 5. Proposed module layout

```
pom.xml
src/main/resources/
  plugin.yml           # folia-supported: true, api-version, commands, permissions
  config.yml
  calendar.yml
  temperature.yml
  events.yml
  custom-events.yml
  biomes.yml
  lang/en_US.yml
src/main/java/io/github/kenvandine/solstice/
  Solstice.java                  # bootstrap, wiring, lifecycle
  platform/
    Schedulers.java              # the Folia facade EVERYTHING routes through
    ClaimGuard.java              # region-plugin abstraction, no-op default
  config/
    ConfigManager.java           # parses to immutable snapshots, atomic swap on reload
    MainConfig.java CalendarConfig.java TemperatureConfig.java Messages.java
  api/
    SolsticeAPI.java Season.java SubSeason.java SeasonDate.java
    WorldSeasonState.java        # immutable record in the AtomicReference
    event/…
  calendar/
    CalendarEngine.java          # global-region tick; date + variable day/night length
    WorldCalendar.java MonthDef.java
  season/
    SeasonManager.java           # derives season from date, fires transitions
  temperature/
    TemperatureManager.java      # per-player loop on entity schedulers
    TemperatureModel.java ModifierSet.java TemperatureEffects.java
    PlayerTemperature.java CustomTempItems.java
  world/
    SnowManager.java             # freeze/melt, region-scheduled, block-by-block budget
    CropListener.java FloraManager.java MobSpawnListener.java
  visual/
    ParticleManager.java BiomeColorManager.java SeasonBiome.java ColorBlend.java
  events/
    EventManager.java SolsticeEvent.java LootTable.java
  command/
    SolsticeCommand.java SeasonCommand.java ToggleCommands.java
  storage/
    PlayerDataStore.java WorldDataStore.java
  integration/
    PlaceholderHook.java
```

Single Maven module — no NMS means no version-bridge submodules, which is most of why the original
carries `1_21_R3`, `26_R1`, `26_R2` packages. Shade with `maven-shade-plugin`, relocate anything bundled.

---

## 6. Biome colors — approach VERIFIED

The original registers ~70 custom biomes and **injects them into chunk packets per player**, so world
data is never modified and removing the plugin instantly reverts everything. That design is right. But it
runs on ProtocolLib, **which does not support Folia**.

**Decision: use PacketEvents 2.13.0.** This was the highest-risk unknown in the plan and has now been
checked directly against the artifacts rather than taken from docs.

### What was verified (2026-08-20, static analysis + compile test)

| Claim | Evidence |
|---|---|
| Has a real Folia scheduler layer | ships `io/github/retrooper/packetevents/util/folia/{FoliaScheduler, GlobalRegionScheduler, RegionScheduler, EntityScheduler, AsyncScheduler, TaskWrapper}` |
| Folia detection is the correct standard check | `FoliaCompatUtil` static init does `Class.forName("io.papermc.paper.threadedregions.RegionizedServer")`, sets `folia=true`, eagerly caches the Folia schedulers, and `goto`s a catch block for the non-Folia path |
| No unguarded `BukkitScheduler` use | every class referencing `org/bukkit/scheduler/BukkitScheduler` (the 5 folia/* classes, `FoliaCompatUtil`, bStats `Metrics`) **also** references `isFolia` — the Bukkit path is the fallback branch only |
| Declares Folia support as a standalone plugin | its `plugin.yml` has `folia-supported: true` (matters if users install it as a plugin instead of shading) |
| Supports our exact MC target | `ServerVersion` contains `V_26_1`, `V_26_1_1`, **`V_26_1_2`**, `V_26_2` — covers folia-api `26.1.2` and the current beta |
| Biome rewrite path exists | `WrapperPlayServerChunkData.getColumn()` → `Column.getChunks()` → `Chunk_v1_18.getBiomeData()` → `DataPalette.get(x,y,z)` / `.set(x,y,z,id)`; also `DataPalette.createForBiome()` |
| Registry injection path exists | `WrapperConfigServerRegistryData.getElements()/setElements()` with `RegistryElement(ResourceLocation, NBT)` |
| Known-packs negotiation reachable | `WrapperConfigServerSelectKnownPacks.getKnownPacks()/setKnownPacks()` |
| It all actually typechecks together | a probe plugin exercising all four Folia schedulers + registry injection + chunk biome remap **compiles cleanly** against `folia-api:26.1.2.build.8-stable` + `packetevents-spigot:2.13.0` |

The probe source is worth recreating as the first real commit — it is essentially the skeleton of
`Schedulers` + `BiomeColorManager`.

### Two things this buys us architecturally

1. **PacketEvents listeners run on Netty threads, not region threads.** This is a genuine advantage under
   Folia: all biome rewriting happens completely off the region threads, so it can never violate thread
   affinity. It does mean the color state must be readable from arbitrary threads — which the immutable
   `AtomicReference<WorldSeasonState>` snapshot design in §2 already gives us for free. Do not "fix" that
   design later; the packet layer depends on it.
2. Colors stay **client-side only**. World data is never touched, so uninstalling reverts instantly and
   `restoreworld` only ever has to undo *physical* changes (snow, ice, flora), never colors.

### Remaining risk

All of the above is **static** verification: the classes exist, the guards are right, and the code
compiles. It has **not been run on a live Folia server**. The two things most likely to bite at runtime:

- **Registry injection vs. known packs.** On 1.20.5+ the client can advertise that it already has the
  vanilla datapack, and the server then sends registry *references* instead of full entries. If injected
  biomes don't appear, strip entries from `SelectKnownPacks` to force a full registry sync.
- **Biome id remapping must stay consistent** between the registry the client received at login and the
  ids written into chunk palettes. Get this wrong and clients see scrambled biomes, not just wrong colors.

### Fallbacks, if PacketEvents disappoints at runtime

Keep `BiomeColorManager` behind an interface so these stay droppable:

2. **Datapack-defined biomes + server-side `World#setBiome`** — pure Bukkit API, but it *mutates world
   data*, needs a restore path, is expensive at scale, and must be region-scheduled per chunk.
3. **Skip tinting; physical seasonal changes only** — snow, leaf-block swaps, flora. Falls short of
   "full suite".

Also: sub-season blending means the color set is recomputed as the season progresses, so the packet
rewriter needs a cheap way to invalidate and resend chunks. Budget that work on region schedulers, and
respect the cancellable chunk-refresh event.

### Bedrock

Floodgate/Geyser clients cannot consume these packets. Detect them and skip the rewrite rather than
sending them garbage.

---

## 7. Build order

1. **Scaffold + `Schedulers`** — POM against `dev.folia:folia-api:26.1.2.build.8-stable`, `plugin.yml` with
   `folia-supported: true`, main class, scheduler facade. Get an empty plugin loading on Folia first.
2. **Calendar + season engine** — date model, immutable per-world state, variable day/night length on the
   global scheduler, season derivation, sub-seasons, `SeasonChangeEvent` / `DayChangeEvent`.
3. **Temperature** — full modifier model, effects, action-bar display, custom items, API modifiers.
4. **World effects** — snow/ice freeze and melt, crop rules, flora, seasonal mobs and replacements.
5. **Visuals** — particles first (easy, self-contained), then biome colors (hard, see §6).
6. **Events** — built-ins plus the custom event scheduler.
7. **Commands, permissions, PlaceholderAPI, public API.**
8. **Build, verify, document** — shaded jar; then a bytecode scan of the output to prove
   `org/bukkit/scheduler/BukkitScheduler` and `getScheduler()V` on `Server` appear **nowhere**:
   `unzip -p target/*.jar '*.class' | strings | grep -i bukkitscheduler` should be empty. Write a README
   covering features and the Folia design decisions.

## 8. Testing

There is no Folia server in this directory. Before claiming anything works, download a Folia server jar
from <https://papermc.io/downloads/folia>, accept the EULA, drop the plugin in, and actually boot it.
Folia bugs are overwhelmingly thread-affinity crashes that only appear at runtime — a clean compile
proves nothing. Test with multiple players in different regions, and force chunk loading far apart so
more than one region thread is live.
