package io.github.phqen1x.worldeditcraft.schem;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import io.github.phqen1x.worldeditcraft.voxel.VoxelPalette;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;

/**
 * Reads a GZip'd Sponge Schematic file back into a {@link VoxelGrid}.
 * Accepts both v2 (block data at the root — {@code Palette}/{@code
 * BlockData}/{@code BlockEntities} directly under {@code Schematic}) and
 * v3 (the same three nested under a {@code Blocks} compound, with the
 * byte array renamed {@code Data}) on read, matching the published
 * Sponge Schematic Specification for each version — this plugin only
 * ever writes v3 ({@link SchematicWriter}), but WorldEdit's own exports
 * (and anything a user imports with {@code /wec import}) may be older.
 */
public final class SchematicReader {

    private SchematicReader() {
    }

    public static Result read(byte[] gzipBytes) {
        NbtTag.CompoundTag root;
        try (GZIPInputStream gzip = new GZIPInputStream(new ByteArrayInputStream(gzipBytes));
             DataInputStream in = new DataInputStream(gzip)) {
            root = NbtReader.readUnnamedRoot(in);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }

        NbtTag schematicTag = root.value().get("Schematic");
        if (!(schematicTag instanceof NbtTag.CompoundTag schematic)) {
            throw new IllegalArgumentException("Not a schematic file: missing root 'Schematic' compound");
        }

        int version = intField(schematic, "Version", 2);
        int dataVersion = intField(schematic, "DataVersion", 0);
        int width = shortFieldUnsigned(schematic, "Width");
        int height = shortFieldUnsigned(schematic, "Height");
        int length = shortFieldUnsigned(schematic, "Length");
        int[] offset = intArrayField(schematic, "Offset", new int[]{0, 0, 0});

        String name = "";
        String author = "";
        long dateEpochMillis = 0;
        if (schematic.value().get("Metadata") instanceof NbtTag.CompoundTag metadata) {
            name = stringField(metadata, "Name", "");
            author = stringField(metadata, "Author", "");
            dateEpochMillis = longField(metadata, "Date", 0);
        }

        NbtTag.CompoundTag blockContainer = version >= 3
                ? requireCompound(schematic, "Blocks")
                : schematic;
        String dataKey = version >= 3 ? "Data" : "BlockData";

        Map<String, NbtTag> paletteTag = requireCompound(blockContainer, "Palette").value();
        List<BlockStateRef> orderedStates = orderPaletteByIndex(paletteTag);
        VoxelPalette palette = VoxelPalette.of(orderedStates);

        byte[] packed = ((NbtTag.ByteArrayTag) blockContainer.value().get(dataKey)).value();
        VoxelGrid grid = new VoxelGrid(width, height, length, palette);
        unpackData(grid, packed);

        if (blockContainer.value().get("BlockEntities") instanceof NbtTag.ListTag blockEntities) {
            for (NbtTag entry : blockEntities.value()) {
                readBlockEntity(grid, (NbtTag.CompoundTag) entry);
            }
        }

        return new Result(grid, offset, name, author, dateEpochMillis, dataVersion, version);
    }

    private static List<BlockStateRef> orderPaletteByIndex(Map<String, NbtTag> paletteTag) {
        BlockStateRef[] byIndex = new BlockStateRef[paletteTag.size()];
        for (Map.Entry<String, NbtTag> entry : paletteTag.entrySet()) {
            int index = ((NbtTag.IntTag) entry.getValue()).value();
            if (index < 0 || index >= byIndex.length) {
                throw new IllegalArgumentException("Palette index " + index + " out of range for a palette of size " + byIndex.length);
            }
            byIndex[index] = BlockStateRef.parse(entry.getKey());
        }
        return List.of(byIndex);
    }

    private static void unpackData(VoxelGrid grid, byte[] packed) {
        VarIntCodec.Cursor cursor = new VarIntCodec.Cursor();
        for (int y = 0; y < grid.height(); y++) {
            for (int z = 0; z < grid.length(); z++) {
                for (int x = 0; x < grid.width(); x++) {
                    int index = VarIntCodec.read(packed, cursor);
                    grid.setPaletteIndex(x, y, z, index);
                }
            }
        }
    }

    private static void readBlockEntity(VoxelGrid grid, NbtTag.CompoundTag entry) {
        int[] pos = ((NbtTag.IntArrayTag) entry.value().get("Pos")).value();
        String id = ((NbtTag.StringTag) entry.value().get("Id")).value();

        Map<String, Object> data;
        NbtTag dataTag = entry.value().get("Data");
        if (dataTag == null) {
            dataTag = entry.value().get("Extra");
        }
        if (dataTag instanceof NbtTag.CompoundTag compound) {
            data = extraFields(compound);
        } else {
            // v2 files may inline extra fields directly alongside Pos/Id
            // rather than nesting them (see the Sponge v2 spec's
            // "same structure as the Minecraft Chunk Format" note).
            data = new LinkedHashMap<>();
            for (Map.Entry<String, NbtTag> field : entry.value().entrySet()) {
                if (!field.getKey().equals("Pos") && !field.getKey().equals("Id")) {
                    data.put(field.getKey(), NbtValueCodec.fromTag(field.getValue()));
                }
            }
        }
        grid.setBlockEntity(pos[0], pos[1], pos[2], id, data);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> extraFields(NbtTag.CompoundTag compound) {
        return (Map<String, Object>) NbtValueCodec.fromTag(compound);
    }

    private static NbtTag.CompoundTag requireCompound(NbtTag.CompoundTag parent, String key) {
        if (!(parent.value().get(key) instanceof NbtTag.CompoundTag compound)) {
            throw new IllegalArgumentException("Missing or malformed '" + key + "' compound");
        }
        return compound;
    }

    private static int intField(NbtTag.CompoundTag compound, String key, int fallback) {
        return compound.value().get(key) instanceof NbtTag.IntTag t ? t.value() : fallback;
    }

    private static long longField(NbtTag.CompoundTag compound, String key, long fallback) {
        return compound.value().get(key) instanceof NbtTag.LongTag t ? t.value() : fallback;
    }

    private static String stringField(NbtTag.CompoundTag compound, String key, String fallback) {
        return compound.value().get(key) instanceof NbtTag.StringTag t ? t.value() : fallback;
    }

    private static int shortFieldUnsigned(NbtTag.CompoundTag compound, String key) {
        if (!(compound.value().get(key) instanceof NbtTag.ShortTag t)) {
            throw new IllegalArgumentException("Missing required field '" + key + "'");
        }
        return t.value() & 0xFFFF;
    }

    private static int[] intArrayField(NbtTag.CompoundTag compound, String key, int[] fallback) {
        return compound.value().get(key) instanceof NbtTag.IntArrayTag t ? t.value() : fallback;
    }

    public record Result(
            VoxelGrid grid,
            int[] offset,
            String name,
            String author,
            long dateEpochMillis,
            int dataVersion,
            int formatVersion
    ) {
        public Result {
            offset = offset.clone();
        }

        @Override
        public int[] offset() {
            return offset.clone();
        }
    }
}
