package io.github.kenvandine.solstice.config;

import io.github.kenvandine.solstice.api.SeasonDate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/**
 * Parsed calendar.yml: month lengths, weekday names, and the date the calendar starts at.
 * Provides the date math (total-elapsed-days <-> year/month/day) everything else builds on.
 */
public final class CalendarConfig {

    private final List<String> weekdays;
    private final List<MonthDef> months;
    private final int startYear;
    private final int startMonth;
    private final int startDay;
    private final int daysPerYear;

    private CalendarConfig(List<String> weekdays, List<MonthDef> months, int startYear, int startMonth, int startDay) {
        this.weekdays = List.copyOf(weekdays);
        this.months = List.copyOf(months);
        this.startYear = startYear;
        this.startMonth = startMonth;
        this.startDay = startDay;
        int total = 0;
        for (MonthDef m : months) {
            total += m.days();
        }
        this.daysPerYear = total;
    }

    public static CalendarConfig load(FileConfiguration yaml) {
        List<String> weekdays = new ArrayList<>(yaml.getStringList("weekdays"));
        if (weekdays.isEmpty()) {
            weekdays = List.of("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday");
        }

        List<MonthDef> months = new ArrayList<>();
        List<?> rawMonths = yaml.getList("months");
        if (rawMonths != null) {
            for (Object raw : rawMonths) {
                if (raw instanceof ConfigurationSection section) {
                    months.add(new MonthDef(section.getString("name", "Month"),
                            section.getInt("days", 30),
                            section.getLong("day-ticks", 6000),
                            section.getLong("night-ticks", 6000)));
                } else if (raw instanceof java.util.Map<?, ?> map) {
                    Object name = map.get("name");
                    months.add(new MonthDef(name != null ? String.valueOf(name) : "Month",
                            asInt(map.get("days"), 30),
                            asLong(map.get("day-ticks"), 6000),
                            asLong(map.get("night-ticks"), 6000)));
                }
            }
        }
        if (months.isEmpty()) {
            months.add(new MonthDef("January", 30, 12000, 12000));
        }

        ConfigurationSection start = yaml.getConfigurationSection("start");
        int startYear = start != null ? start.getInt("year", 1) : 1;
        int startMonth = start != null ? start.getInt("month", 1) : 1;
        int startDay = start != null ? start.getInt("day", 1) : 1;

        return new CalendarConfig(weekdays, months, startYear, startMonth, startDay);
    }

    private static int asInt(Object o, int def) {
        return o instanceof Number n ? n.intValue() : def;
    }

    private static long asLong(Object o, long def) {
        return o instanceof Number n ? n.longValue() : def;
    }

    public List<String> weekdays() {
        return weekdays;
    }

    public List<MonthDef> months() {
        return months;
    }

    public int monthCount() {
        return months.size();
    }

    public MonthDef monthDef(int month1Based) {
        int idx = ((month1Based - 1) % months.size() + months.size()) % months.size();
        return months.get(idx);
    }

    public int daysPerYear() {
        return daysPerYear;
    }

    /** The calendar's starting date, as a fully-resolved {@link SeasonDate}. */
    public SeasonDate startDate() {
        return dateFromTotalDays(totalDaysFor(startYear, startMonth, startDay));
    }

    /** Total elapsed days since year 1, day 1 (0-based) for the given year/month/day. */
    public long totalDaysFor(int year, int month, int day) {
        long total = (long) (year - 1) * daysPerYear;
        for (int m = 1; m < month; m++) {
            total += monthDef(m).days();
        }
        total += (day - 1);
        return total;
    }

    /** Resolves a total-elapsed-days count back into a full calendar date. */
    public SeasonDate dateFromTotalDays(long totalDays) {
        long remaining = totalDays;
        int year = 1 + (int) Math.floorDiv(remaining, daysPerYear);
        remaining = Math.floorMod(remaining, daysPerYear);

        int month = 1;
        int dayOfYear = (int) remaining + 1;
        int dayRemaining = (int) remaining;
        for (MonthDef m : months) {
            if (dayRemaining < m.days()) {
                break;
            }
            dayRemaining -= m.days();
            month++;
        }
        int day = dayRemaining + 1;

        int weekdayIndex = (int) Math.floorMod(totalDays, weekdays.size());
        return new SeasonDate(year, month, day, dayOfYear, weekdayIndex, totalDays);
    }

    public SeasonDate nextDay(SeasonDate date) {
        return dateFromTotalDays(date.totalDays() + 1);
    }

    /** Clamps a configured day-of-month to a valid day for that month (PLAN.md §3.6 snap rule). */
    public int clampDay(int month, int day) {
        return Math.max(1, Math.min(day, monthDef(month).days()));
    }
}
