package io.github.kenvandine.solstice.temperature;

import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.config.TemperatureConfig;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;

import java.util.HashMap;
import java.util.Map;

/**
 * Pure temperature math. All methods are stateless functions of config + world/location inputs so
 * they can run on any thread (region, entity, or otherwise) without synchronization.
 */
public final class TemperatureModel {

    private TemperatureModel() {
    }

    /** Ambient air temperature at a location: season base + weather + time-of-day + biome + altitude. */
    public static double airTemperature(TemperatureConfig cfg, Season season, double seasonProgress, Location location) {
        World world = location.getWorld();
        TemperatureConfig.Range range = cfg.seasonBase.get(season);
        double base = range.base(seasonProgress);
        double weather = weatherModifier(cfg, world);
        double time = timeOfDayModifier(cfg, world.getTime());
        double biome = biomeModifier(cfg, location.getBlock().getBiome());
        double altitude = altitudeModifier(cfg, season, location.getBlockY());
        return base + weather + time + biome + altitude;
    }

    public static double weatherModifier(TemperatureConfig cfg, World world) {
        if (world.isThundering()) {
            return cfg.weatherThunder;
        }
        if (world.hasStorm()) {
            return cfg.weatherRainOrSnow;
        }
        return cfg.weatherClear;
    }

    /**
     * Smooth (piecewise-linear, circular) interpolation across the four key ticks in
     * temperature.yml: flat +midday from middayStart..middayEnd, flat -night from
     * nightStart..nightEnd, and linear ramps filling the two gaps between them.
     */
    public static double timeOfDayModifier(TemperatureConfig cfg, long worldTime) {
        long t = Math.floorMod(worldTime, 24000L);
        long p0 = cfg.middayStartTick, p1 = cfg.middayEndTick, p2 = cfg.nightStartTick, p3 = cfg.nightEndTick;
        double midday = cfg.middayModifier, night = cfg.nightModifier;

        if (t >= p0 && t <= p1) {
            return midday;
        }
        if (t > p1 && t < p2) {
            return lerp(midday, night, (t - p1) / (double) (p2 - p1));
        }
        if (t >= p2 && t <= p3) {
            return night;
        }
        // Predawn ramp: wraps through midnight from nightEnd back around to middayStart.
        long tt = t > p3 ? t : t + 24000L;
        long wrappedP0 = p0 + 24000L;
        return lerp(night, midday, (tt - p3) / (double) (wrappedP0 - p3));
    }

    private static double lerp(double from, double to, double frac) {
        return from + (to - from) * Math.clamp(frac, 0.0, 1.0);
    }

    public static double biomeModifier(TemperatureConfig cfg, Biome biome) {
        for (TemperatureConfig.BiomeGroup group : cfg.biomeGroups) {
            if (group.biomes().contains(biome)) {
                return group.modifier();
            }
        }
        return 0.0;
    }

    public static double altitudeModifier(TemperatureConfig cfg, Season season, int y) {
        int ref = cfg.altitudeReferenceY;
        if (y > ref && season != Season.WINTER) {
            return (y - ref) * cfg.altitudeAbovePerBlock;
        }
        if (y < ref && season == Season.WINTER) {
            return (ref - y) * cfg.altitudeBelowPerBlockWinter;
        }
        return 0.0;
    }

    /** Sums modifiers for configured block types within radius (squared-distance sphere) of location. */
    public static double nearbyBlocksModifier(TemperatureConfig cfg, Location location) {
        if (cfg.nearbyBlockRules.isEmpty()) {
            return 0.0;
        }
        Map<Material, Double> byMaterial = new HashMap<>();
        for (TemperatureConfig.NearbyBlockRule rule : cfg.nearbyBlockRules) {
            byMaterial.put(rule.material(), rule.modifier());
        }

        World world = location.getWorld();
        int radius = cfg.nearbyBlocksRadius;
        int radiusSq = radius * radius;
        int cx = location.getBlockX();
        int cy = location.getBlockY();
        int cz = location.getBlockZ();
        int minY = world.getMinHeight();
        int maxY = world.getMaxHeight();

        double total = 0.0;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                int y = cy + dy;
                if (y < minY || y >= maxY) {
                    continue;
                }
                for (int dz = -radius; dz <= radius; dz++) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) {
                        continue;
                    }
                    Material type = world.getBlockAt(cx + dx, y, cz + dz).getType();
                    Double modifier = byMaterial.get(type);
                    if (modifier != null) {
                        total += modifier;
                    }
                }
            }
        }
        return total;
    }

    public static double armorModifier(TemperatureConfig cfg, org.bukkit.inventory.ItemStack[] armor) {
        int leather = 0, metal = 0, netherite = 0;
        for (org.bukkit.inventory.ItemStack piece : armor) {
            if (piece == null) continue;
            Material type = piece.getType();
            String name = type.name();
            if (name.startsWith("LEATHER_")) {
                leather++;
            } else if (name.startsWith("IRON_") || name.startsWith("GOLDEN_") || name.startsWith("DIAMOND_")) {
                metal++;
            } else if (name.startsWith("NETHERITE_")) {
                netherite++;
            }
        }
        double leatherValue = leather == 4 ? cfg.armorLeatherFullSet : leather * cfg.armorLeatherPerPiece;
        leatherValue = Math.min(leatherValue, cfg.armorLeatherCap);
        double metalValue = metal == 4 ? cfg.armorMetalFullSet : metal * cfg.armorMetalPerPiece;
        double netheriteValue = netherite == 4 ? cfg.armorNetheriteFullSet : netherite * cfg.armorNetheritePerPiece;
        return leatherValue + metalValue + netheriteValue;
    }
}
