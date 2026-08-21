package io.github.kenvandine.solstice.config;

import io.github.kenvandine.solstice.api.Season;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public record MainConfig(
        Set<String> enabledWorlds,
        Set<String> disabledWorlds,
        Map<Season, MonthDay> seasonStarts,
        boolean subSeasonsEnabled,
        boolean biomeColorsEnabled,
        boolean skipBedrockClients,
        boolean particlesEnabled,
        boolean cherryBlossomSpring,
        boolean snowIceEnabled,
        int snowIceBlocksPerTickPerRegion,
        boolean floraEnabled,
        boolean winterCropsRequireRoof,
        double summerCropGrowthMultiplier,
        boolean summerHusks,
        boolean winterStrays,
        double autumnPumpkinChance,
        String claimGuardProvider,
        boolean registerRsAlias,
        int playerDataSaveIntervalSeconds
) {
    public record MonthDay(int day, int month) {
    }

    public boolean managesWorld(String worldName) {
        if (disabledWorlds.contains(worldName)) {
            return false;
        }
        return enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    public static MainConfig load(FileConfiguration yaml) {
        ConfigurationSection worlds = yaml.getConfigurationSection("worlds");
        Set<String> enabled = new HashSet<>(worlds != null ? worlds.getStringList("enabled") : java.util.List.of());
        Set<String> disabled = new HashSet<>(worlds != null ? worlds.getStringList("disabled-worlds") : java.util.List.of());

        Map<Season, MonthDay> starts = new EnumMap<>(Season.class);
        ConfigurationSection seasons = yaml.getConfigurationSection("seasons");
        starts.put(Season.SPRING, readMonthDay(seasons, "spring", 4, 3));
        starts.put(Season.SUMMER, readMonthDay(seasons, "summer", 4, 6));
        starts.put(Season.AUTUMN, readMonthDay(seasons, "autumn", 4, 9));
        starts.put(Season.WINTER, readMonthDay(seasons, "winter", 4, 12));

        return new MainConfig(
                enabled,
                disabled,
                starts,
                yaml.getBoolean("sub-seasons.enabled", true),
                yaml.getBoolean("visuals.biome-colors.enabled", true),
                yaml.getBoolean("visuals.biome-colors.skip-bedrock-clients", true),
                yaml.getBoolean("visuals.particles.enabled", true),
                yaml.getBoolean("visuals.cherry-blossom-spring", true),
                yaml.getBoolean("world-effects.snow-ice.enabled", true),
                yaml.getInt("world-effects.snow-ice.blocks-per-tick-per-region", 8),
                yaml.getBoolean("world-effects.flora.enabled", true),
                yaml.getBoolean("world-effects.crops.winter-requires-roof", true),
                yaml.getDouble("world-effects.crops.summer-growth-multiplier", 2.0),
                yaml.getBoolean("world-effects.mob-replacements.summer-husks", true),
                yaml.getBoolean("world-effects.mob-replacements.winter-strays", true),
                yaml.getDouble("world-effects.mob-replacements.autumn-pumpkin-chance", 0.20),
                yaml.getString("claim-guard.provider", "none"),
                yaml.getBoolean("placeholderapi.register-rs-alias", false),
                yaml.getInt("storage.player-data-save-interval-seconds", 60)
        );
    }

    private static MonthDay readMonthDay(ConfigurationSection seasons, String key, int defDay, int defMonth) {
        ConfigurationSection section = seasons != null ? seasons.getConfigurationSection(key) : null;
        if (section == null) {
            return new MonthDay(defDay, defMonth);
        }
        return new MonthDay(section.getInt("start-day", defDay), section.getInt("start-month", defMonth));
    }
}
