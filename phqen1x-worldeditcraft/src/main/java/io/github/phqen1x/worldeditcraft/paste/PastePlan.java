package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.dsl.Marker;
import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.Transform;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure planning for a paste: applies a {@link Transform} and an origin,
 * buckets placements by the chunk they land in, splits each chunk's
 * placements into per-tick batches, and separates pass 1 (solids) from
 * pass 2 (attachment-sensitive blocks and block entities) — no
 * scheduler, no world, no I/O, so this is fully unit-testable (see the
 * design doc's "The paste engine": "PastePlan is pure ... This is what
 * makes the hard part unit-testable without a server"). {@link
 * PasteEngine} only ever submits what this class decided.
 *
 * <p>Chunk-bucketing and the FIFO-within-a-chunk property this plan
 * relies on for pass ordering are the same idiom {@code campus-lobby}'s
 * {@code SceneBuilder} already uses (see docs/phqen1x-rpg-suite/07-folia-safety.md's
 * "Chunk-bucketed block placement" and "FIFO ordering within a chunk").
 */
public final class PastePlan {

    public record Batch(List<Placement> placements) {
    }

    public record ChunkPlan(int chunkX, int chunkZ, List<Batch> pass1Batches, List<Batch> pass2Batches) {
    }

    public record WorldMarker(String id, int x, int y, int z, Map<String, Object> meta) {
    }

    public record Result(List<ChunkPlan> chunks, int totalPlacements, List<WorldMarker> markers) {
    }

    private PastePlan() {
    }

    /**
     * @param skipAir if true, air voxels in the grid are never placed
     *                (the structure blends with whatever is already
     *                there); if false, air is placed literally like any
     *                other block, clearing the target volume to match
     *                the schematic's own empty space. Backs {@code /wec
     *                paste --no-air} (which sets this to {@code true}).
     */
    public static Result build(VoxelGrid grid, List<Marker> markers, Transform transform,
                                int originX, int originY, int originZ, int blocksPerTick, boolean skipAir) {
        int width = grid.width();
        int length = grid.length();

        Map<Long, List<Placement>> pass1ByChunk = new LinkedHashMap<>();
        Map<Long, List<Placement>> pass2ByChunk = new LinkedHashMap<>();
        Map<Integer, VoxelGrid.BlockEntity> blockEntities = grid.blockEntities(); // fetched once — see VoxelGrid#blockEntities' copying cost
        int total = 0;

        for (int y = 0; y < grid.height(); y++) {
            for (int z = 0; z < length; z++) {
                for (int x = 0; x < width; x++) {
                    if (skipAir && grid.isAir(x, y, z)) {
                        continue;
                    }
                    BlockStateRef transformedState = transform.applyToState(grid.get(x, y, z));
                    int[] xz = transform.apply(x, z, width, length);
                    int worldX = originX + xz[0];
                    int worldY = originY + y;
                    int worldZ = originZ + xz[1];

                    VoxelGrid.BlockEntity blockEntity = blockEntities.get(grid.index(x, y, z));
                    Placement placement = new Placement(worldX, worldY, worldZ, transformedState, blockEntity);
                    boolean pass2 = blockEntity != null || AttachmentSensitivity.isAttachmentSensitive(transformedState);

                    long chunkKey = chunkKey(worldX >> 4, worldZ >> 4);
                    (pass2 ? pass2ByChunk : pass1ByChunk).computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(placement);
                    total++;
                }
            }
        }

        List<ChunkPlan> chunkPlans = buildChunkPlans(pass1ByChunk, pass2ByChunk, blocksPerTick);
        List<WorldMarker> worldMarkers = translateMarkers(markers, transform, width, length, originX, originY, originZ);
        return new Result(chunkPlans, total, worldMarkers);
    }

    private static List<ChunkPlan> buildChunkPlans(Map<Long, List<Placement>> pass1ByChunk,
                                                     Map<Long, List<Placement>> pass2ByChunk, int blocksPerTick) {
        java.util.Set<Long> allChunkKeys = new java.util.TreeSet<>(); // deterministic order for tests and logs
        allChunkKeys.addAll(pass1ByChunk.keySet());
        allChunkKeys.addAll(pass2ByChunk.keySet());

        List<ChunkPlan> plans = new ArrayList<>();
        for (long key : allChunkKeys) {
            int chunkX = (int) (key >> 32);
            int chunkZ = (int) key;
            List<Batch> pass1 = batch(pass1ByChunk.getOrDefault(key, List.of()), blocksPerTick);
            List<Batch> pass2 = batch(pass2ByChunk.getOrDefault(key, List.of()), blocksPerTick);
            plans.add(new ChunkPlan(chunkX, chunkZ, pass1, pass2));
        }
        plans.sort(Comparator.comparingInt(ChunkPlan::chunkX).thenComparingInt(ChunkPlan::chunkZ));
        return plans;
    }

    private static List<Batch> batch(List<Placement> placements, int blocksPerTick) {
        if (placements.isEmpty()) {
            return List.of();
        }
        int size = Math.max(1, blocksPerTick);
        List<Batch> batches = new ArrayList<>();
        for (int i = 0; i < placements.size(); i += size) {
            batches.add(new Batch(List.copyOf(placements.subList(i, Math.min(i + size, placements.size())))));
        }
        return batches;
    }

    private static List<WorldMarker> translateMarkers(List<Marker> markers, Transform transform, int width, int length,
                                                        int originX, int originY, int originZ) {
        List<WorldMarker> result = new ArrayList<>(markers.size());
        for (Marker marker : markers) {
            int[] pos = marker.position();
            int[] xz = transform.apply(pos[0], pos[2], width, length);
            result.add(new WorldMarker(marker.id(), originX + xz[0], originY + pos[1], originZ + xz[1], marker.meta()));
        }
        return result;
    }

    /** Same encoding {@code campus-lobby}'s {@code SceneBuilder} uses. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return (((long) chunkX) << 32) ^ (chunkZ & 0xffffffffL);
    }
}
