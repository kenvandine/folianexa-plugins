package io.github.phqen1x.worldeditcraft.schem;

import java.io.ByteArrayOutputStream;

/**
 * Unsigned LEB128 varint packing, used by the Sponge v3 {@code .schem}
 * format's {@code Blocks.Data} array to store palette indices compactly:
 * seven bits per byte, low group first, high bit set on every byte but
 * the last. Indices below 128 (the common case) take a single byte.
 */
final class VarIntCodec {

    private VarIntCodec() {
    }

    static void write(ByteArrayOutputStream out, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("varint value must be non-negative: " + value);
        }
        while (true) {
            if ((value & ~0x7F) == 0) {
                out.write(value);
                return;
            }
            out.write((value & 0x7F) | 0x80);
            value >>>= 7;
        }
    }

    /**
     * Reads one varint starting at {@code cursor.pos}, advancing it past
     * the value's bytes.
     */
    static int read(byte[] data, Cursor cursor) {
        int value = 0;
        int shift = 0;
        while (true) {
            if (cursor.pos >= data.length) {
                throw new IllegalArgumentException("Truncated varint at position " + cursor.pos);
            }
            byte b = data[cursor.pos++];
            value |= (b & 0x7F) << shift;
            if ((b & 0x80) == 0) {
                return value;
            }
            shift += 7;
            if (shift > 35) {
                throw new IllegalArgumentException("Varint too long (more than 5 bytes)");
            }
        }
    }

    /** A mutable read cursor into a byte array — plain index, no allocation per read. */
    static final class Cursor {
        int pos;
    }
}
