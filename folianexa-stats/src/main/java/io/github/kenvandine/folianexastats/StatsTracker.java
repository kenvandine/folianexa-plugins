package io.github.kenvandine.folianexastats;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * In-memory per-player counters. Plain Java, no {@code org.bukkit.*}
 * imports — everything here is unit-testable without a running server,
 * and this class itself never performs I/O (see {@link FoliaNexaStatsPlugin}
 * for how join/quit/report-cycle events drive it, and
 * {@link HttpMgmtClient} for the actual network calls).
 *
 * <p><b>Why "base" + "local" counters, not one running total:</b> mgmt
 * treats every reported stat value as the current absolute total (it
 * doesn't sum deltas server-side — see
 * mgmt/src/folia_mgmt/routers/stats.py in the FoliaNexa repo). If this
 * plugin only tracked counts since its own process started, every server
 * restart would report a lower total than mgmt already has on file,
 * silently regressing every leaderboard. Instead, the first time a player
 * is seen in a given process lifetime, their existing totals are fetched
 * from mgmt's public API as a "base" offset (see
 * {@link #applyBaseline}); everything counted locally afterward is added
 * on top of that base when reporting. A player is only included in a
 * report once their baseline has actually loaded — see
 * {@link #drainReports} — so a report can never go out under-counting a
 * player who just joined.
 *
 * <p>All methods are synchronized on a single coarse lock. That's fine at
 * ordinary SMP scale (this is join/quit/kill/death/block-break volume,
 * not a hot path); if profiling ever shows contention on a very busy
 * server, revisit with per-player locks.
 */
final class StatsTracker {

    static final class PlayerCounters {
        String username;
        double baseKills;
        double baseDeaths;
        double baseBlocksMined;
        double basePlaytimeSecondsTotal;
        boolean baselineLoaded;

        long localKills;
        long localDeaths;
        long localBlocksMined;
        long localPlaytimeSeconds;

        boolean sessionActive;
        Instant lastFlush;

        final Map<String, Long> pendingPlaytimeDaily = new LinkedHashMap<>();
        // Softdepend readings (auraskills_power_level, axauctions_wealth) —
        // gathered once per report cycle on the player's own region thread
        // (see FoliaNexaStatsPlugin), not accumulated like the counters
        // above. A key stays at its last known value while a player is
        // offline or the source plugin is briefly unavailable, rather than
        // disappearing from the leaderboard.
        final Map<String, Double> extraStats = new LinkedHashMap<>();
    }

    record PlayerReport(String uuid, String username, Map<String, Double> stats, Map<String, Long> playtimeDaily) {
    }

    private final Map<String, PlayerCounters> players = new HashMap<>();

    synchronized boolean needsBaseline(String uuid) {
        PlayerCounters counters = players.get(uuid);
        return counters == null || !counters.baselineLoaded;
    }

    synchronized void onJoin(String uuid, String username, Instant now) {
        PlayerCounters counters = players.computeIfAbsent(uuid, u -> new PlayerCounters());
        counters.username = username;
        counters.sessionActive = true;
        counters.lastFlush = now;
    }

    synchronized void applyBaseline(String uuid, double kills, double deaths, double blocksMined, double playtimeSecondsTotal) {
        PlayerCounters counters = players.get(uuid);
        if (counters == null || counters.baselineLoaded) {
            return; // already seeded (or the player left before this resolved) — never overwrite local progress
        }
        counters.baseKills = kills;
        counters.baseDeaths = deaths;
        counters.baseBlocksMined = blocksMined;
        counters.basePlaytimeSecondsTotal = playtimeSecondsTotal;
        counters.baselineLoaded = true;
    }

    synchronized void recordKill(String uuid) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.localKills++;
    }

    synchronized void recordDeath(String uuid) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.localDeaths++;
    }

    synchronized void recordBlockMined(String uuid) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.localBlocksMined++;
    }

    /** Adds elapsed time since the last flush (or session start) to this player's playtime, split across UTC day boundaries. */
    synchronized void flushPlaytime(String uuid, Instant now) {
        PlayerCounters counters = players.get(uuid);
        if (counters == null || !counters.sessionActive) return;
        if (!now.isAfter(counters.lastFlush)) return;

        Map<String, Long> segments = PlaytimeSplitter.splitByUtcDay(counters.lastFlush, now);
        long totalSeconds = 0;
        for (Map.Entry<String, Long> entry : segments.entrySet()) {
            counters.pendingPlaytimeDaily.merge(entry.getKey(), entry.getValue(), Long::sum);
            totalSeconds += entry.getValue();
        }
        counters.localPlaytimeSeconds += totalSeconds;
        counters.lastFlush = now;
    }

    synchronized void recordExtraStats(String uuid, Map<String, Double> extra) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.extraStats.putAll(extra);
    }

    synchronized void onQuit(String uuid, Instant now) {
        flushPlaytime(uuid, now);
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.sessionActive = false;
    }

    /**
     * Builds one report entry per baseline-loaded tracked player, and
     * clears each one's pending playtime-daily deltas (they're "since the
     * last report" — once handed to the caller to send, they're spent).
     * Kill/death/block totals are cumulative and intentionally NOT reset;
     * they're recomputed (base + local) on every call.
     */
    synchronized List<PlayerReport> drainReports() {
        List<PlayerReport> reports = new ArrayList<>();
        for (Map.Entry<String, PlayerCounters> entry : players.entrySet()) {
            PlayerCounters counters = entry.getValue();
            if (!counters.baselineLoaded) continue;

            Map<String, Double> stats = new LinkedHashMap<>();
            stats.put("kills", counters.baseKills + counters.localKills);
            stats.put("deaths", counters.baseDeaths + counters.localDeaths);
            stats.put("blocks_mined", counters.baseBlocksMined + counters.localBlocksMined);
            stats.put("playtime_seconds_total", counters.basePlaytimeSecondsTotal + counters.localPlaytimeSeconds);
            stats.putAll(counters.extraStats);

            Map<String, Long> playtimeDaily = new LinkedHashMap<>(counters.pendingPlaytimeDaily);
            counters.pendingPlaytimeDaily.clear();

            reports.add(new PlayerReport(entry.getKey(), counters.username, stats, playtimeDaily));
        }
        return reports;
    }
}
