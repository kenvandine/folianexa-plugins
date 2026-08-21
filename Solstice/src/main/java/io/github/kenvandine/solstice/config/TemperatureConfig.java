package io.github.kenvandine.solstice.config;

import io.github.kenvandine.solstice.api.Season;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.potion.PotionEffectType;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class TemperatureConfig {

    public record Range(double min, double max) {
        public double base(double progress01) {
            return min + (max - min) * Math.clamp(progress01, 0.0, 1.0);
        }
    }

    public record BiomeGroup(Set<Biome> biomes, double modifier) {
    }

    public record NearbyBlockRule(Material material, double modifier) {
    }

    public record CustomItem(Material material, Integer customModelData, double modifier, boolean armorSlot) {
    }

    public final boolean enabled;
    public final int recalculateIntervalSeconds;
    public final boolean defaultFahrenheit;
    public final Set<World.Environment> disabledDimensions;
    public final boolean actionBar;
    public final boolean bossBar;
    public final boolean bossBarPadding;
    public final boolean sideBar;
    public final boolean chatWarnings;

    public final Map<Season, Range> seasonBase;
    public final double weatherClear;
    public final double weatherRainOrSnow;
    public final double weatherThunder;

    public final double nightModifier;
    public final double middayModifier;
    public final long nightStartTick;
    public final long nightEndTick;
    public final long middayStartTick;
    public final long middayEndTick;

    public final List<BiomeGroup> biomeGroups;

    public final int altitudeReferenceY;
    public final double altitudeAbovePerBlock;
    public final double altitudeBelowPerBlockWinter;

    public final double waterSummerWinterModifier;
    public final double waterSpringAutumnModifier;
    public final double waterDecayPer2Seconds;

    public final double sprintingMaxModifier;

    public final int nearbyBlocksRadius;
    public final List<NearbyBlockRule> nearbyBlockRules;
    public final double nearbyBlocksCap;

    public final double armorLeatherPerPiece;
    public final double armorLeatherFullSet;
    public final double armorLeatherCap;
    public final double armorMetalPerPiece;
    public final double armorMetalFullSet;
    public final double armorNetheritePerPiece;
    public final double armorNetheriteFullSet;

    public final double foodFullHungerBelowC;
    public final double foodFullHungerModifier;
    public final double foodWaterBottleAboveC;
    public final double foodWaterBottleModifier;
    public final int foodWaterBottleDurationSeconds;

    public final double freezingDamageBelowC;
    public final int freezingDamageIntervalSeconds;
    public final double slownessBelowC;
    public final double hungerBelowC;
    public final double coldBreathAtOrBelowC;
    public final double comfortableMinC;
    public final double comfortableMaxC;
    public final PotionEffectType comfortableEffect;
    public final int comfortableEffectAmplifier;
    public final double sweatingAboveC;
    public final double healingDisabledAboveC;
    public final double slownessAboveC;
    public final double burningAboveC;

    public final List<CustomItem> customItems;

    private TemperatureConfig(Builder b) {
        this.enabled = b.enabled;
        this.recalculateIntervalSeconds = b.recalculateIntervalSeconds;
        this.defaultFahrenheit = b.defaultFahrenheit;
        this.disabledDimensions = b.disabledDimensions;
        this.actionBar = b.actionBar;
        this.bossBar = b.bossBar;
        this.bossBarPadding = b.bossBarPadding;
        this.sideBar = b.sideBar;
        this.chatWarnings = b.chatWarnings;
        this.seasonBase = b.seasonBase;
        this.weatherClear = b.weatherClear;
        this.weatherRainOrSnow = b.weatherRainOrSnow;
        this.weatherThunder = b.weatherThunder;
        this.nightModifier = b.nightModifier;
        this.middayModifier = b.middayModifier;
        this.nightStartTick = b.nightStartTick;
        this.nightEndTick = b.nightEndTick;
        this.middayStartTick = b.middayStartTick;
        this.middayEndTick = b.middayEndTick;
        this.biomeGroups = b.biomeGroups;
        this.altitudeReferenceY = b.altitudeReferenceY;
        this.altitudeAbovePerBlock = b.altitudeAbovePerBlock;
        this.altitudeBelowPerBlockWinter = b.altitudeBelowPerBlockWinter;
        this.waterSummerWinterModifier = b.waterSummerWinterModifier;
        this.waterSpringAutumnModifier = b.waterSpringAutumnModifier;
        this.waterDecayPer2Seconds = b.waterDecayPer2Seconds;
        this.sprintingMaxModifier = b.sprintingMaxModifier;
        this.nearbyBlocksRadius = b.nearbyBlocksRadius;
        this.nearbyBlockRules = b.nearbyBlockRules;
        this.nearbyBlocksCap = b.nearbyBlocksCap;
        this.armorLeatherPerPiece = b.armorLeatherPerPiece;
        this.armorLeatherFullSet = b.armorLeatherFullSet;
        this.armorLeatherCap = b.armorLeatherCap;
        this.armorMetalPerPiece = b.armorMetalPerPiece;
        this.armorMetalFullSet = b.armorMetalFullSet;
        this.armorNetheritePerPiece = b.armorNetheritePerPiece;
        this.armorNetheriteFullSet = b.armorNetheriteFullSet;
        this.foodFullHungerBelowC = b.foodFullHungerBelowC;
        this.foodFullHungerModifier = b.foodFullHungerModifier;
        this.foodWaterBottleAboveC = b.foodWaterBottleAboveC;
        this.foodWaterBottleModifier = b.foodWaterBottleModifier;
        this.foodWaterBottleDurationSeconds = b.foodWaterBottleDurationSeconds;
        this.freezingDamageBelowC = b.freezingDamageBelowC;
        this.freezingDamageIntervalSeconds = b.freezingDamageIntervalSeconds;
        this.slownessBelowC = b.slownessBelowC;
        this.hungerBelowC = b.hungerBelowC;
        this.coldBreathAtOrBelowC = b.coldBreathAtOrBelowC;
        this.comfortableMinC = b.comfortableMinC;
        this.comfortableMaxC = b.comfortableMaxC;
        this.comfortableEffect = b.comfortableEffect;
        this.comfortableEffectAmplifier = b.comfortableEffectAmplifier;
        this.sweatingAboveC = b.sweatingAboveC;
        this.healingDisabledAboveC = b.healingDisabledAboveC;
        this.slownessAboveC = b.slownessAboveC;
        this.burningAboveC = b.burningAboveC;
        this.customItems = b.customItems;
    }

    private static final class Builder {
        boolean enabled;
        int recalculateIntervalSeconds;
        boolean defaultFahrenheit;
        Set<World.Environment> disabledDimensions = EnumSet.noneOf(World.Environment.class);
        boolean actionBar;
        boolean bossBar;
        boolean bossBarPadding;
        boolean sideBar;
        boolean chatWarnings;
        Map<Season, Range> seasonBase = new EnumMap<>(Season.class);
        double weatherClear, weatherRainOrSnow, weatherThunder;
        double nightModifier, middayModifier;
        long nightStartTick, nightEndTick, middayStartTick, middayEndTick;
        List<BiomeGroup> biomeGroups = new ArrayList<>();
        int altitudeReferenceY;
        double altitudeAbovePerBlock, altitudeBelowPerBlockWinter;
        double waterSummerWinterModifier, waterSpringAutumnModifier, waterDecayPer2Seconds;
        double sprintingMaxModifier;
        int nearbyBlocksRadius;
        List<NearbyBlockRule> nearbyBlockRules = new ArrayList<>();
        double nearbyBlocksCap;
        double armorLeatherPerPiece, armorLeatherFullSet, armorLeatherCap;
        double armorMetalPerPiece, armorMetalFullSet;
        double armorNetheritePerPiece, armorNetheriteFullSet;
        double foodFullHungerBelowC, foodFullHungerModifier;
        double foodWaterBottleAboveC, foodWaterBottleModifier;
        int foodWaterBottleDurationSeconds;
        double freezingDamageBelowC;
        int freezingDamageIntervalSeconds;
        double slownessBelowC, hungerBelowC, coldBreathAtOrBelowC;
        double comfortableMinC, comfortableMaxC;
        PotionEffectType comfortableEffect;
        int comfortableEffectAmplifier;
        double sweatingAboveC, healingDisabledAboveC, slownessAboveC, burningAboveC;
        List<CustomItem> customItems = new ArrayList<>();
    }

    public static TemperatureConfig load(FileConfiguration yaml) {
        Builder b = new Builder();
        b.enabled = yaml.getBoolean("enabled", true);
        b.recalculateIntervalSeconds = yaml.getInt("recalculate-interval-seconds", 2);
        b.defaultFahrenheit = "fahrenheit".equalsIgnoreCase(yaml.getString("default-unit", "celsius"));
        for (String dim : yaml.getStringList("disabled-dimensions")) {
            try {
                b.disabledDimensions.add(World.Environment.valueOf(dim.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException ignored) {
            }
        }
        b.actionBar = yaml.getBoolean("display.action-bar", true);
        b.bossBar = yaml.getBoolean("display.boss-bar", false);
        b.bossBarPadding = yaml.getBoolean("display.boss-bar-padding", false);
        b.sideBar = yaml.getBoolean("display.side-bar", false);
        b.chatWarnings = yaml.getBoolean("display.chat-warnings", true);

        ConfigurationSection seasonBase = yaml.getConfigurationSection("season-base");
        for (Season season : Season.values()) {
            ConfigurationSection s = seasonBase != null ? seasonBase.getConfigurationSection(season.name()) : null;
            b.seasonBase.put(season, s != null
                    ? new Range(s.getDouble("min", 0), s.getDouble("max", 20))
                    : new Range(0, 20));
        }

        b.weatherClear = yaml.getDouble("weather.clear", 0);
        b.weatherRainOrSnow = yaml.getDouble("weather.rain-or-snow", -4);
        b.weatherThunder = yaml.getDouble("weather.thunder", -5);

        b.nightModifier = yaml.getDouble("time-of-day.night-modifier", -5);
        b.middayModifier = yaml.getDouble("time-of-day.midday-modifier", 3);
        b.nightStartTick = yaml.getLong("time-of-day.night-start-tick", 14800);
        b.nightEndTick = yaml.getLong("time-of-day.night-end-tick", 23500);
        b.middayStartTick = yaml.getLong("time-of-day.midday-start-tick", 6000);
        b.middayEndTick = yaml.getLong("time-of-day.midday-end-tick", 12000);

        ConfigurationSection groups = yaml.getConfigurationSection("biome-groups");
        if (groups != null) {
            for (String key : groups.getKeys(false)) {
                ConfigurationSection g = groups.getConfigurationSection(key);
                if (g == null) continue;
                Set<Biome> biomes = new HashSet<>();
                for (String name : g.getStringList("biomes")) {
                    Biome biome = Registry.BIOME.get(NamespacedKey.minecraft(name.toLowerCase(Locale.ROOT)));
                    if (biome != null) {
                        biomes.add(biome);
                    }
                }
                b.biomeGroups.add(new BiomeGroup(biomes, g.getDouble("modifier", 0)));
            }
        }

        b.altitudeReferenceY = yaml.getInt("altitude.reference-y", 64);
        b.altitudeAbovePerBlock = yaml.getDouble("altitude.above-per-block", -0.08);
        b.altitudeBelowPerBlockWinter = yaml.getDouble("altitude.below-per-block-winter", 0.2);

        b.waterSummerWinterModifier = yaml.getDouble("water.summer-winter-modifier", -10);
        b.waterSpringAutumnModifier = yaml.getDouble("water.spring-autumn-modifier", -4);
        b.waterDecayPer2Seconds = yaml.getDouble("water.decay-per-2-seconds", 1);

        b.sprintingMaxModifier = yaml.getDouble("sprinting.max-modifier", 4);

        ConfigurationSection nearby = yaml.getConfigurationSection("nearby-blocks");
        b.nearbyBlocksRadius = nearby != null ? nearby.getInt("radius", 16) : 16;
        b.nearbyBlocksCap = nearby != null ? nearby.getDouble("cap", 30) : 30;
        if (nearby != null) {
            addRule(b, nearby, "lava", Material.LAVA);
            addRule(b, nearby, "fire", Material.FIRE);
            addRule(b, nearby, "campfire", Material.CAMPFIRE);
            addRule(b, nearby, "torch", Material.TORCH);
            addRule(b, nearby, "lantern", Material.LANTERN);
            addRule(b, nearby, "soul-fire", Material.SOUL_FIRE);
            addRule(b, nearby, "blue-ice", Material.BLUE_ICE);
            addRule(b, nearby, "soul-campfire", Material.SOUL_CAMPFIRE);
            addRule(b, nearby, "soul-torch", Material.SOUL_TORCH);
            addRule(b, nearby, "soul-lantern", Material.SOUL_LANTERN);
            addRule(b, nearby, "ice", Material.ICE);
            addRule(b, nearby, "packed-ice", Material.PACKED_ICE);
        }

        b.armorLeatherPerPiece = yaml.getDouble("armor.leather-per-piece", 5);
        b.armorLeatherFullSet = yaml.getDouble("armor.leather-full-set", 20);
        b.armorLeatherCap = yaml.getDouble("armor.leather-cap", 25);
        b.armorMetalPerPiece = yaml.getDouble("armor.iron-gold-diamond-per-piece", 1.25);
        b.armorMetalFullSet = yaml.getDouble("armor.iron-gold-diamond-full-set", 5);
        b.armorNetheritePerPiece = yaml.getDouble("armor.netherite-per-piece", 0.75);
        b.armorNetheriteFullSet = yaml.getDouble("armor.netherite-full-set", 3);

        b.foodFullHungerBelowC = yaml.getDouble("food.full-hunger-below-c", 25);
        b.foodFullHungerModifier = yaml.getDouble("food.full-hunger-modifier", 5);
        b.foodWaterBottleAboveC = yaml.getDouble("food.water-bottle-above-c", 25);
        b.foodWaterBottleModifier = yaml.getDouble("food.water-bottle-modifier", -10);
        b.foodWaterBottleDurationSeconds = yaml.getInt("food.water-bottle-duration-seconds", 300);

        b.freezingDamageBelowC = yaml.getDouble("effects.freezing-damage-below-c", -20);
        b.freezingDamageIntervalSeconds = yaml.getInt("effects.freezing-damage-interval-seconds", 2);
        b.slownessBelowC = yaml.getDouble("effects.slowness-below-c", -15);
        b.hungerBelowC = yaml.getDouble("effects.hunger-below-c", -10);
        b.coldBreathAtOrBelowC = yaml.getDouble("effects.cold-breath-at-or-below-c", 0);
        b.comfortableMinC = yaml.getDouble("effects.comfortable-min-c", 15);
        b.comfortableMaxC = yaml.getDouble("effects.comfortable-max-c", 30);
        String effectName = yaml.getString("effects.comfortable-effect", "NONE");
        b.comfortableEffect = effectName == null || effectName.equalsIgnoreCase("NONE")
                ? null : PotionEffectType.getByName(effectName.toUpperCase(Locale.ROOT));
        b.comfortableEffectAmplifier = yaml.getInt("effects.comfortable-effect-amplifier", 0);
        b.sweatingAboveC = yaml.getDouble("effects.sweating-above-c", 40);
        b.healingDisabledAboveC = yaml.getDouble("effects.healing-disabled-above-c", 50);
        b.slownessAboveC = yaml.getDouble("effects.slowness-above-c", 60);
        b.burningAboveC = yaml.getDouble("effects.burning-above-c", 65);

        List<?> rawItems = yaml.getList("custom-items");
        if (rawItems != null) {
            for (Object raw : rawItems) {
                if (!(raw instanceof ConfigurationSection section)) continue;
                Material mat;
                try {
                    mat = Material.valueOf(section.getString("material", "AIR").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    continue;
                }
                Integer cmd = section.contains("custom-model-data") ? section.getInt("custom-model-data") : null;
                double modifier = section.getDouble("modifier", 0);
                boolean armorSlot = "ARMOR".equalsIgnoreCase(section.getString("slot", "HAND"));
                b.customItems.add(new CustomItem(mat, cmd, modifier, armorSlot));
            }
        }

        return new TemperatureConfig(b);
    }

    private static void addRule(Builder b, ConfigurationSection nearby, String key, Material material) {
        if (nearby.contains(key)) {
            b.nearbyBlockRules.add(new NearbyBlockRule(material, nearby.getDouble(key, 0)));
        }
    }
}
