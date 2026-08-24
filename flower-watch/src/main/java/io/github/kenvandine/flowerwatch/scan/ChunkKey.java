package io.github.kenvandine.flowerwatch.scan;

/** Plain identity for a chunk — no Bukkit types, so {@link DensityTracker} is directly unit-testable. */
public record ChunkKey(String world, int chunkX, int chunkZ) {
}
