package io.github.kenvandine.solstice.storage;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Player toggle preferences, held in a {@code ConcurrentHashMap} and flushed to a single
 * playerdata.yml. Reads are lock-free; writes replace the whole {@link PlayerPrefs} record for a
 * player (PLAN.md §2 — no shared mutable state, only atomic whole-value swaps).
 */
public final class PlayerDataStore {

    private final File file;
    private final Map<UUID, PlayerPrefs> cache = new ConcurrentHashMap<>();

    public PlayerDataStore(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "playerdata.yml");
    }

    public void loadAll() {
        if (!file.exists()) {
            return;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        for (String key : yaml.getKeys(false)) {
            ConfigurationSection section = yaml.getConfigurationSection(key);
            if (section == null) continue;
            try {
                UUID id = UUID.fromString(key);
                cache.put(id, new PlayerPrefs(
                        section.getBoolean("temperature-display", true),
                        section.getBoolean("season-colors", true),
                        section.getBoolean("season-particles", true),
                        section.getBoolean("fahrenheit", false)
                ));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void saveAll() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<UUID, PlayerPrefs> entry : cache.entrySet()) {
            String path = entry.getKey().toString();
            PlayerPrefs prefs = entry.getValue();
            yaml.set(path + ".temperature-display", prefs.temperatureDisplay());
            yaml.set(path + ".season-colors", prefs.seasonColors());
            yaml.set(path + ".season-particles", prefs.seasonParticles());
            yaml.set(path + ".fahrenheit", prefs.fahrenheit());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    public PlayerPrefs get(UUID playerId) {
        return cache.computeIfAbsent(playerId, id -> PlayerPrefs.defaults());
    }

    public void set(UUID playerId, PlayerPrefs prefs) {
        cache.put(playerId, prefs);
    }

    public void forget(UUID playerId) {
        cache.remove(playerId);
    }
}
