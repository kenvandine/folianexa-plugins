package io.github.kenvandine.solstice.config;

import io.github.kenvandine.solstice.api.Season;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parsed biomes.yml: per-category seasonal colors, plus which biomes fall into each category.
 * A biome not explicitly categorized here has no category of its own — see PLAN.md §4 on
 * classifying unknown/modded biomes; a live registry-temperature lookup is future work, this is
 * the static approximation for the first build. Consumers that want a color still fall back to
 * "temperate" ({@link #colorsFor}); consumers like flora density instead see the distinct
 * {@link #UNCATEGORIZED_CATEGORY} sentinel so they can apply their own fallback (e.g. config.yml's
 * "default" biome-density entry) instead of silently being treated as "temperate".
 */
public final class BiomesConfig {

    public record RgbColors(int foliage, int grass, int sky, int water, int waterFog, int fog) {
    }

    public static final String FALLBACK_CATEGORY = "temperate";

    /** Returned by {@link #categoryFor} for biomes with no entry in biomes.yml's categories. */
    public static final String UNCATEGORIZED_CATEGORY = "__uncategorized__";

    private final Map<Biome, String> biomeToCategory;
    private final Map<String, Map<Season, RgbColors>> categoryColors;

    private BiomesConfig(Map<Biome, String> biomeToCategory, Map<String, Map<Season, RgbColors>> categoryColors) {
        this.biomeToCategory = biomeToCategory;
        this.categoryColors = categoryColors;
    }

    public static BiomesConfig load(FileConfiguration yaml) {
        Map<Biome, String> biomeToCategory = new HashMap<>();
        ConfigurationSection categories = yaml.getConfigurationSection("categories");
        if (categories != null) {
            for (String category : categories.getKeys(false)) {
                for (String biomeName : categories.getStringList(category)) {
                    Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(biomeName.toLowerCase(Locale.ROOT)));
                    if (biome != null) {
                        biomeToCategory.put(biome, category);
                    }
                }
            }
        }

        Map<String, Map<Season, RgbColors>> categoryColors = new HashMap<>();
        ConfigurationSection colors = yaml.getConfigurationSection("colors");
        if (colors != null) {
            for (String category : colors.getKeys(false)) {
                ConfigurationSection catSection = colors.getConfigurationSection(category);
                if (catSection == null) continue;
                Map<Season, RgbColors> perSeason = new EnumMap<>(Season.class);
                for (Season season : Season.values()) {
                    ConfigurationSection s = catSection.getConfigurationSection(season.name());
                    perSeason.put(season, s != null ? readColors(s) : defaultColors());
                }
                categoryColors.put(category, perSeason);
            }
        }
        categoryColors.putIfAbsent(FALLBACK_CATEGORY, defaultCategoryColors());

        return new BiomesConfig(biomeToCategory, categoryColors);
    }

    private static RgbColors readColors(ConfigurationSection s) {
        return new RgbColors(
                hex(s.getString("foliage", "#7CC66F")),
                hex(s.getString("grass", "#8FE060")),
                hex(s.getString("sky", "#8FC8E8")),
                hex(s.getString("water", "#5FB8E8")),
                hex(s.getString("water-fog", "#5FB8E8")),
                hex(s.getString("fog", "#C9E8F5"))
        );
    }

    private static int hex(String value) {
        String v = value.startsWith("#") ? value.substring(1) : value;
        return Integer.parseInt(v, 16);
    }

    private static RgbColors defaultColors() {
        return new RgbColors(0x7CC66F, 0x8FE060, 0x8FC8E8, 0x5FB8E8, 0x5FB8E8, 0xC9E8F5);
    }

    private static Map<Season, RgbColors> defaultCategoryColors() {
        Map<Season, RgbColors> map = new EnumMap<>(Season.class);
        for (Season season : Season.values()) {
            map.put(season, defaultColors());
        }
        return map;
    }

    /** @return the biome's configured category, or {@link #UNCATEGORIZED_CATEGORY} if none is set. */
    public String categoryFor(Biome biome) {
        return biomeToCategory.getOrDefault(biome, UNCATEGORIZED_CATEGORY);
    }

    public RgbColors colorsFor(Biome biome, Season season) {
        Map<Season, RgbColors> perSeason = categoryColors.getOrDefault(categoryFor(biome), categoryColors.get(FALLBACK_CATEGORY));
        return perSeason.get(season);
    }

    public List<String> categories() {
        return List.copyOf(categoryColors.keySet());
    }
}
