package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.llm.LemonadeSettings;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.List;

/**
 * Reads {@code config.yml}'s {@code lemonade}/{@code generation}/{@code
 * paste}/{@code library}/{@code worldedit} sections into a {@link
 * WorldEditCraftConfig}. Every default below mirrors the shipped {@code
 * config.yml} exactly, so a key an operator deletes still behaves the
 * documented way rather than erroring.
 */
final class ConfigLoader {

    private ConfigLoader() {
    }

    static WorldEditCraftConfig load(FileConfiguration config) {
        return new WorldEditCraftConfig(
                loadLemonade(config),
                loadGeneration(config),
                loadPaste(config),
                loadLibrary(config),
                loadWorldEdit(config)
        );
    }

    private static LemonadeSettings loadLemonade(FileConfiguration config) {
        return new LemonadeSettings(
                config.getString("lemonade.base-url", "http://lemonade.local:13305"),
                config.getString("lemonade.api-path", "/api/v1"),
                config.getString("lemonade.model", ""),
                config.getString("lemonade.api-key", ""),
                config.getInt("lemonade.connect-timeout-seconds", 10),
                config.getInt("lemonade.request-timeout-seconds", 180),
                config.getDouble("lemonade.temperature", 0.4),
                config.getDouble("lemonade.top-p", 0.9),
                config.getInt("lemonade.max-tokens", 8192),
                config.getInt("lemonade.max-attempts", 3),
                config.getInt("lemonade.max-concurrent-requests", 2),
                config.getInt("lemonade.queue-capacity", 32)
        );
    }

    private static WorldEditCraftConfig.Generation loadGeneration(FileConfiguration config) {
        int[] defaultSize = intTriple(config.getIntegerList("generation.default-size"), new int[]{32, 24, 32});
        return new WorldEditCraftConfig.Generation(
                config.getInt("generation.max-dimension", 128),
                config.getLong("generation.max-volume", 400_000L),
                config.getInt("generation.max-ops", 400),
                defaultSize,
                config.getString("generation.block-policy", "vanilla"),
                config.getStringList("generation.block-allowlist"),
                config.getBoolean("generation.keep-failed-responses", true)
        );
    }

    private static WorldEditCraftConfig.Paste loadPaste(FileConfiguration config) {
        return new WorldEditCraftConfig.Paste(
                config.getInt("paste.blocks-per-tick", 2048),
                config.getBoolean("paste.clear-volume-first", false),
                config.getString("paste.unknown-block", "air"),
                config.getInt("paste.undo-history", 10),
                config.getInt("paste.max-concurrent-jobs", 2),
                config.getInt("paste.preview-seconds", 60)
        );
    }

    private static WorldEditCraftConfig.Library loadLibrary(FileConfiguration config) {
        return new WorldEditCraftConfig.Library(
                config.getString("library.directory", "schematics"),
                config.getInt("library.max-entries", 500)
        );
    }

    private static WorldEditCraftConfig.WorldEdit loadWorldEdit(FileConfiguration config) {
        return new WorldEditCraftConfig.WorldEdit(config.getBoolean("worldedit.delegate-paste", false));
    }

    private static int[] intTriple(List<Integer> list, int[] fallback) {
        if (list == null || list.size() != 3) {
            return fallback;
        }
        int[] result = new int[3];
        for (int i = 0; i < 3; i++) {
            result[i] = list.get(i);
        }
        return result;
    }
}
