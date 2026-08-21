package io.github.kenvandine.solstice.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

/**
 * Loads every config file into an immutable snapshot object behind a volatile field. /reload
 * builds new snapshots and swaps the references — no thread ever observes a half-written config
 * (PLAN.md §2). Config parsing itself is plain file IO with no game-state access, so it is safe to
 * run on the caller's thread; callers that want a truly non-blocking reload should invoke this
 * through {@code Schedulers.async}.
 */
public final class ConfigManager {

    private final Plugin plugin;

    private volatile MainConfig mainConfig;
    private volatile CalendarConfig calendarConfig;
    private volatile TemperatureConfig temperatureConfig;
    private volatile BiomesConfig biomesConfig;
    private volatile EventsConfig eventsConfig;
    private volatile CustomEventsConfig customEventsConfig;
    private volatile Messages messages;

    public ConfigManager(Plugin plugin) {
        this.plugin = plugin;
    }

    public void loadAll() {
        saveDefaultIfMissing("config.yml");
        saveDefaultIfMissing("calendar.yml");
        saveDefaultIfMissing("temperature.yml");
        saveDefaultIfMissing("events.yml");
        saveDefaultIfMissing("custom-events.yml");
        saveDefaultIfMissing("biomes.yml");
        saveDefaultIfMissing("lang/en_US.yml");

        this.mainConfig = MainConfig.load(loadYaml("config.yml"));
        this.calendarConfig = CalendarConfig.load(loadYaml("calendar.yml"));
        this.temperatureConfig = TemperatureConfig.load(loadYaml("temperature.yml"));
        this.biomesConfig = BiomesConfig.load(loadYaml("biomes.yml"));
        this.eventsConfig = EventsConfig.load(loadYaml("events.yml"));
        this.customEventsConfig = CustomEventsConfig.load(loadYaml("custom-events.yml"));
        this.messages = Messages.load(loadYaml("lang/en_US.yml"));
    }

    public void reload() {
        loadAll();
    }

    private void saveDefaultIfMissing(String resourcePath) {
        File target = new File(plugin.getDataFolder(), resourcePath);
        if (target.exists()) {
            return;
        }
        try {
            target.getParentFile().mkdirs();
            try (InputStream in = plugin.getResource(resourcePath)) {
                if (in == null) {
                    plugin.getLogger().warning("No bundled default for " + resourcePath);
                    return;
                }
                Files.copy(in, target.toPath());
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to write default " + resourcePath + ": " + e.getMessage());
        }
    }

    private YamlConfiguration loadYaml(String resourcePath) {
        File file = new File(plugin.getDataFolder(), resourcePath);
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8)) {
            yaml.load(reader);
        } catch (Exception e) {
            plugin.getLogger().severe("Failed to load " + resourcePath + ": " + e.getMessage());
        }
        return yaml;
    }

    public MainConfig main() {
        return mainConfig;
    }

    public CalendarConfig calendar() {
        return calendarConfig;
    }

    public TemperatureConfig temperature() {
        return temperatureConfig;
    }

    public BiomesConfig biomes() {
        return biomesConfig;
    }

    public EventsConfig events() {
        return eventsConfig;
    }

    public CustomEventsConfig customEvents() {
        return customEventsConfig;
    }

    public Messages messages() {
        return messages;
    }
}
