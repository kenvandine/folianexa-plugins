package io.github.kenvandine.campuslobby;

import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.logging.Level;

/**
 * Persists {@link LobbyState} to a plugin-internal file, separate from
 * config.yml so operator comments/formatting in config.yml survive
 * (Bukkit's config saving would otherwise strip them).
 */
final class LobbyStateStore {
    private static final String FILE_NAME = "lobby-state.yml";

    private final CampusLobbyPlugin plugin;
    private final File file;

    LobbyStateStore(CampusLobbyPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), FILE_NAME);
    }

    LobbyState load() {
        if (!file.exists()) {
            return null;
        }
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String world = yaml.getString("world");
        if (world == null) {
            return null;
        }
        return new LobbyState(world, yaml.getInt("x"), yaml.getInt("y"), yaml.getInt("z"));
    }

    void save(LobbyState state) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("world", state.world());
        yaml.set("x", state.x());
        yaml.set("y", state.y());
        yaml.set("z", state.z());
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "Failed to save lobby state to " + file, e);
        }
    }
}
