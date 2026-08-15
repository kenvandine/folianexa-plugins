# FoliaNexaStats

Reports per-player kills, deaths, blocks mined, and playtime to
[`folia-nexa-mgmt`](https://github.com/kenvandine/FoliaNexa)'s public
player hub (`portal/`, PLAN.md §7A) — the piece that turns
`GET /api/v1/public/leaderboards` from an empty response into real data.

Softdepends on two other plugins, reading their real APIs when present
(and simply omitting the corresponding stat when they're not):

- **AuraSkills** — reports `auraskills_power_level` via
  `SkillsUser#getPowerLevel()`.
- **Vault** — reports `axauctions_wealth` (gated on **AxAuctions**
  specifically being installed, not Vault alone). AxAuctions is a
  closed-source premium plugin with no public API to compile against or
  verify; like the rest of the Bukkit auction-house ecosystem, it
  transacts through whichever Vault-registered economy plugin is present
  rather than implementing its own — so that's what this actually reads,
  reported under the `axauctions_wealth` key because that's the stat a
  world running AxAuctions cares about surfacing.

This is an original, from-scratch plugin — not a port of anything.

## Building

Requires Java 21.

```bash
./gradlew build
# build/libs/folianexa-stats-0.1.0.jar
```

## Installing (for local testing)

Drop the built jar into a Folia/Paper 1.21.4 server's `plugins/`
directory and (re)start the server. See
[the `FoliaNexa` plugin-dev environment setup guide](https://github.com/kenvandine/FoliaNexa/blob/main/docs/plugin-dev/01-environment-setup.md)
for running a local test server. Set `mgmt-base-url` and
`mgmt-api-token` in `config.yml` (generated on first run) before
expecting any reports to actually land — see
[`docs/vps-edge-deployment.md`](https://github.com/kenvandine/FoliaNexa/blob/main/docs/vps-edge-deployment.md)
in the `FoliaNexa` repo for how the mgmt-side API token is issued
(same pattern as the proxy/bot service accounts, CLAUDE.md Phases 7–8).

## Configuration (`config.yml`)

| Key | Default | What |
| --- | --- | --- |
| `mgmt-base-url` | `http://mgmt.internal:8443` | mgmt's own address, reachable from wherever this world's container runs. |
| `mgmt-api-token` | *(empty)* | Operator-role API token — required, reports 401 without one. |
| `report-interval-seconds` | `60` | How often all currently-known players' stats are batched and posted. |

## Usage

- `/foliaNexaStats reload` — reloads `config.yml` without a restart.
- `/foliaNexaStats report` — triggers an immediate report instead of
  waiting for the next interval (useful when testing).

## Design notes

- **Why a player's reported "total" doesn't reset to zero on server
  restart:** mgmt mirrors whatever value this plugin reports as a
  player's current absolute total — it doesn't sum deltas server-side.
  So the first time this plugin sees a player in a given process
  lifetime, it fetches their existing totals from mgmt's own public API
  (`GET /api/v1/public/players/{uuid}`) as a baseline before reporting
  anything for them — see `StatsTracker`'s class docs for the full
  reasoning. A player is only included in a report cycle once that
  baseline has actually loaded.
- **Folia scheduling:** the periodic report cycle runs each online
  player's playtime-flush and AuraSkills/Vault reads on *that player's
  own region thread* (`Bukkit.getRegionScheduler()`) — these touch other
  plugins' live state, which this codebase's Folia-safety conventions
  treat the same as touching game state directly. Only the actual HTTP
  POST happens on `Bukkit.getAsyncScheduler()`, after every online
  player's region-thread read has completed (a simple counter-based
  fan-in — see `FoliaNexaStatsPlugin#runReportCycle`).
- **JSON handling:** hand-rolled (`MiniJson`), not a bundled/shaded
  library — the shapes involved are small and well-known
  (`mgmt/src/folia_mgmt/routers/stats.py` / `public_stats.py` in the
  `FoliaNexa` repo), and this avoids any risk of a classpath conflict
  with another plugin's JSON library on the same server.

## What's real vs. unverified

Built and tested with real Gradle (JDK 21, `com.gradleup.shadow`
8.3.5) — `./gradlew build` succeeds, producing a real shadow jar.
24 tests, all passing, all real (no mocking library): `MiniJsonTest`
and `PlaytimeSplitterTest` exercise the pure-Java pieces directly;
`StatsTrackerTest` covers the baseline-seeding/never-regress logic and
midnight-crossing playtime accounting; `HttpMgmtClientTest` runs
against a real local JDK `HttpServer`, not a mock, confirming the exact
request/response shapes this plugin sends and expects.

The AuraSkills and Vault integrations are built against those
projects' real, verified APIs (AuraSkills API Bukkit's javadocs,
confirmed at
https://aurelium.dev/javadocs/auraskills-api-bukkit/dev/aurelium/auraskills/api/user/SkillsUser.html;
Vault's real `Economy` interface, confirmed against
https://github.com/MilkBowl/VaultAPI) — but **not** exercised against
the real plugins running on a live server, since none was available in
this environment. Not verified at all: this plugin actually loading in
a live Folia server, actually connecting to a real `folia-nexa-mgmt`
instance end-to-end, or AxAuctions' real behavior (closed-source, no
public API — see the design-notes section above for what's actually
being assumed there). If you're picking this up to run it for real,
those are the things to check first.

## License

MIT
