package io.github.kenvandine.hungergames;

import java.util.List;

/**
 * A single map ("arena") tributes can play a match on. Entirely config-driven
 * — see config.yml's {@code arenas} list — so adding a new map is purely a
 * config.yml edit (build the map, add one entry here), no code changes
 * needed. {@code world} is expected to be its own dedicated Bukkit world per
 * arena: that lets this plugin drive the map's shrinking border with the
 * real per-world {@code WorldBorder} API (native fog/push/damage, animated
 * shrink) instead of reimplementing border math and damage by hand — see
 * GameManager's javadoc.
 */
record ArenaConfig(
        String id,
        String displayName,
        String world,
        Point corner1,
        Point corner2,
        Point borderCenter,
        int initialBorderRadius,
        List<SpawnPoint> spawnPoints,
        List<SpawnPoint> deathmatchSpawnPoints,
        List<Point> chestLocations
) {
}
