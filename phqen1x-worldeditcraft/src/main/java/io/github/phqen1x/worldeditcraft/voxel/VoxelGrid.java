package io.github.phqen1x.worldeditcraft.voxel;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A dense {@code width x height x length} grid of palette indices in YZX
 * order — {@code y * width * length + z * width + x}, x varying fastest —
 * matching the Sponge v3 {@code .schem} format's {@code Blocks.Data}
 * layout exactly, so {@link io.github.phqen1x.worldeditcraft.schem.SchematicWriter}
 * can write the array with no reordering pass. A freshly-allocated grid
 * is all zeros, which {@link VoxelPalette}'s reserved index 0 (air) makes
 * mean "entirely air" for free.
 *
 * <p>Block entities (chests, signs, spawners, banners — anything with an
 * NBT payload beyond its block state) are kept in a sparse map keyed by
 * the same linear index, since the overwhelming majority of voxels never
 * have one.
 */
public final class VoxelGrid {

    private final int width;
    private final int height;
    private final int length;
    private final short[] data;
    private final VoxelPalette palette;
    private final Map<Integer, BlockEntity> blockEntities = new LinkedHashMap<>();

    public VoxelGrid(int width, int height, int length, VoxelPalette palette) {
        if (width <= 0 || height <= 0 || length <= 0) {
            throw new IllegalArgumentException("Grid dimensions must be positive: " + width + "x" + height + "x" + length);
        }
        long volume = (long) width * height * length;
        if (volume > Integer.MAX_VALUE - 8) {
            throw new IllegalArgumentException("Grid volume too large: " + volume);
        }
        this.width = width;
        this.height = height;
        this.length = length;
        this.data = new short[(int) volume];
        this.palette = palette;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    public int length() {
        return length;
    }

    public VoxelPalette palette() {
        return palette;
    }

    public int index(int x, int y, int z) {
        return y * width * length + z * width + x;
    }

    public boolean inBounds(int x, int y, int z) {
        return x >= 0 && x < width && y >= 0 && y < height && z >= 0 && z < length;
    }

    public void set(int x, int y, int z, BlockStateRef state) {
        data[index(x, y, z)] = palette.intern(state);
        if (!state.id().equals("air")) {
            // A later plain set() over a former block-entity position (e.g.
            // an op overwriting a chest with stone) must drop the stale
            // payload rather than leave orphaned NBT pointing at a block
            // that no longer matches it.
            blockEntities.remove(index(x, y, z));
        }
    }

    public BlockStateRef get(int x, int y, int z) {
        return palette.stateAt(data[index(x, y, z)] & 0xFFFF);
    }

    public boolean isAir(int x, int y, int z) {
        return (data[index(x, y, z)] & 0xFFFF) == palette.airIndex();
    }

    public void setBlockEntity(int x, int y, int z, String id, Map<String, Object> nbtData) {
        blockEntities.put(index(x, y, z), new BlockEntity(id, Map.copyOf(nbtData)));
    }

    public Map<Integer, BlockEntity> blockEntities() {
        return Map.copyOf(blockEntities);
    }

    /** The raw palette index at a position — what a schematic writer packs into {@code Blocks.Data}. */
    public int paletteIndex(int x, int y, int z) {
        return data[index(x, y, z)] & 0xFFFF;
    }

    /**
     * Writes a raw palette index directly, bypassing {@link VoxelPalette#intern} —
     * for a schematic reader unpacking {@code Blocks.Data}, where the file's own
     * palette already fixes each index's meaning.
     */
    public void setPaletteIndex(int x, int y, int z, int paletteIndex) {
        data[index(x, y, z)] = (short) paletteIndex;
    }

    /** Inverts {@link #index(int, int, int)}: the {@code [x, y, z]} a linear index refers to. */
    public int[] positionOf(int linearIndex) {
        int plane = width * length;
        int y = linearIndex / plane;
        int rem = linearIndex % plane;
        int z = rem / width;
        int x = rem % width;
        return new int[]{x, y, z};
    }

    /** A block entity's id (e.g. {@code minecraft:chest}) and its NBT payload, keyed by voxel index. */
    public record BlockEntity(String id, Map<String, Object> data) {
    }
}
