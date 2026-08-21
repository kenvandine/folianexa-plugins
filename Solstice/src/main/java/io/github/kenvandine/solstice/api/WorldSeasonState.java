package io.github.kenvandine.solstice.api;

/**
 * Immutable per-world snapshot. Held behind an {@code AtomicReference} so any thread can read the
 * current season lock-free; only the global region scheduler (via SeasonManager/CalendarEngine)
 * ever swaps the reference. See PLAN.md §2 — this is the concurrency backbone the packet-based
 * biome recoloring depends on.
 *
 * @param date            the current calendar date
 * @param season          the season the date falls in
 * @param subSeason       which blend phase of the season we're in
 * @param seasonProgress  0.0 (season start) to 1.0 (season end, exclusive)
 * @param dayLengthTicks  configured day length for the current month, in ticks
 * @param nightLengthTicks configured night length for the current month, in ticks
 * @param ticksIntoDayNight progress (in ticks) into the current day+night cycle; wraps at
 *                          dayLengthTicks + nightLengthTicks, at which point the date advances
 * @param timePaused      whether time advancement is currently paused (admin /pausetime)
 */
public record WorldSeasonState(
        SeasonDate date,
        Season season,
        SubSeason subSeason,
        double seasonProgress,
        long dayLengthTicks,
        long nightLengthTicks,
        long ticksIntoDayNight,
        boolean timePaused
) {
    public static WorldSeasonState initial(SeasonDate date, Season season, long dayLengthTicks, long nightLengthTicks) {
        return new WorldSeasonState(date, season, SubSeason.SUB_1, 0.0, dayLengthTicks, nightLengthTicks, 0L, false);
    }

    public Season nextSeason() {
        return season.next();
    }

    public long cycleLengthTicks() {
        return dayLengthTicks + nightLengthTicks;
    }

    public boolean isDaytime() {
        return ticksIntoDayNight < dayLengthTicks;
    }

    public WorldSeasonState withDate(SeasonDate newDate) {
        return new WorldSeasonState(newDate, season, subSeason, seasonProgress, dayLengthTicks, nightLengthTicks, ticksIntoDayNight, timePaused);
    }

    public WorldSeasonState withSeason(Season newSeason, double newProgress, SubSeason newSubSeason) {
        return new WorldSeasonState(date, newSeason, newSubSeason, newProgress, dayLengthTicks, nightLengthTicks, ticksIntoDayNight, timePaused);
    }

    public WorldSeasonState withDayNightLength(long newDayLengthTicks, long newNightLengthTicks) {
        return new WorldSeasonState(date, season, subSeason, seasonProgress, newDayLengthTicks, newNightLengthTicks, ticksIntoDayNight, timePaused);
    }

    public WorldSeasonState withTicksIntoDayNight(long newTicksIntoDayNight) {
        return new WorldSeasonState(date, season, subSeason, seasonProgress, dayLengthTicks, nightLengthTicks, newTicksIntoDayNight, timePaused);
    }

    public WorldSeasonState withPaused(boolean paused) {
        return new WorldSeasonState(date, season, subSeason, seasonProgress, dayLengthTicks, nightLengthTicks, ticksIntoDayNight, paused);
    }
}
