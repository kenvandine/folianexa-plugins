package io.github.phqen1x.worldeditcraft.dsl;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BuildScriptInterpreterTest {

    private static final BuildScriptValidator.GenerationLimits LIMITS =
            new BuildScriptValidator.GenerationLimits(128, 400_000, 400, "vanilla", List.of());

    @Test
    void boxProducesExpectedVoxels() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("from", List.of(1.0, 1.0, 1.0));
        fields.put("to", List.of(2.0, 1.0, 2.0));
        fields.put("block", "stone");
        BuildOp op = new BuildOp("box", fields, 0);

        VoxelGrid grid = interpret(4, 4, 4, Map.of("stone", "minecraft:stone"), 1L, op).grid();

        BlockStateRef stone = BlockStateRef.parse("minecraft:stone");
        BlockStateRef air = BlockStateRef.parse("minecraft:air");
        assertEquals(stone, grid.get(1, 1, 1));
        assertEquals(stone, grid.get(2, 1, 2));
        assertEquals(air, grid.get(0, 0, 0));
        assertEquals(air, grid.get(3, 3, 3));
    }

    @Test
    void hollowBoxLeavesInteriorEmpty() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("from", List.of(0.0, 0.0, 0.0));
        fields.put("to", List.of(4.0, 4.0, 4.0));
        fields.put("block", "wall");
        fields.put("thickness", 1.0);
        BuildOp op = new BuildOp("hollow_box", fields, 0);

        VoxelGrid grid = interpret(5, 5, 5, Map.of("wall", "minecraft:stone"), 1L, op).grid();

        assertFalse(grid.isAir(0, 0, 0)); // corner is shell
        assertTrue(grid.isAir(2, 2, 2)); // center is hollow
    }

    @Test
    void markerRecordsPositionButPlacesNoBlock() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", List.of(1.0, 1.0, 1.0));
        fields.put("id", "entrance");
        BuildOp op = new BuildOp("marker", fields, 0);

        BuildScriptInterpreter.Result result = interpret(3, 3, 3, Map.of(), 1L, op);

        assertTrue(result.grid().isAir(1, 1, 1));
        assertEquals(1, result.markers().size());
        assertEquals("entrance", result.markers().get(0).id());
        assertEquals(1, result.markers().get(0).position()[0]);
    }

    @Test
    void repeatAppliesOpMultipleTimesAtOffsetPositions() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("at", List.of(0.0, 0.0, 0.0));
        fields.put("block", "stone");
        fields.put("repeat", Map.of("count", 3.0, "step", List.of(2.0, 0.0, 0.0)));
        BuildOp op = new BuildOp("place_block", fields, 0);

        VoxelGrid grid = interpret(10, 1, 1, Map.of("stone", "minecraft:stone"), 1L, op).grid();

        assertFalse(grid.isAir(0, 0, 0));
        assertFalse(grid.isAir(2, 0, 0));
        assertFalse(grid.isAir(4, 0, 0));
        assertTrue(grid.isAir(1, 0, 0));
        assertTrue(grid.isAir(6, 0, 0));
    }

    @Test
    void scatterWithFixedSeedIsReproducibleAcrossRuns() {
        Map<String, Object> fields = new LinkedHashMap<>();
        fields.put("region", List.of(List.of(0.0, 0.0, 0.0), List.of(9.0, 0.0, 9.0)));
        fields.put("block", "stone");
        fields.put("density", 0.3);
        BuildOp op = new BuildOp("scatter", fields, 3);

        VoxelGrid first = interpret(10, 1, 10, Map.of("stone", "minecraft:stone"), 8471132L, op).grid();
        VoxelGrid second = interpret(10, 1, 10, Map.of("stone", "minecraft:stone"), 8471132L, op).grid();

        for (int x = 0; x < 10; x++) {
            for (int z = 0; z < 10; z++) {
                assertEquals(first.isAir(x, 0, z), second.isAir(x, 0, z),
                        "mismatch at (%d,0,%d)".formatted(x, z));
            }
        }
        long placed = countNonAir(first);
        assertTrue(placed > 0 && placed < 100, "expected a sparse but non-empty scatter, got " + placed);
    }

    @Test
    void carveRemovesBlocksProbabilistically() {
        Map<String, Object> fillFields = Map.of("block", "stone");
        BuildOp fill = new BuildOp("fill", fillFields, 0);

        Map<String, Object> carveFields = new LinkedHashMap<>();
        carveFields.put("region", List.of(List.of(0.0, 0.0, 0.0), List.of(4.0, 0.0, 4.0)));
        carveFields.put("chance", 1.0); // carve everything, deterministically
        BuildOp carve = new BuildOp("carve", carveFields, 1);

        VoxelGrid grid = interpret(5, 1, 5, Map.of("stone", "minecraft:stone"), 1L, fill, carve).grid();

        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < 5; z++) {
                assertTrue(grid.isAir(x, 0, z), "expected (%d,0,%d) to be carved to air".formatted(x, z));
            }
        }
    }

    @Test
    void noiseReplaceOnlyTouchesMatchingBlocks() {
        BuildOp fill = new BuildOp("fill", Map.of("block", "wall"), 0);

        Map<String, Object> replaceFields = new LinkedHashMap<>();
        replaceFields.put("region", List.of(List.of(0.0, 0.0, 0.0), List.of(3.0, 0.0, 3.0)));
        replaceFields.put("find", "wall");
        replaceFields.put("block", "mossy");
        replaceFields.put("chance", 1.0);
        BuildOp replace = new BuildOp("noise_replace", replaceFields, 1);

        Map<String, String> palette = Map.of("wall", "minecraft:stone_bricks", "mossy", "minecraft:mossy_stone_bricks");
        VoxelGrid grid = interpret(4, 1, 4, palette, 1L, fill, replace).grid();

        BlockStateRef mossy = BlockStateRef.parse("minecraft:mossy_stone_bricks");
        assertEquals(mossy, grid.get(1, 0, 1));
    }

    private static long countNonAir(VoxelGrid grid) {
        long count = 0;
        for (int y = 0; y < grid.height(); y++) {
            for (int z = 0; z < grid.length(); z++) {
                for (int x = 0; x < grid.width(); x++) {
                    if (!grid.isAir(x, y, z)) {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    private static BuildScriptInterpreter.Result interpret(int w, int h, int l, Map<String, String> palette, long seed, BuildOp... ops) {
        BuildScript script = new BuildScript("t", new int[]{w, h, l}, seed, palette, List.of(ops), "");
        BuildScriptValidator.ValidationResult validated = BuildScriptValidator.validate(script, LIMITS);
        return BuildScriptInterpreter.interpret(validated, palette, seed);
    }
}
