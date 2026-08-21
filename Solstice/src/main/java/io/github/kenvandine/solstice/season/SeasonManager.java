package io.github.kenvandine.solstice.season;

import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.SeasonDate;
import io.github.kenvandine.solstice.api.SubSeason;
import io.github.kenvandine.solstice.config.CalendarConfig;
import io.github.kenvandine.solstice.config.MainConfig;

/**
 * Pure date-to-season derivation (PLAN.md §3.3: "season is always derived from the date"). No
 * mutable state, no threading concerns — safe to call from anywhere.
 */
public final class SeasonManager {

    private SeasonManager() {
    }

    public record Derived(Season season, double progress, SubSeason subSeason, long daysUntilNextSeason,
                           Season nextSeason, long seasonLengthDays) {
    }

    public static Derived derive(MainConfig main, CalendarConfig calendar, SeasonDate current) {
        long[] startTotalDays = new long[Season.values().length];
        for (Season season : Season.values()) {
            MainConfig.MonthDay md = main.seasonStarts().get(season);
            startTotalDays[season.ordinal()] = latestStartAtOrBefore(calendar, md, current);
        }

        Season currentSeason = Season.SPRING;
        long bestStart = Long.MIN_VALUE;
        for (Season season : Season.values()) {
            long start = startTotalDays[season.ordinal()];
            if (start > bestStart) {
                bestStart = start;
                currentSeason = season;
            }
        }

        Season nextSeason = currentSeason.next();
        MainConfig.MonthDay nextMd = main.seasonStarts().get(nextSeason);
        int nextDay = calendar.clampDay(nextMd.month(), nextMd.day());
        long nextStart = calendar.totalDaysFor(current.year(), nextMd.month(), nextDay);
        if (nextStart <= bestStart) {
            nextStart = calendar.totalDaysFor(current.year() + 1, nextMd.month(), nextDay);
        }

        long cycleLength = Math.max(1, nextStart - bestStart);
        double progress = (current.totalDays() - bestStart) / (double) cycleLength;
        progress = Math.clamp(progress, 0.0, 0.999999);
        long daysUntilNext = nextStart - current.totalDays();

        SubSeason subSeason = main.subSeasonsEnabled() ? SubSeason.forProgress(progress) : SubSeason.FULL;

        return new Derived(currentSeason, progress, subSeason, daysUntilNext, nextSeason, cycleLength);
    }

    private static long latestStartAtOrBefore(CalendarConfig calendar, MainConfig.MonthDay md, SeasonDate current) {
        int day = calendar.clampDay(md.month(), md.day());
        long candidate = calendar.totalDaysFor(current.year(), md.month(), day);
        if (candidate <= current.totalDays()) {
            return candidate;
        }
        return calendar.totalDaysFor(current.year() - 1, md.month(), day);
    }
}
