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
 * <p><b>Deltas, not totals:</b> every counter here (kills/deaths/blocks
 * mined/playtime) is reported as "how much since the last successful
 * report", the same shape {@link #recordExtraStats extraStats} was never
 * given — mgmt sums deltas into its own running total server-side (see
 * mgmt/src/folia_mgmt/routers/stats.py in the FoliaNexa repo). This
 * replaced an earlier "base + local" design that instead fetched a
 * player's existing total from mgmt once per process lifetime and
 * reported base+local as if it were the whole truth: that broke the
 * moment a player was tracked by more than one world at once (this
 * plugin is `default_for_all_worlds: true`, so that's the common case,
 * not an edge case) — two independent per-world processes each
 * "reporting the total" just clobbered each other's number every cycle,
 * confirmed live as visibly flickering public stats. Deltas summed
 * server-side are correct regardless of how many worlds are
 * simultaneously reporting for the same player.
 *
 * <p>A delta is only removed from these counters once
 * {@link #confirmReported} is called with a report that was actually
 * delivered — see that method's docs for why a failed/unsent report must
 * leave the counters untouched rather than being cleared eagerly
 * (that's what would make a network blip actually lose progress, not
 * just report it a cycle late).
 *
 * <p>All methods are synchronized on a single coarse lock. That's fine at
 * ordinary SMP scale (this is join/quit/kill/death/block-break volume,
 * not a hot path); if profiling ever shows contention on a very busy
 * server, revisit with per-player locks.
 */
final class StatsTracker {

    static final class PlayerCounters {
        String username;

        long pendingKills;
        long pendingDeaths;
        long pendingBlocksMined;
        long pendingPlaytimeSeconds;

        boolean sessionActive;
        Instant lastFlush;

        final Map<String, Long> pendingPlaytimeDaily = new LinkedHashMap<>();
        // Softdepend readings (auraskills_power_level, axauctions_wealth) —
        // a point-in-time gauge, gathered once per report cycle on the
        // player's own region thread (see FoliaNexaStatsPlugin), not a
        // counter — mgmt overwrites rather than sums these (see
        // report_stats' gauges handling). A key stays at its last known
        // value while a player is offline or the source plugin is briefly
        // unavailable, rather than disappearing from the leaderboard.
        final Map<String, Double> gauges = new LinkedHashMap<>();
    }

    record PlayerReport(
            String uuid,
            String username,
            Map<String, Double> statDeltas,
            Map<String, Double> gauges,
            Map<String, Long> playtimeDaily) {
    }

    private final Map<String, PlayerCounters> players = new HashMap<>();

    synchronized void onJoin(String uuid, String username, Instant now) {
        PlayerCounters counters = players.computeIfAbsent(uuid, u -> new PlayerCounters());
        counters.username = username;
        counters.sessionActive = true;
        counters.lastFlush = now;
    }

    synchronized void recordKill(String uuid) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.pendingKills++;
    }

    synchronized void recordDeath(String uuid) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.pendingDeaths++;
    }

    synchronized void recordBlockMined(String uuid) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.pendingBlocksMined++;
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
        counters.pendingPlaytimeSeconds += totalSeconds;
        counters.lastFlush = now;
    }

    synchronized void recordExtraStats(String uuid, Map<String, Double> extra) {
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.gauges.putAll(extra);
    }

    synchronized void onQuit(String uuid, Instant now) {
        flushPlaytime(uuid, now);
        PlayerCounters counters = players.get(uuid);
        if (counters != null) counters.sessionActive = false;
    }

    /**
     * Builds one report entry per tracked player (online or not — a
     * player who went offline is still reported, gauges/deltas included,
     * same "never silently drop someone from the leaderboard" reasoning
     * as before) reflecting pending deltas *as of right now*. Does not
     * clear anything — see {@link #confirmReported}, which callers must
     * invoke with this exact return value only after the report actually
     * made it to mgmt. Calling this again before confirming (e.g. a slow
     * network round trip overlapping the next report-interval tick) is
     * safe and simply re-reports the same still-pending deltas, now
     * possibly larger.
     */
    synchronized List<PlayerReport> snapshotReports() {
        List<PlayerReport> reports = new ArrayList<>();
        for (Map.Entry<String, PlayerCounters> entry : players.entrySet()) {
            PlayerCounters counters = entry.getValue();

            Map<String, Double> statDeltas = new LinkedHashMap<>();
            statDeltas.put("kills", (double) counters.pendingKills);
            statDeltas.put("deaths", (double) counters.pendingDeaths);
            statDeltas.put("blocks_mined", (double) counters.pendingBlocksMined);
            statDeltas.put("playtime_seconds_total", (double) counters.pendingPlaytimeSeconds);

            reports.add(new PlayerReport(
                    entry.getKey(),
                    counters.username,
                    statDeltas,
                    new LinkedHashMap<>(counters.gauges),
                    new LinkedHashMap<>(counters.pendingPlaytimeDaily)));
        }
        return reports;
    }

    /**
     * Marks exactly the deltas in {@code reports} (a prior
     * {@link #snapshotReports} return value) as successfully delivered —
     * subtracts them from the live pending counters rather than resetting
     * to zero, so any activity that happened during the network round
     * trip (between snapshotting and this call landing) isn't lost. A
     * player who quit and was removed from tracking, or was never
     * tracked (shouldn't happen, but defensive), is skipped rather than
     * throwing — nothing left to confirm against.
     *
     * <p>Callers must only invoke this after the report was actually
     * delivered (HTTP 200) — never on a failed/timed-out send. Confirming
     * eagerly (e.g. as part of building the snapshot, before the network
     * call even happens) is exactly the bug this two-phase split exists
     * to avoid: a transient mgmt outage would otherwise permanently lose
     * that cycle's kills/deaths/blocks/playtime instead of just reporting
     * them a little late on the next successful cycle.
     */
    synchronized void confirmReported(List<PlayerReport> reports) {
        for (PlayerReport report : reports) {
            PlayerCounters counters = players.get(report.uuid());
            if (counters == null) continue;

            counters.pendingKills -= report.statDeltas().getOrDefault("kills", 0.0).longValue();
            counters.pendingDeaths -= report.statDeltas().getOrDefault("deaths", 0.0).longValue();
            counters.pendingBlocksMined -= report.statDeltas().getOrDefault("blocks_mined", 0.0).longValue();
            counters.pendingPlaytimeSeconds -= report.statDeltas().getOrDefault("playtime_seconds_total", 0.0).longValue();

            for (Map.Entry<String, Long> entry : report.playtimeDaily().entrySet()) {
                counters.pendingPlaytimeDaily.merge(entry.getKey(), -entry.getValue(), Long::sum);
                if (counters.pendingPlaytimeDaily.get(entry.getKey()) <= 0) {
                    counters.pendingPlaytimeDaily.remove(entry.getKey());
                }
            }
        }
    }
}
