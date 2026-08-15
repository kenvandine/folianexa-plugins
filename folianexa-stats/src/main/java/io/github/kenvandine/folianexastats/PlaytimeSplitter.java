package io.github.kenvandine.folianexastats;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Splits an elapsed playtime interval across UTC calendar-day boundaries.
 *
 * <p>The report interval (default 60s, config.yml) is usually well under a
 * day, but a segment CAN straddle UTC midnight (e.g. last flush at
 * 23:59:30, this flush at 00:00:30) — mgmt's {@code playtime_daily} is
 * keyed by UTC date and expects each report to carry only the seconds
 * that actually happened on that date (see
 * mgmt/src/folia_mgmt/routers/stats.py in the FoliaNexa repo), so
 * attributing a whole segment to a single day would silently misreport
 * the heatmap around midnight.
 */
final class PlaytimeSplitter {

    private PlaytimeSplitter() {
    }

    /** Returns UTC date (yyyy-MM-dd) -> seconds elapsed on that date, for {@code [from, to)}. */
    static Map<String, Long> splitByUtcDay(Instant from, Instant to) {
        Map<String, Long> result = new LinkedHashMap<>();
        if (!to.isAfter(from)) {
            return result;
        }

        Instant cursor = from;
        while (cursor.isBefore(to)) {
            LocalDate day = cursor.atZone(ZoneOffset.UTC).toLocalDate();
            Instant nextMidnight = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant segmentEnd = nextMidnight.isBefore(to) ? nextMidnight : to;
            long seconds = segmentEnd.getEpochSecond() - cursor.getEpochSecond();
            if (seconds > 0) {
                result.merge(day.toString(), seconds, Long::sum);
            }
            cursor = segmentEnd;
        }
        return result;
    }
}
