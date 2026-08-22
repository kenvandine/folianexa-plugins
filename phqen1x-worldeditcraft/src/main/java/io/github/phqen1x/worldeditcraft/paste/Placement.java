package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;

/**
 * One block to place, already translated into world coordinates. {@code
 * blockEntity} is non-null only for voxels that came from a {@code
 * block_entity} op — {@link PasteEngine} best-effort-applies it after
 * placing {@code state} (see the plugin README's known limitations for
 * exactly what "best-effort" covers today: sign text, not arbitrary NBT).
 */
public record Placement(int x, int y, int z, BlockStateRef state, VoxelGrid.BlockEntity blockEntity) {
}
