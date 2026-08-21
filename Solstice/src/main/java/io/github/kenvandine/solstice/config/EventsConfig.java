package io.github.kenvandine.solstice.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Parsed events.yml — the four built-in events (Christmas, New Year, Halloween, Easter). */
public final class EventsConfig {

    public static final class BuiltinEvent {
        public final String key;
        public final boolean enabled;
        public final String name;
        public final boolean displayEvent;
        public final int startDay;
        public final int startMonth;
        public final int endDay;
        public final int endMonth;
        public final Set<String> disabledWorlds;
        public final List<String> startCommands;
        public final List<String> stopCommands;
        public final ConfigurationSection raw;

        BuiltinEvent(String key, ConfigurationSection section) {
            this.key = key;
            this.raw = section;
            this.enabled = section.getBoolean("enabled", true);
            this.name = section.getString("name", key);
            this.displayEvent = section.getBoolean("display-event", true);
            this.startDay = section.getInt("start-day", 1);
            this.startMonth = section.getInt("start-month", 1);
            this.endDay = section.getInt("end-day", startDay);
            this.endMonth = section.getInt("end-month", startMonth);
            this.disabledWorlds = new HashSet<>(section.getStringList("disabled-worlds"));
            this.startCommands = new ArrayList<>(section.getStringList("start-commands"));
            this.stopCommands = new ArrayList<>(section.getStringList("stop-commands"));
        }

        /** True if the (month, day) span wraps across the year boundary (e.g. Halloween 31/10-2/11). */
        public boolean wrapsYear() {
            return endMonth < startMonth || (endMonth == startMonth && endDay < startDay);
        }

        public boolean isWithin(int month, int day) {
            int startKey = startMonth * 100 + startDay;
            int endKey = endMonth * 100 + endDay;
            int key = month * 100 + day;
            if (!wrapsYear()) {
                return key >= startKey && key <= endKey;
            }
            return key >= startKey || key <= endKey;
        }
    }

    private final List<BuiltinEvent> events;

    private EventsConfig(List<BuiltinEvent> events) {
        this.events = events;
    }

    public static EventsConfig load(FileConfiguration yaml) {
        List<BuiltinEvent> events = new ArrayList<>();
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section != null) {
                events.add(new BuiltinEvent(key, section));
            }
        }
        return new EventsConfig(events);
    }

    public List<BuiltinEvent> events() {
        return events;
    }
}
