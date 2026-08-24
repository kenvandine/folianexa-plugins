package io.github.kenvandine.flowerwatch.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Set;

/**
 * Thin, always-live wrapper over {@link JavaPlugin#getConfig()} — every
 * getter re-reads from the plugin's current config object rather than a
 * snapshot taken at construction time, so {@code /flowerwatch reload}
 * (which calls {@link JavaPlugin#reloadConfig()}) takes effect on the
 * very next event without needing to reconstruct or re-wire any of the
 * listener/scanner objects that hold a reference to this class.
 */
public final class FlowerWatchConfig {

    private final JavaPlugin plugin;

    public FlowerWatchConfig(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    private FileConfiguration raw() {
        return plugin.getConfig();
    }

    public boolean enabled() {
        return raw().getBoolean("enabled", true);
    }

    public boolean eventEnabled(String key) {
        return raw().getBoolean("events." + key, true);
    }

    public String logFile() {
        return raw().getString("log.file", "logs/flowerwatch.log");
    }

    public int logMaxFileSizeMb() {
        return Math.max(1, raw().getInt("log.max-file-size-mb", 10));
    }

    public int logMaxFiles() {
        return Math.max(1, raw().getInt("log.max-files", 5));
    }

    public boolean densityScanEnabled() {
        return raw().getBoolean("density-scan.enabled", true);
    }

    public long densityScanIntervalSeconds() {
        return Math.max(5, raw().getLong("density-scan.interval-seconds", 60));
    }

    public int densityScanAlertThreshold() {
        return Math.max(1, raw().getInt("density-scan.alert-threshold", 25));
    }

    public int densityScanMinY() {
        return raw().getInt("density-scan.min-y", -64);
    }

    public int densityScanMaxY() {
        return raw().getInt("density-scan.max-y", 192);
    }

    public Set<String> densityScanWorlds() {
        return Set.copyOf(raw().getStringList("density-scan.worlds"));
    }

    public boolean coreProtectEnabled() {
        return raw().getBoolean("coreprotect.enabled", true);
    }

    public int coreProtectLookbackSeconds() {
        return Math.max(1, raw().getInt("coreprotect.lookback-seconds", 5));
    }

    public int coreProtectMaxLookupsPerMinute() {
        return Math.max(0, raw().getInt("coreprotect.max-lookups-per-minute", 60));
    }
}
