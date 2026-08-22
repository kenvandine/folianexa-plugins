package io.github.phqen1x.worldeditcraft.schem;

import java.util.List;
import java.util.Map;

/**
 * A hand-rolled NBT value tree — this repo does not shade third-party
 * dependencies (see {@code MiniJson}'s javadoc for the same rationale
 * applied to JSON), and the alternative here would be shading a library
 * or taking a runtime dependency on WorldEdit for its NBT implementation,
 * which reintroduces the exact dependency this plugin's design avoids
 * (see docs/phqen1x-rpg-suite/01-worldeditcraft-design.md
 * "Why not just depend on WorldEdit?"). Thirteen tag types, matching the
 * NBT specification; every {@link NbtWriter}/{@link NbtReader} round-trip
 * only ever needs these.
 */
sealed interface NbtTag {

    record ByteTag(byte value) implements NbtTag {
    }

    record ShortTag(short value) implements NbtTag {
    }

    record IntTag(int value) implements NbtTag {
    }

    record LongTag(long value) implements NbtTag {
    }

    record FloatTag(float value) implements NbtTag {
    }

    record DoubleTag(double value) implements NbtTag {
    }

    record ByteArrayTag(byte[] value) implements NbtTag {
    }

    record StringTag(String value) implements NbtTag {
    }

    /** Every element must be the same NBT tag type, per the NBT spec. */
    record ListTag(byte elementTypeId, List<NbtTag> value) implements NbtTag {
    }

    /** Insertion-ordered name -> tag; callers should pass a {@link java.util.LinkedHashMap}. */
    record CompoundTag(Map<String, NbtTag> value) implements NbtTag {
    }

    record IntArrayTag(int[] value) implements NbtTag {
    }

    record LongArrayTag(long[] value) implements NbtTag {
    }

    byte END = 0;
    byte BYTE = 1;
    byte SHORT = 2;
    byte INT = 3;
    byte LONG = 4;
    byte FLOAT = 5;
    byte DOUBLE = 6;
    byte BYTE_ARRAY = 7;
    byte STRING = 8;
    byte LIST = 9;
    byte COMPOUND = 10;
    byte INT_ARRAY = 11;
    byte LONG_ARRAY = 12;

    static byte typeIdOf(NbtTag tag) {
        return switch (tag) {
            case ByteTag ignored -> BYTE;
            case ShortTag ignored -> SHORT;
            case IntTag ignored -> INT;
            case LongTag ignored -> LONG;
            case FloatTag ignored -> FLOAT;
            case DoubleTag ignored -> DOUBLE;
            case ByteArrayTag ignored -> BYTE_ARRAY;
            case StringTag ignored -> STRING;
            case ListTag ignored -> LIST;
            case CompoundTag ignored -> COMPOUND;
            case IntArrayTag ignored -> INT_ARRAY;
            case LongArrayTag ignored -> LONG_ARRAY;
        };
    }
}
