package io.github.kenvandine.campuslobby;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.LinkedHashMap;
import java.util.Map;

/** Reads config.yml's {@code scene}/{@code signs} sections into a {@link SceneConfig}. */
final class ConfigLoader {

    private ConfigLoader() {
    }

    static SceneConfig load(FileConfiguration config) {
        SceneConfig defaults = SceneConfig.defaults();

        int plazaRadius = config.getInt("scene.plaza-radius", defaults.plazaRadius());
        int towerHeight = config.getInt("scene.tower-height", defaults.towerHeight());

        SceneConfig.Include include = new SceneConfig.Include(
                config.getBoolean("scene.include.belltower", true),
                config.getBoolean("scene.include.wolf-statue", true),
                config.getBoolean("scene.include.tunnel", true),
                config.getBoolean("scene.include.union-facade", true),
                config.getBoolean("scene.include.library-facade", true)
        );

        SceneConfig.Colors defaultColors = defaults.colors();
        SceneConfig.Colors colors = new SceneConfig.Colors(
                config.getString("scene.colors.primary-red", defaultColors.primaryRed()),
                config.getString("scene.colors.white", defaultColors.white()),
                config.getString("scene.colors.black", defaultColors.black()),
                config.getString("scene.colors.brick", defaultColors.brick()),
                config.getString("scene.colors.brick-trim", defaultColors.brickTrim()),
                config.getString("scene.colors.glass", defaultColors.glass()),
                config.getString("scene.colors.roof", defaultColors.roof()),
                config.getString("scene.colors.clay-red", defaultColors.clayRed()),
                config.getString("scene.colors.clay-white", defaultColors.clayWhite()),
                config.getString("scene.colors.clay-black", defaultColors.clayBlack()),
                config.getString("scene.colors.clay-gray", defaultColors.clayGray()),
                config.getString("scene.colors.clay-normal", defaultColors.clayNormal())
        );

        Map<String, String> signLabels = new LinkedHashMap<>();
        ConfigurationSection signsSection = config.getConfigurationSection("signs");
        if (signsSection != null) {
            for (String key : signsSection.getKeys(false)) {
                signLabels.put(key, config.getString("signs." + key));
            }
        }

        SceneConfig.Clear clear = new SceneConfig.Clear(
                config.getInt("scene.clear.padding", defaults.clear().padding()),
                config.getInt("scene.clear.height-above", defaults.clear().heightAbove())
        );
        int borderMargin = config.getInt("scene.border-margin", defaults.borderMargin());

        return new SceneConfig(plazaRadius, towerHeight, include, colors, signLabels, clear, borderMargin);
    }
}
