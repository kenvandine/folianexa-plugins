package io.github.kenvandine.solstice.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;

/** Parsed custom-events.yml — dated / weekly / daily custom events (PLAN.md §3.6). */
public final class CustomEventsConfig {

    public record DatedEvent(String name, String date, List<String> actions) {
    }

    public record WeeklyEvent(String name, String weekday, List<String> actions) {
    }

    public record DailyEvent(String name, List<String> actions) {
    }

    private final List<DatedEvent> dated;
    private final List<WeeklyEvent> weekly;
    private final List<DailyEvent> daily;

    private CustomEventsConfig(List<DatedEvent> dated, List<WeeklyEvent> weekly, List<DailyEvent> daily) {
        this.dated = dated;
        this.weekly = weekly;
        this.daily = daily;
    }

    public static CustomEventsConfig load(FileConfiguration yaml) {
        List<DatedEvent> dated = new ArrayList<>();
        List<?> rawDated = yaml.getList("dated");
        if (rawDated != null) {
            for (Object raw : rawDated) {
                if (raw instanceof ConfigurationSection s) {
                    dated.add(new DatedEvent(s.getString("name", "event"), s.getString("date", ""), s.getStringList("actions")));
                }
            }
        }

        List<WeeklyEvent> weekly = new ArrayList<>();
        List<?> rawWeekly = yaml.getList("weekly");
        if (rawWeekly != null) {
            for (Object raw : rawWeekly) {
                if (raw instanceof ConfigurationSection s) {
                    weekly.add(new WeeklyEvent(s.getString("name", "event"), s.getString("weekday", "Sunday"), s.getStringList("actions")));
                }
            }
        }

        List<DailyEvent> daily = new ArrayList<>();
        List<?> rawDaily = yaml.getList("daily");
        if (rawDaily != null) {
            for (Object raw : rawDaily) {
                if (raw instanceof ConfigurationSection s) {
                    daily.add(new DailyEvent(s.getString("name", "event"), s.getStringList("actions")));
                }
            }
        }

        return new CustomEventsConfig(dated, weekly, daily);
    }

    public List<DatedEvent> dated() {
        return dated;
    }

    public List<WeeklyEvent> weekly() {
        return weekly;
    }

    public List<DailyEvent> daily() {
        return daily;
    }
}
