package io.github.phqen1x.worldeditcraft.voxel;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A bidirectional, insertion-ordered map between {@link BlockStateRef}
 * and its palette index. Index 0 is always air ({@code minecraft:air}),
 * matching {@link VoxelGrid}'s convention that a freshly-allocated grid
 * (all-zero {@code short[]}) is entirely air with no extra bookkeeping.
 */
public final class VoxelPalette {

    private final Map<BlockStateRef, Short> indexByState = new LinkedHashMap<>();
    private final List<BlockStateRef> stateByIndex = new ArrayList<>();

    public VoxelPalette() {
        internIndex(BlockStateRef.parse("minecraft:air"));
    }

    private VoxelPalette(List<BlockStateRef> orderedStates) {
        for (BlockStateRef state : orderedStates) {
            internIndex(state);
        }
    }

    /**
     * Builds a palette from an explicit, already-indexed list of states —
     * used when reading a {@code .schem} file back, where the file itself
     * dictates which index each block state occupies, rather than
     * discovering states through {@link #intern}.
     */
    public static VoxelPalette of(List<BlockStateRef> orderedStates) {
        return new VoxelPalette(orderedStates);
    }

    /** Returns the existing index for {@code state}, or allocates a new one. */
    public short intern(BlockStateRef state) {
        return internIndex(state);
    }

    private short internIndex(BlockStateRef state) {
        Short existing = indexByState.get(state);
        if (existing != null) {
            return existing;
        }
        if (stateByIndex.size() >= Short.MAX_VALUE) {
            throw new IllegalStateException("Palette exceeds " + Short.MAX_VALUE + " distinct block states");
        }
        short index = (short) stateByIndex.size();
        indexByState.put(state, index);
        stateByIndex.add(state);
        return index;
    }

    public BlockStateRef stateAt(int index) {
        if (index < 0 || index >= stateByIndex.size()) {
            throw new IndexOutOfBoundsException("No palette entry at index " + index);
        }
        return stateByIndex.get(index);
    }

    /** The index that resolves to {@code minecraft:air}, or {@code -1} if this palette has none. */
    public short airIndex() {
        Short index = indexByState.get(BlockStateRef.parse("minecraft:air"));
        return index == null ? -1 : index;
    }

    public int size() {
        return stateByIndex.size();
    }

    /** Insertion order, index 0 first — the order {@link #intern} allocated them in. */
    public List<BlockStateRef> statesInOrder() {
        return List.copyOf(stateByIndex);
    }
}
