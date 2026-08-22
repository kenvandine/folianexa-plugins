package io.github.phqen1x.worldeditcraft.schem;

import java.io.DataOutput;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * Writes an {@link NbtTag} tree to a {@link DataOutput} in big-endian,
 * uncompressed NBT — the same binary layout {@link SchematicWriter} then
 * GZips. Named tags (the wire format everywhere except inside a list,
 * where element tags are unnamed) get a type byte, a UTF-length-prefixed
 * name, then the payload; list elements skip the type byte and name since
 * the list header already carries the element type.
 */
final class NbtWriter {

    private NbtWriter() {
    }

    static void writeNamed(DataOutput out, String name, NbtTag tag) throws IOException {
        out.writeByte(NbtTag.typeIdOf(tag));
        writeModifiedUtf8(out, name);
        writePayload(out, tag);
    }

    /** Writes the unnamed root compound, per the Sponge v3 layout. */
    static void writeUnnamedRoot(DataOutput out, NbtTag.CompoundTag root) throws IOException {
        out.writeByte(NbtTag.COMPOUND);
        writeModifiedUtf8(out, "");
        writePayload(out, root);
    }

    private static void writePayload(DataOutput out, NbtTag tag) throws IOException {
        switch (tag) {
            case NbtTag.ByteTag t -> out.writeByte(t.value());
            case NbtTag.ShortTag t -> out.writeShort(t.value());
            case NbtTag.IntTag t -> out.writeInt(t.value());
            case NbtTag.LongTag t -> out.writeLong(t.value());
            case NbtTag.FloatTag t -> out.writeFloat(t.value());
            case NbtTag.DoubleTag t -> out.writeDouble(t.value());
            case NbtTag.ByteArrayTag t -> {
                out.writeInt(t.value().length);
                out.write(t.value());
            }
            case NbtTag.StringTag t -> writeModifiedUtf8(out, t.value());
            case NbtTag.ListTag t -> {
                out.writeByte(t.value().isEmpty() ? NbtTag.END : t.elementTypeId());
                out.writeInt(t.value().size());
                for (NbtTag element : t.value()) {
                    writePayload(out, element);
                }
            }
            case NbtTag.CompoundTag t -> {
                for (Map.Entry<String, NbtTag> entry : t.value().entrySet()) {
                    writeNamed(out, entry.getKey(), entry.getValue());
                }
                out.writeByte(NbtTag.END);
            }
            case NbtTag.IntArrayTag t -> {
                out.writeInt(t.value().length);
                for (int value : t.value()) {
                    out.writeInt(value);
                }
            }
            case NbtTag.LongArrayTag t -> {
                out.writeInt(t.value().length);
                for (long value : t.value()) {
                    out.writeLong(value);
                }
            }
        }
    }

    /**
     * NBT strings are length-prefixed with an unsigned short byte count
     * (not a char count) followed by Java's "modified UTF-8" encoding —
     * exactly what {@link DataOutput#writeUTF} already produces, so this
     * is a one-line delegation, kept as a named method for clarity at
     * call sites.
     */
    private static void writeModifiedUtf8(DataOutput out, String value) throws IOException {
        out.writeUTF(value);
    }

    static List<NbtTag> asList(NbtTag... tags) {
        return List.of(tags);
    }
}
