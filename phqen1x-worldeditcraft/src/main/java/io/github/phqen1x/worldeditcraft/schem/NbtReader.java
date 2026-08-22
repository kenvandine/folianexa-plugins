package io.github.phqen1x.worldeditcraft.schem;

import java.io.DataInput;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Reads an {@link NbtTag} tree back from a {@link DataInput}, the inverse
 * of {@link NbtWriter}. Guards against a corrupt or hostile file turning
 * into a stack overflow or an out-of-memory allocation: nesting past
 * {@link #MAX_DEPTH} or a single array/list/compound claiming more than
 * {@link #MAX_ALLOCATION} elements/bytes fails cleanly instead.
 */
final class NbtReader {

    private static final int MAX_DEPTH = 512;
    private static final int MAX_ALLOCATION = 64 * 1024 * 1024;

    private NbtReader() {
    }

    /** Reads the unnamed root compound written by {@link NbtWriter#writeUnnamedRoot}. */
    static NbtTag.CompoundTag readUnnamedRoot(DataInput in) throws IOException {
        byte typeId = in.readByte();
        if (typeId != NbtTag.COMPOUND) {
            throw new IOException("Expected a root compound tag, found type " + typeId);
        }
        readModifiedUtf8(in); // root name, discarded
        return readCompoundPayload(in, 0);
    }

    private static NbtTag readPayload(DataInput in, byte typeId, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting exceeds max depth " + MAX_DEPTH);
        }
        return switch (typeId) {
            case NbtTag.BYTE -> new NbtTag.ByteTag(in.readByte());
            case NbtTag.SHORT -> new NbtTag.ShortTag(in.readShort());
            case NbtTag.INT -> new NbtTag.IntTag(in.readInt());
            case NbtTag.LONG -> new NbtTag.LongTag(in.readLong());
            case NbtTag.FLOAT -> new NbtTag.FloatTag(in.readFloat());
            case NbtTag.DOUBLE -> new NbtTag.DoubleTag(in.readDouble());
            case NbtTag.BYTE_ARRAY -> {
                int length = readCappedLength(in);
                byte[] bytes = new byte[length];
                in.readFully(bytes);
                yield new NbtTag.ByteArrayTag(bytes);
            }
            case NbtTag.STRING -> new NbtTag.StringTag(readModifiedUtf8(in));
            case NbtTag.LIST -> {
                byte elementType = in.readByte();
                int length = readCappedLength(in);
                List<NbtTag> elements = new ArrayList<>(Math.min(length, 1024));
                for (int i = 0; i < length; i++) {
                    elements.add(readPayload(in, elementType, depth + 1));
                }
                yield new NbtTag.ListTag(elementType, elements);
            }
            case NbtTag.COMPOUND -> readCompoundPayload(in, depth + 1);
            case NbtTag.INT_ARRAY -> {
                int length = readCappedLength(in);
                int[] values = new int[length];
                for (int i = 0; i < length; i++) {
                    values[i] = in.readInt();
                }
                yield new NbtTag.IntArrayTag(values);
            }
            case NbtTag.LONG_ARRAY -> {
                int length = readCappedLength(in);
                long[] values = new long[length];
                for (int i = 0; i < length; i++) {
                    values[i] = in.readLong();
                }
                yield new NbtTag.LongArrayTag(values);
            }
            default -> throw new IOException("Unknown NBT tag type " + typeId);
        };
    }

    private static NbtTag.CompoundTag readCompoundPayload(DataInput in, int depth) throws IOException {
        if (depth > MAX_DEPTH) {
            throw new IOException("NBT nesting exceeds max depth " + MAX_DEPTH);
        }
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        while (true) {
            byte typeId = in.readByte();
            if (typeId == NbtTag.END) {
                return new NbtTag.CompoundTag(entries);
            }
            String name = readModifiedUtf8(in);
            entries.put(name, readPayload(in, typeId, depth + 1));
        }
    }

    private static int readCappedLength(DataInput in) throws IOException {
        int length = in.readInt();
        if (length < 0 || length > MAX_ALLOCATION) {
            throw new IOException("NBT array/list length out of bounds: " + length);
        }
        return length;
    }

    private static String readModifiedUtf8(DataInput in) throws IOException {
        return in.readUTF();
    }
}
