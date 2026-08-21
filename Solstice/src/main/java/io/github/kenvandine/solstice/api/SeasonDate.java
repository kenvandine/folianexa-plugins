package io.github.kenvandine.solstice.api;

/**
 * A date on Solstice's configurable calendar. {@code dayOfYear} and {@code weekdayIndex} are
 * precomputed by the calendar engine (which knows the configured month lengths and weekday count)
 * rather than derived here, so this type stays free of any calendar-config dependency.
 *
 * @param year          calendar year, starting at 1 on world creation unless configured otherwise
 * @param month         1-based month number
 * @param day           1-based day of month
 * @param dayOfYear     1-based day within the year
 * @param weekdayIndex  0-based index into the configured weekday-name list
 * @param totalDays     days elapsed since epoch (year 1, day 1) — used for ordering and deltas
 */
public record SeasonDate(int year, int month, int day, int dayOfYear, int weekdayIndex, long totalDays) {

    public boolean isSameDay(SeasonDate other) {
        return totalDays == other.totalDays;
    }

    public long daysUntil(SeasonDate other) {
        return other.totalDays - totalDays;
    }
}
