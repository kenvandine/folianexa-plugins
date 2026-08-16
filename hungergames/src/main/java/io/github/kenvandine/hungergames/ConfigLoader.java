package io.github.kenvandine.hungergames;

import org.bukkit.configuration.file.FileConfiguration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Reads config.yml's {@code game}/{@code loot}/{@code twists}/{@code
 * arenas} sections into a {@link HungerGamesConfig}. {@code loot},
 * {@code twists.pool} and {@code arenas} are all YAML lists-of-maps
 * ({@link FileConfiguration#getMapList}), which hands back plain {@code
 * Map}/{@code List} objects rather than {@code ConfigurationSection}s — the
 * helpers below parse those defensively (missing keys fall back to a
 * default, numbers may arrive as Integer/Long/Double depending on how an
 * operator wrote them in YAML).
 */
final class ConfigLoader {

    private ConfigLoader() {
    }

    static HungerGamesConfig load(FileConfiguration config) {
        return new HungerGamesConfig(
                loadGame(config),
                loadLoot(config),
                config.getInt("game.items-per-chest-min", 2),
                config.getInt("game.items-per-chest-max", 5),
                loadTwists(config),
                loadArenas(config)
        );
    }

    private static HungerGamesConfig.Game loadGame(FileConfiguration config) {
        SpawnPoint lobbySpawn = new SpawnPoint(
                config.getDouble("game.lobby-spawn.x", 0),
                config.getDouble("game.lobby-spawn.y", 100),
                config.getDouble("game.lobby-spawn.z", 0),
                (float) config.getDouble("game.lobby-spawn.yaw", 0),
                (float) config.getDouble("game.lobby-spawn.pitch", 0)
        );
        return new HungerGamesConfig.Game(
                config.getInt("game.min-players", 2),
                config.getInt("game.max-players", 24),
                config.getInt("game.countdown-seconds", 30),
                config.getInt("game.grace-period-seconds", 30),
                config.getInt("game.game-length-seconds", 900),
                config.getInt("game.deathmatch-countdown-seconds", 15),
                config.getInt("game.ending-seconds", 10),
                config.getInt("game.final-border-diameter", 20),
                config.getInt("game.deathmatch-border-diameter", 6),
                config.getDouble("game.border-damage-amount", 1.0),
                config.getDouble("game.border-damage-buffer", 4.0),
                config.getInt("game.border-warning-distance", 5),
                config.getString("game.lobby-world", "world"),
                lobbySpawn
        );
    }

    private static List<LootEntry> loadLoot(FileConfiguration config) {
        List<LootEntry> entries = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("loot")) {
            entries.add(new LootEntry(
                    str(raw, "material", "STONE"),
                    intVal(raw, "min-amount", 1),
                    intVal(raw, "max-amount", 1),
                    doubleVal(raw, "weight", 1.0)
            ));
        }
        return entries;
    }

    private static HungerGamesConfig.Twists loadTwists(FileConfiguration config) {
        boolean enabled = config.getBoolean("twists.enabled", true);
        double chance = config.getDouble("twists.chance", 0.35);

        List<TwistConfig> pool = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("twists.pool")) {
            pool.add(new TwistConfig(
                    str(raw, "id", "unnamed-twist"),
                    str(raw, "display-name", "Twist"),
                    str(raw, "description", ""),
                    doubleVal(raw, "weight", 1.0),
                    intOrNull(raw, "grace-period-seconds"),
                    doubleOrNull(raw, "loot-multiplier"),
                    intOrNull(raw, "game-length-seconds"),
                    intOrNull(raw, "deathmatch-countdown-seconds"),
                    intOrNull(raw, "final-border-diameter"),
                    intOrNull(raw, "deathmatch-border-diameter"),
                    potionEffects(raw.get("potion-effects"))
            ));
        }
        return new HungerGamesConfig.Twists(enabled, chance, pool);
    }

    private static List<ArenaConfig> loadArenas(FileConfiguration config) {
        List<ArenaConfig> arenas = new ArrayList<>();
        for (Map<?, ?> raw : config.getMapList("arenas")) {
            String id = str(raw, "id", "arena");
            arenas.add(new ArenaConfig(
                    id,
                    str(raw, "display-name", id),
                    str(raw, "world", "world"),
                    point(raw.get("corner1")),
                    point(raw.get("corner2")),
                    point(raw.get("border-center")),
                    intVal(raw, "initial-border-radius", 100),
                    spawnPoints(raw.get("spawn-points")),
                    spawnPoints(raw.get("deathmatch-spawn-points")),
                    points(raw.get("chest-locations"))
            ));
        }
        return arenas;
    }

    private static String str(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        return value == null ? fallback : value.toString();
    }

    private static int intVal(Map<?, ?> map, String key, int fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : fallback;
    }

    private static double doubleVal(Map<?, ?> map, String key, double fallback) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : fallback;
    }

    private static Integer intOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.intValue() : null;
    }

    private static Double doubleOrNull(Map<?, ?> map, String key) {
        Object value = map.get(key);
        return value instanceof Number number ? number.doubleValue() : null;
    }

    private static Point point(Object raw) {
        if (!(raw instanceof Map<?, ?> map)) {
            return new Point(0, 0, 0);
        }
        return new Point(doubleVal(map, "x", 0), doubleVal(map, "y", 0), doubleVal(map, "z", 0));
    }

    private static List<Point> points(Object raw) {
        List<Point> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(point(map));
                }
            }
        }
        return result;
    }

    private static SpawnPoint spawnPoint(Map<?, ?> map) {
        return new SpawnPoint(
                doubleVal(map, "x", 0),
                doubleVal(map, "y", 0),
                doubleVal(map, "z", 0),
                (float) doubleVal(map, "yaw", 0),
                (float) doubleVal(map, "pitch", 0)
        );
    }

    private static List<SpawnPoint> spawnPoints(Object raw) {
        List<SpawnPoint> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(spawnPoint(map));
                }
            }
        }
        return result;
    }

    private static List<PotionEffectSpec> potionEffects(Object raw) {
        List<PotionEffectSpec> result = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof Map<?, ?> map) {
                    result.add(new PotionEffectSpec(
                            str(map, "type", "SPEED"),
                            intVal(map, "amplifier", 0),
                            intVal(map, "duration-seconds", 30)
                    ));
                }
            }
        }
        return result;
    }
}
