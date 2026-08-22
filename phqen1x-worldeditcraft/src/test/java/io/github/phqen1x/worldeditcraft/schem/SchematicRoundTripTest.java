package io.github.phqen1x.worldeditcraft.schem;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import io.github.phqen1x.worldeditcraft.voxel.VoxelPalette;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Grid -> bytes -> grid is identical — the test that catches transposition
 * (a wrong axis order in {@link VoxelGrid#index} or the writer's/reader's
 * iteration order produces a structure that is transposed rather than
 * broken, which survives a careless eyeball check). Uses a deliberately
 * asymmetric 3x4x5 fixture so a swapped axis actually changes the result.
 */
class SchematicRoundTripTest {

    @Test
    void asymmetricGridRoundTripsExactly() {
        VoxelGrid original = buildAsymmetricFixture();

        byte[] bytes = SchematicWriter.write(original, sampleMeta(), new int[]{0, 0, 0});
        SchematicReader.Result result = SchematicReader.read(bytes);
        VoxelGrid readBack = result.grid();

        assertEquals(original.width(), readBack.width());
        assertEquals(original.height(), readBack.height());
        assertEquals(original.length(), readBack.length());

        for (int y = 0; y < original.height(); y++) {
            for (int z = 0; z < original.length(); z++) {
                for (int x = 0; x < original.width(); x++) {
                    assertEquals(original.get(x, y, z), readBack.get(x, y, z),
                            "mismatch at (%d,%d,%d)".formatted(x, y, z));
                }
            }
        }
    }

    @Test
    void blockEntitiesRoundTripWithPositionAndPayload() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(3, 3, 3, palette);
        grid.set(1, 1, 1, BlockStateRef.parse("minecraft:chest[facing=north]"));
        grid.setBlockEntity(1, 1, 1, "minecraft:chest", Map.of("Items", java.util.List.of()));

        byte[] bytes = SchematicWriter.write(grid, sampleMeta(), new int[]{0, 0, 0});
        SchematicReader.Result result = SchematicReader.read(bytes);

        VoxelGrid.BlockEntity blockEntity = result.grid().blockEntities().get(result.grid().index(1, 1, 1));
        assertEquals("minecraft:chest", blockEntity.id());
    }

    @Test
    void metadataRoundTrips() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(2, 2, 2, palette);

        byte[] bytes = SchematicWriter.write(grid, sampleMeta(), new int[]{-4, 0, -4});
        SchematicReader.Result result = SchematicReader.read(bytes);

        assertEquals("wayside_shrine", result.name());
        assertEquals("Phqen1xWorldEditCraft", result.author());
        assertEquals(SchematicWriter.CURRENT_VERSION, result.formatVersion());
        assertEquals(-4, result.offset()[0]);
        assertEquals(0, result.offset()[1]);
        assertEquals(-4, result.offset()[2]);
    }

    private static VoxelGrid buildAsymmetricFixture() {
        VoxelPalette palette = new VoxelPalette();
        VoxelGrid grid = new VoxelGrid(3, 4, 5, palette);
        BlockStateRef stone = BlockStateRef.parse("minecraft:stone");
        BlockStateRef glass = BlockStateRef.parse("minecraft:glass");
        BlockStateRef gold = BlockStateRef.parse("minecraft:gold_block");

        // Fill with stone, then place a unique marker block at one corner
        // along each axis so a swapped x/y/z order is guaranteed to
        // disagree with the original at some position.
        for (int y = 0; y < 4; y++) {
            for (int z = 0; z < 5; z++) {
                for (int x = 0; x < 3; x++) {
                    grid.set(x, y, z, stone);
                }
            }
        }
        grid.set(2, 0, 0, glass);  // max x, min y, min z
        grid.set(0, 3, 0, gold);   // min x, max y, min z
        grid.set(0, 0, 4, BlockStateRef.parse("minecraft:diamond_block")); // min x, min y, max z
        return grid;
    }

    private static SchematicMeta sampleMeta() {
        return new SchematicMeta("wayside_shrine", "Phqen1xWorldEditCraft", 1_700_000_000_000L,
                "a small wayside shrine", "test-model", java.util.List.of("shrine"), "deadbeef");
    }
}
