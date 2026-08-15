package io.github.kenvandine.folianexastats;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaytimeSplitterTest {

    @Test
    void sameDaySegmentIsNotSplit() {
        Instant from = Instant.parse("2026-08-15T10:00:00Z");
        Instant to = Instant.parse("2026-08-15T10:01:00Z");

        Map<String, Long> result = PlaytimeSplitter.splitByUtcDay(from, to);

        assertEquals(1, result.size());
        assertEquals(60L, result.get("2026-08-15"));
    }

    @Test
    void segmentStraddlingMidnightSplitsAcrossBothDays() {
        Instant from = Instant.parse("2026-08-15T23:59:30Z");
        Instant to = Instant.parse("2026-08-16T00:00:30Z");

        Map<String, Long> result = PlaytimeSplitter.splitByUtcDay(from, to);

        assertEquals(2, result.size());
        assertEquals(30L, result.get("2026-08-15"));
        assertEquals(30L, result.get("2026-08-16"));
    }

    @Test
    void segmentSpanningMultipleFullDaysAttributesEachDayCorrectly() {
        Instant from = Instant.parse("2026-08-15T12:00:00Z");
        Instant to = Instant.parse("2026-08-18T06:00:00Z");

        Map<String, Long> result = PlaytimeSplitter.splitByUtcDay(from, to);

        assertEquals(4, result.size());
        assertEquals(12 * 3600L, result.get("2026-08-15"));
        assertEquals(24 * 3600L, result.get("2026-08-16"));
        assertEquals(24 * 3600L, result.get("2026-08-17"));
        assertEquals(6 * 3600L, result.get("2026-08-18"));
        long total = result.values().stream().mapToLong(Long::longValue).sum();
        assertEquals(to.getEpochSecond() - from.getEpochSecond(), total);
    }

    @Test
    void zeroOrNegativeDurationProducesNoEntries() {
        Instant t = Instant.parse("2026-08-15T10:00:00Z");
        assertTrue(PlaytimeSplitter.splitByUtcDay(t, t).isEmpty());
        assertTrue(PlaytimeSplitter.splitByUtcDay(t, t.minusSeconds(5)).isEmpty());
    }
}
