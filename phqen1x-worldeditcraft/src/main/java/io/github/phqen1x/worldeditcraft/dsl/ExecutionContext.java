package io.github.phqen1x.worldeditcraft.dsl;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Everything one {@link OpRegistry} executor needs to rasterize its op:
 * the grid it writes into, the script's palette (key -> literal
 * block-state string, resolved and cached), and the running list of
 * markers ops append to. Shared per-voxel modifiers ({@code
 * replace_only}, {@code skip_air}, {@code chance}) are centralized in
 * {@link #tryWrite} so every op gets them for free rather than
 * re-implementing the same three checks.
 */
public final class ExecutionContext {

    private final VoxelGrid grid;
    private final Map<String, String> palette;
    private final Map<String, BlockStateRef> resolvedCache = new HashMap<>();
    private final List<Marker> markers = new ArrayList<>();
    private final long scriptSeed;

    public ExecutionContext(VoxelGrid grid, Map<String, String> palette, long scriptSeed) {
        this.grid = grid;
        this.palette = palette;
        this.scriptSeed = scriptSeed;
    }

    public VoxelGrid grid() {
        return grid;
    }

    /** A palette key (e.g. {@code "wall"}) or a literal block-state string, either way resolved to a {@link BlockStateRef}. */
    public BlockStateRef resolveBlock(String blockFieldValue) {
        return resolvedCache.computeIfAbsent(blockFieldValue, value -> {
            String literal = palette.getOrDefault(value, value);
            return BlockStateRef.parse(literal);
        });
    }

    public boolean paletteHasKey(String key) {
        return palette.containsKey(key);
    }

    public void addMarker(String id, int[] position, Map<String, Object> meta) {
        markers.add(new Marker(id, position, meta));
    }

    public List<Marker> markers() {
        return List.copyOf(markers);
    }

    /** A {@link Random} seeded per {@code (opIndex, seedOffset)}, per the design doc's determinism rule. */
    public Random randomFor(int opIndex, int seedOffset) {
        return new Random(scriptSeed ^ opIndex ^ seedOffset);
    }

    /**
     * Writes {@code state} at {@code (x, y, z)} after applying this op's
     * shared modifiers, in the order the design doc lists them: {@code
     * replace_only} (only write where this block already is), {@code
     * skip_air} (only write where currently air), then {@code chance}
     * (probabilistic skip, drawn from {@code rng}). Out-of-bounds
     * coordinates are silently ignored — bounds clamping/reporting is
     * {@link BuildScriptValidator}'s job, not the interpreter's.
     */
    public void tryWrite(BuildOp op, int x, int y, int z, BlockStateRef state, Random rng) {
        if (!grid.inBounds(x, y, z)) {
            return;
        }
        String replaceOnly = op.replaceOnly();
        if (replaceOnly != null && !grid.get(x, y, z).equals(resolveBlock(replaceOnly))) {
            return;
        }
        if (op.skipAir() && !grid.isAir(x, y, z)) {
            return;
        }
        Double chance = op.chance();
        if (chance != null && rng.nextDouble() >= chance) {
            return;
        }
        grid.set(x, y, z, state);
    }
}
