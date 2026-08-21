package io.github.kenvandine.folianexastats;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StatsTrackerTest {

    private static final String UUID_A = "uuid-a";

    @Test
    void snapshotReportsIncludesPendingDeltasImmediately() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordKill(UUID_A);
        tracker.recordKill(UUID_A);
        tracker.recordDeath(UUID_A);
        tracker.recordBlockMined(UUID_A);

        Map<String, Double> deltas = tracker.snapshotReports().get(0).statDeltas();
        assertEquals(2.0, deltas.get("kills"));
        assertEquals(1.0, deltas.get("deaths"));
        assertEquals(1.0, deltas.get("blocks_mined"));
    }

    @Test
    void snapshotWithoutConfirmingRepeatsTheSamePendingDeltas() {
        // Simulates a report that's still in flight (or never sent) — the
        // next report cycle's own snapshot must include the same pending
        // amount, not a reset-to-zero.
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordKill(UUID_A);

        tracker.snapshotReports(); // not confirmed — e.g. the send failed
        Map<String, Double> deltas = tracker.snapshotReports().get(0).statDeltas();
        assertEquals(1.0, deltas.get("kills"));
    }

    @Test
    void confirmReportedClearsExactlyWhatWasReported() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordKill(UUID_A);
        tracker.recordKill(UUID_A);

        List<StatsTracker.PlayerReport> reports = tracker.snapshotReports();
        tracker.confirmReported(reports);

        Map<String, Double> deltas = tracker.snapshotReports().get(0).statDeltas();
        assertEquals(0.0, deltas.get("kills"));
    }

    @Test
    void activityDuringTheNetworkRoundTripIsNotLostByConfirming() {
        // The whole point of the snapshot/confirm split: a kill recorded
        // *after* snapshotReports() was called (while the HTTP POST is
        // presumably still in flight) must survive a confirmReported()
        // call against the earlier snapshot — confirming must subtract
        // the reported amount, not reset the counter to zero.
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordKill(UUID_A);

        List<StatsTracker.PlayerReport> reports = tracker.snapshotReports();
        tracker.recordKill(UUID_A); // happens after the snapshot was taken
        tracker.confirmReported(reports); // confirms only the first kill

        Map<String, Double> deltas = tracker.snapshotReports().get(0).statDeltas();
        assertEquals(1.0, deltas.get("kills"));
    }

    @Test
    void confirmingAnUntrackedOrGoneUuidIsANoOp() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        List<StatsTracker.PlayerReport> reports = tracker.snapshotReports();

        StatsTracker other = new StatsTracker(); // reports.get(0)'s uuid was never onJoin'd here
        other.confirmReported(reports); // must not throw
    }

    @Test
    void flushPlaytimeCrossingMidnightSplitsIntoBothDaysDailyDelta() {
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.parse("2026-08-15T23:59:00Z");
        tracker.onJoin(UUID_A, "Steve", t0);

        tracker.flushPlaytime(UUID_A, t0.plusSeconds(90)); // crosses midnight
        StatsTracker.PlayerReport report = tracker.snapshotReports().get(0);

        assertEquals(90.0, report.statDeltas().get("playtime_seconds_total"));
        assertEquals(60L, report.playtimeDaily().get("2026-08-15"));
        assertEquals(30L, report.playtimeDaily().get("2026-08-16"));
    }

    @Test
    void confirmReportedClearsPlaytimeDailyPerDateAndDropsZeroedEntries() {
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.parse("2026-08-15T10:00:00Z");
        tracker.onJoin(UUID_A, "Steve", t0);
        tracker.flushPlaytime(UUID_A, t0.plusSeconds(60));

        List<StatsTracker.PlayerReport> first = tracker.snapshotReports();
        assertEquals(60L, first.get(0).playtimeDaily().get("2026-08-15"));
        tracker.confirmReported(first);

        // Confirmed — the next snapshot's daily delta for that date is
        // gone entirely (not present as a zero), and no new playtime was
        // flushed since, so playtime_seconds_total is back to 0 too.
        StatsTracker.PlayerReport second = tracker.snapshotReports().get(0);
        assertTrue(second.playtimeDaily().isEmpty());
        assertEquals(0.0, second.statDeltas().get("playtime_seconds_total"));
    }

    @Test
    void onQuitFlushesRemainingPlaytime() {
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.parse("2026-08-15T10:00:00Z");
        tracker.onJoin(UUID_A, "Steve", t0);

        tracker.onQuit(UUID_A, t0.plusSeconds(30));

        Map<String, Double> deltas = tracker.snapshotReports().get(0).statDeltas();
        assertEquals(30.0, deltas.get("playtime_seconds_total"));
    }

    @Test
    void offlinePlayersAreStillReported() {
        // Never dropped from the leaderboard just for being offline — same
        // invariant the old baseline-gated design had, now unconditional
        // since there's no baseline to wait for either.
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.now();
        tracker.onJoin(UUID_A, "Steve", t0);
        tracker.onQuit(UUID_A, t0.plusSeconds(10));

        assertEquals(1, tracker.snapshotReports().size());
    }

    @Test
    void extraStatsFromSoftIntegrationsAreIncludedAsGaugesNotDeltas() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordExtraStats(UUID_A, Map.of("auraskills_power_level", 42.0));

        List<StatsTracker.PlayerReport> reports = tracker.snapshotReports();
        assertEquals(42.0, reports.get(0).gauges().get("auraskills_power_level"));
        assertFalse(reports.get(0).gauges().containsKey("axauctions_wealth"));

        // Gauges are a point-in-time snapshot, not consumed by confirming —
        // still present (at the same last-known value) on the next report.
        tracker.confirmReported(reports);
        assertEquals(42.0, tracker.snapshotReports().get(0).gauges().get("auraskills_power_level"));
    }
}
