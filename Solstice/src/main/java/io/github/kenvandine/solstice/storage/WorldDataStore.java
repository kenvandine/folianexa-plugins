package io.github.kenvandine.solstice.storage;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;

/**
 * Per-world persisted calendar state (elapsed days, progress into the current day/night cycle,
 * pause flag). Plain file IO — callers should invoke save/load off the game-state threads via
 * {@code Schedulers.async}; this class itself does no scheduling.
 */
public final class WorldDataStore {

    public record Data(long totalDays, long ticksIntoDayNight, boolean timePaused) {
    }

    private final File folder;

    public WorldDataStore(Plugin plugin) {
        this.folder = new File(plugin.getDataFolder(), "worlddata");
        this.folder.mkdirs();
    }

    public Data load(String worldName, Data defaultData) {
        File file = new File(folder, worldName + ".yml");
        if (!file.exists()) {
            return defaultData;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        return new Data(
                yaml.getLong("total-days", defaultData.totalDays()),
                yaml.getLong("ticks-into-day-night", defaultData.ticksIntoDayNight()),
                yaml.getBoolean("time-paused", defaultData.timePaused())
        );
    }

    public void save(String worldName, Data data) {
        File file = new File(folder, worldName + ".yml");
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("total-days", data.totalDays());
        yaml.set("ticks-into-day-night", data.ticksIntoDayNight());
        yaml.set("time-paused", data.timePaused());
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }
}
