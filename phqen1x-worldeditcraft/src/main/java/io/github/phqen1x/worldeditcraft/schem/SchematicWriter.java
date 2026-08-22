package io.github.phqen1x.worldeditcraft.schem;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/**
 * Writes a {@link VoxelGrid} out as a real, GZip-compressed Sponge
 * Schematic v3 file (the standard format WorldEdit/FAWE read and write —
 * see the design doc's "Why not just depend on WorldEdit?" for why this
 * plugin implements the format itself rather than depending on either).
 * Always writes v3; {@link SchematicReader} additionally reads v2.
 */
public final class SchematicWriter {

    /**
     * Minecraft 1.21.4's data version — the engine version this cluster
     * targets (see build.gradle.kts's {@code paper-api} coordinate).
     * WorldEdit uses {@code DataVersion} to know whether a schematic's
     * block states need remapping for the server it's loaded on.
     */
    private static final int DATA_VERSION_1_21_4 = 4189;
    static final int CURRENT_VERSION = 3;

    private SchematicWriter() {
    }

    public static byte[] write(VoxelGrid grid, SchematicMeta meta, int[] offset) {
        NbtTag.CompoundTag root = buildRoot(grid, meta, offset);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (GZIPOutputStream gzip = new GZIPOutputStream(bytes);
             DataOutputStream out = new DataOutputStream(gzip)) {
            NbtWriter.writeUnnamedRoot(out, root);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    private static NbtTag.CompoundTag buildRoot(VoxelGrid grid, SchematicMeta meta, int[] offset) {
        Map<String, NbtTag> schematic = new LinkedHashMap<>();
        schematic.put("Version", new NbtTag.IntTag(CURRENT_VERSION));
        schematic.put("DataVersion", new NbtTag.IntTag(DATA_VERSION_1_21_4));
        schematic.put("Metadata", buildMetadata(meta));
        schematic.put("Width", new NbtTag.ShortTag((short) grid.width()));
        schematic.put("Height", new NbtTag.ShortTag((short) grid.height()));
        schematic.put("Length", new NbtTag.ShortTag((short) grid.length()));
        schematic.put("Offset", new NbtTag.IntArrayTag(offset));
        schematic.put("Blocks", buildBlocks(grid));

        Map<String, NbtTag> root = new LinkedHashMap<>();
        root.put("Schematic", new NbtTag.CompoundTag(schematic));
        return new NbtTag.CompoundTag(root);
    }

    private static NbtTag.CompoundTag buildMetadata(SchematicMeta meta) {
        Map<String, NbtTag> metadata = new LinkedHashMap<>();
        metadata.put("Name", new NbtTag.StringTag(meta.name()));
        metadata.put("Author", new NbtTag.StringTag(meta.author()));
        metadata.put("Date", new NbtTag.LongTag(meta.dateEpochMillis()));
        return new NbtTag.CompoundTag(metadata);
    }

    private static NbtTag.CompoundTag buildBlocks(VoxelGrid grid) {
        Map<String, NbtTag> blocks = new LinkedHashMap<>();
        blocks.put("Palette", buildPalette(grid));
        blocks.put("Data", new NbtTag.ByteArrayTag(packData(grid)));
        blocks.put("BlockEntities", buildBlockEntities(grid));
        return new NbtTag.CompoundTag(blocks);
    }

    private static NbtTag.CompoundTag buildPalette(VoxelGrid grid) {
        Map<String, NbtTag> palette = new LinkedHashMap<>();
        List<BlockStateRef> states = grid.palette().statesInOrder();
        for (int i = 0; i < states.size(); i++) {
            palette.put(states.get(i).toString(), new NbtTag.IntTag(i));
        }
        return new NbtTag.CompoundTag(palette);
    }

    private static byte[] packData(VoxelGrid grid) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        for (int y = 0; y < grid.height(); y++) {
            for (int z = 0; z < grid.length(); z++) {
                for (int x = 0; x < grid.width(); x++) {
                    VarIntCodec.write(out, grid.paletteIndex(x, y, z));
                }
            }
        }
        return out.toByteArray();
    }

    private static NbtTag.ListTag buildBlockEntities(VoxelGrid grid) {
        List<NbtTag> entries = new java.util.ArrayList<>();
        for (Map.Entry<Integer, VoxelGrid.BlockEntity> entry : grid.blockEntities().entrySet()) {
            int[] pos = grid.positionOf(entry.getKey());
            VoxelGrid.BlockEntity blockEntity = entry.getValue();

            Map<String, NbtTag> compound = new LinkedHashMap<>();
            compound.put("Pos", new NbtTag.IntArrayTag(pos));
            compound.put("Id", new NbtTag.StringTag(blockEntity.id()));
            compound.put("Data", (NbtTag.CompoundTag) NbtValueCodec.toTag(blockEntity.data()));
            entries.add(new NbtTag.CompoundTag(compound));
        }
        byte elementType = entries.isEmpty() ? NbtTag.END : NbtTag.COMPOUND;
        return new NbtTag.ListTag(elementType, entries);
    }
}
