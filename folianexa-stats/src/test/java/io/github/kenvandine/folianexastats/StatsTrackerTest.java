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
    void newPlayerNeedsBaselineUntilApplied() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());

        assertTrue(tracker.needsBaseline(UUID_A));
        tracker.applyBaseline(UUID_A, 10, 2, 500, 3600);
        assertFalse(tracker.needsBaseline(UUID_A));
    }

    @Test
    void reportsExcludePlayersWithoutALoadedBaseline() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordKill(UUID_A);

        assertTrue(tracker.drainReports().isEmpty());

        tracker.applyBaseline(UUID_A, 0, 0, 0, 0);
        List<StatsTracker.PlayerReport> reports = tracker.drainReports();
        assertEquals(1, reports.size());
        assertEquals(1.0, reports.get(0).stats().get("kills"));
    }

    @Test
    void countsAccumulatedBeforeBaselineLoadsAreNotLost() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.recordKill(UUID_A);
        tracker.recordKill(UUID_A);
        tracker.recordDeath(UUID_A);
        tracker.recordBlockMined(UUID_A);

        // Baseline resolves later (as it would after the real async
        // mgmt fetch completes), seeded from what mgmt already had on file.
        tracker.applyBaseline(UUID_A, /*kills*/ 100, /*deaths*/ 20, /*blocksMined*/ 5000, /*playtime*/ 7200);

        Map<String, Double> stats = tracker.drainReports().get(0).stats();
        assertEquals(102.0, stats.get("kills"));
        assertEquals(21.0, stats.get("deaths"));
        assertEquals(5001.0, stats.get("blocks_mined"));
    }

    @Test
    void applyBaselineNeverOverwritesAfterFirstApplication() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.applyBaseline(UUID_A, 10, 0, 0, 0);
        tracker.recordKill(UUID_A); // local progress after baseline loaded
        tracker.applyBaseline(UUID_A, 999, 999, 999, 999); // a stray/duplicate resolution — must be ignored

        Map<String, Double> stats = tracker.drainReports().get(0).stats();
        assertEquals(11.0, stats.get("kills")); // 10 (real baseline) + 1 local, not 999 + 1
    }

    @Test
    void flushPlaytimeCrossingMidnightSplitsIntoBothDaysDailyDelta() {
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.parse("2026-08-15T23:59:00Z");
        tracker.onJoin(UUID_A, "Steve", t0);
        tracker.applyBaseline(UUID_A, 0, 0, 0, 1000);

        tracker.flushPlaytime(UUID_A, t0.plusSeconds(90)); // crosses midnight
        StatsTracker.PlayerReport report = tracker.drainReports().get(0);

        assertEquals(1090.0, report.stats().get("playtime_seconds_total"));
        assertEquals(60L, report.playtimeDaily().get("2026-08-15"));
        assertEquals(30L, report.playtimeDaily().get("2026-08-16"));
    }

    @Test
    void playtimeDailyIsClearedAfterDrainButCumulativeTotalPersists() {
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.parse("2026-08-15T10:00:00Z");
        tracker.onJoin(UUID_A, "Steve", t0);
        tracker.applyBaseline(UUID_A, 0, 0, 0, 0);
        tracker.flushPlaytime(UUID_A, t0.plusSeconds(60));

        StatsTracker.PlayerReport first = tracker.drainReports().get(0);
        assertEquals(60L, first.playtimeDaily().get("2026-08-15"));
        assertEquals(60.0, first.stats().get("playtime_seconds_total"));

        // No new playtime flushed since — the next report has an empty
        // daily delta but the same cumulative total.
        StatsTracker.PlayerReport second = tracker.drainReports().get(0);
        assertTrue(second.playtimeDaily().isEmpty());
        assertEquals(60.0, second.stats().get("playtime_seconds_total"));
    }

    @Test
    void onQuitFlushesRemainingPlaytime() {
        StatsTracker tracker = new StatsTracker();
        Instant t0 = Instant.parse("2026-08-15T10:00:00Z");
        tracker.onJoin(UUID_A, "Steve", t0);
        tracker.applyBaseline(UUID_A, 0, 0, 0, 0);

        tracker.onQuit(UUID_A, t0.plusSeconds(30));

        Map<String, Double> stats = tracker.drainReports().get(0).stats();
        assertEquals(30.0, stats.get("playtime_seconds_total"));
    }

    @Test
    void extraStatsFromSoftIntegrationsAreIncludedWhenPresent() {
        StatsTracker tracker = new StatsTracker();
        tracker.onJoin(UUID_A, "Steve", Instant.now());
        tracker.applyBaseline(UUID_A, 0, 0, 0, 0);

        tracker.recordExtraStats(UUID_A, Map.of("auraskills_power_level", 42.0));

        Map<String, Double> stats = tracker.drainReports().get(0).stats();
        assertEquals(42.0, stats.get("auraskills_power_level"));
        assertFalse(stats.containsKey("axauctions_wealth"));
    }
}
