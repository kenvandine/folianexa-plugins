package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.dsl.Marker;
import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.Transform;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import io.github.phqen1x.worldeditcraft.voxel.VoxelPalette;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PastePlanTest {

    @Test
    void bucketingAssignsEachPlacementToTheRightChunkKey() {
        // A grid spanning two chunks along X: x=0..15 in chunk 0, x=16..17 in chunk 1.
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(18, 1, 1, palette);
        BlockStateRef stone = BlockStateRef.parse("minecraft:stone");
        for (int x = 0; x < 18; x++) {
            grid.set(x, 0, 0, stone);
        }

        PastePlan.Result result = PastePlan.build(grid, List.of(), Transform.IDENTITY, 0, 64, 0, 2048, true);

        assertEquals(2, result.chunks().size());
        assertEquals(0, result.chunks().get(0).chunkX());
        assertEquals(1, result.chunks().get(1).chunkX());

        int firstChunkPlacements = result.chunks().get(0).pass1Batches().stream().mapToInt(b -> b.placements().size()).sum();
        int secondChunkPlacements = result.chunks().get(1).pass1Batches().stream().mapToInt(b -> b.placements().size()).sum();
        assertEquals(16, firstChunkPlacements);
        assertEquals(2, secondChunkPlacements);
    }

    @Test
    void batchesRespectTheBlocksPerTickBudget() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(10, 1, 1, palette);
        BlockStateRef stone = BlockStateRef.parse("minecraft:stone");
        for (int x = 0; x < 10; x++) {
            grid.set(x, 0, 0, stone);
        }

        PastePlan.Result result = PastePlan.build(grid, List.of(), Transform.IDENTITY, 0, 0, 0, 3, true);

        List<PastePlan.Batch> batches = result.chunks().get(0).pass1Batches();
        assertEquals(4, batches.size()); // 10 placements / 3 per batch = 4 batches (3,3,3,1)
        for (int i = 0; i < 3; i++) {
            assertTrue(batches.get(i).placements().size() <= 3);
        }
        assertEquals(10, batches.stream().mapToInt(b -> b.placements().size()).sum());
    }

    @Test
    void pass2ContainsExactlyTheAttachmentSensitiveBlocksAndBlockEntities() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(4, 1, 1, palette);
        grid.set(0, 0, 0, BlockStateRef.parse("minecraft:stone"));       // pass 1
        grid.set(1, 0, 0, BlockStateRef.parse("minecraft:torch"));      // pass 2 (attachment-sensitive)
        grid.set(2, 0, 0, BlockStateRef.parse("minecraft:oak_planks")); // pass 1
        grid.set(3, 0, 0, BlockStateRef.parse("minecraft:chest"));      // pass 1 block, but pass 2 because it's a block entity
        grid.setBlockEntity(3, 0, 0, "minecraft:chest", Map.of());

        PastePlan.Result result = PastePlan.build(grid, List.of(), Transform.IDENTITY, 0, 0, 0, 2048, true);
        PastePlan.ChunkPlan chunk = result.chunks().get(0);

        List<Integer> pass1Xs = chunk.pass1Batches().stream().flatMap(b -> b.placements().stream()).map(Placement::x).toList();
        List<Integer> pass2Xs = chunk.pass2Batches().stream().flatMap(b -> b.placements().stream()).map(Placement::x).toList();

        assertEquals(List.of(0, 2), pass1Xs);
        assertEquals(List.of(1, 3), pass2Xs);
    }

    @Test
    void transformAndOriginComposeCorrectly() {
        // A 3x1x1 line at y=5, rotated 90 degrees and offset by an origin.
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(3, 1, 1, palette);
        BlockStateRef stone = BlockStateRef.parse("minecraft:stone");
        grid.set(0, 0, 0, stone);
        grid.set(1, 0, 0, stone);
        grid.set(2, 0, 0, stone);

        Transform rot90 = new Transform(90, Transform.Flip.NONE);
        PastePlan.Result result = PastePlan.build(grid, List.of(), rot90, 100, 64, 200, 2048, true);

        List<Placement> placements = result.chunks().stream()
                .flatMap(c -> c.pass1Batches().stream())
                .flatMap(b -> b.placements().stream())
                .toList();
        assertEquals(3, placements.size());

        // width=3, length=1: rotate90 maps (x, 0) -> ((1-1)-0, x) = (0, x). So world Z should vary with local X, world X should be constant.
        for (Placement p : placements) {
            assertEquals(100, p.x());
            assertEquals(64, p.y());
        }
        List<Integer> worldZs = placements.stream().map(Placement::z).sorted().toList();
        assertEquals(List.of(200, 201, 202), worldZs);
    }

    @Test
    void skipAirTrueOmitsAirVoxelsEntirely() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(2, 1, 1, palette);
        grid.set(0, 0, 0, BlockStateRef.parse("minecraft:stone"));
        // (1,0,0) stays air

        PastePlan.Result skipping = PastePlan.build(grid, List.of(), Transform.IDENTITY, 0, 0, 0, 2048, true);
        assertEquals(1, skipping.totalPlacements());

        PastePlan.Result including = PastePlan.build(grid, List.of(), Transform.IDENTITY, 0, 0, 0, 2048, false);
        assertEquals(2, including.totalPlacements());
    }

    @Test
    void markersAreTranslatedTheSameWayAsBlocks() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(3, 2, 3, palette);
        Marker marker = new Marker("entrance", new int[]{1, 1, 2}, Map.of("note", "front door"));

        Transform rot90 = new Transform(90, Transform.Flip.NONE);
        PastePlan.Result result = PastePlan.build(grid, List.of(marker), rot90, 10, 20, 30, 2048, true);

        assertEquals(1, result.markers().size());
        PastePlan.WorldMarker worldMarker = result.markers().get(0);
        assertEquals("entrance", worldMarker.id());
        assertEquals("front door", worldMarker.meta().get("note"));
        // width=3, length=3: rotate90 maps (1,2) -> ((3-1)-2, 1) = (0, 1).
        assertEquals(10 + 0, worldMarker.x());
        assertEquals(20 + 1, worldMarker.y());
        assertEquals(30 + 1, worldMarker.z());
    }

    @Test
    void emptyGridProducesNoChunksAndNoPlacements() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(2, 2, 2, palette); // all air by default

        PastePlan.Result result = PastePlan.build(grid, List.of(), Transform.IDENTITY, 0, 0, 0, 2048, true);

        assertEquals(0, result.totalPlacements());
        assertTrue(result.chunks().isEmpty());
    }

    @Test
    void chunkKeyRoundTripsNegativeCoordinates() {
        long key = PastePlan.chunkKey(-3, 7);
        assertEquals(-3, (int) (key >> 32));
        assertEquals(7, (int) key);
    }
}
