package io.github.phqen1x.worldeditcraft.schem;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NbtReaderTest {

    @Test
    void everyScalarTagTypeRoundTrips() throws IOException {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        entries.put("aByte", new NbtTag.ByteTag((byte) 42));
        entries.put("aShort", new NbtTag.ShortTag((short) 1234));
        entries.put("anInt", new NbtTag.IntTag(-99));
        entries.put("aLong", new NbtTag.LongTag(123456789012L));
        entries.put("aFloat", new NbtTag.FloatTag(1.5f));
        entries.put("aDouble", new NbtTag.DoubleTag(2.25));
        entries.put("aString", new NbtTag.StringTag("hello"));
        entries.put("aByteArray", new NbtTag.ByteArrayTag(new byte[]{1, 2, 3}));
        entries.put("anIntArray", new NbtTag.IntArrayTag(new int[]{7, 8, 9}));
        entries.put("aLongArray", new NbtTag.LongArrayTag(new long[]{10L, 11L}));

        NbtTag.CompoundTag root = new NbtTag.CompoundTag(entries);
        NbtTag.CompoundTag readBack = roundTrip(root);

        assertEquals((byte) 42, ((NbtTag.ByteTag) readBack.value().get("aByte")).value());
        assertEquals((short) 1234, ((NbtTag.ShortTag) readBack.value().get("aShort")).value());
        assertEquals(-99, ((NbtTag.IntTag) readBack.value().get("anInt")).value());
        assertEquals(123456789012L, ((NbtTag.LongTag) readBack.value().get("aLong")).value());
        assertEquals(1.5f, ((NbtTag.FloatTag) readBack.value().get("aFloat")).value());
        assertEquals(2.25, ((NbtTag.DoubleTag) readBack.value().get("aDouble")).value());
        assertEquals("hello", ((NbtTag.StringTag) readBack.value().get("aString")).value());
        assertArrayEquals(new byte[]{1, 2, 3}, ((NbtTag.ByteArrayTag) readBack.value().get("aByteArray")).value());
        assertArrayEquals(new int[]{7, 8, 9}, ((NbtTag.IntArrayTag) readBack.value().get("anIntArray")).value());
        assertArrayEquals(new long[]{10L, 11L}, ((NbtTag.LongArrayTag) readBack.value().get("aLongArray")).value());
    }

    @Test
    void nestedCompoundsAndListsRoundTrip() throws IOException {
        Map<String, NbtTag> inner = new LinkedHashMap<>();
        inner.put("x", new NbtTag.IntTag(1));
        NbtTag.ListTag listOfCompounds = new NbtTag.ListTag(NbtTag.COMPOUND,
                List.of(new NbtTag.CompoundTag(inner), new NbtTag.CompoundTag(inner)));

        Map<String, NbtTag> outer = new LinkedHashMap<>();
        outer.put("items", listOfCompounds);
        NbtTag.CompoundTag root = new NbtTag.CompoundTag(outer);

        NbtTag.CompoundTag readBack = roundTrip(root);
        NbtTag.ListTag readList = (NbtTag.ListTag) readBack.value().get("items");
        assertEquals(2, readList.value().size());
        NbtTag.CompoundTag firstItem = (NbtTag.CompoundTag) readList.value().get(0);
        assertEquals(1, ((NbtTag.IntTag) firstItem.value().get("x")).value());
    }

    @Test
    void nonAsciiStringsRoundTripViaModifiedUtf8() throws IOException {
        // Includes a literal NUL character (\u0000): Java's modified-UTF-8
        // encodes it as the two-byte sequence 0xC0 0x80 rather than a
        // single 0x00 byte, precisely so a NUL never terminates the string
        // early the way it would in a plain C string.
        String nonAsciiKey = "name-éè中文";
        String value = "before" + '\u0000' + "after";

        Map<String, NbtTag> entries = new LinkedHashMap<>();
        entries.put(nonAsciiKey, new NbtTag.StringTag(value));
        NbtTag.CompoundTag readBack = roundTrip(new NbtTag.CompoundTag(entries));

        assertEquals(value, ((NbtTag.StringTag) readBack.value().get(nonAsciiKey)).value());
    }

    @Test
    void deeplyNestedCompoundBombIsRejected() throws IOException {
        // 600 levels of nested single-entry compounds — comfortably past
        // NbtReader's internal depth limit — must fail cleanly rather than
        // stack-overflow.
        NbtTag current = new NbtTag.CompoundTag(Map.of("leaf", new NbtTag.IntTag(1)));
        for (int i = 0; i < 600; i++) {
            current = new NbtTag.CompoundTag(Map.of("nested", current));
        }

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeUnnamedRoot(new DataOutputStream(bytes), (NbtTag.CompoundTag) current);
        byte[] written = bytes.toByteArray();

        assertThrows(IOException.class, () -> NbtReader.readUnnamedRoot(new DataInputStream(new ByteArrayInputStream(written))));
    }

    @Test
    void impossiblyLargeArrayLengthIsRejectedRatherThanAllocated() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        DataOutputStream out = new DataOutputStream(bytes);
        out.writeByte(NbtTag.COMPOUND);
        out.writeUTF(""); // unnamed root
        out.writeByte(NbtTag.BYTE_ARRAY);
        out.writeUTF("bomb");
        out.writeInt(Integer.MAX_VALUE); // claims ~2 billion bytes follow; none actually do
        out.writeByte(NbtTag.END);

        assertThrows(IOException.class, () ->
                NbtReader.readUnnamedRoot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
    }

    private static NbtTag.CompoundTag roundTrip(NbtTag.CompoundTag root) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeUnnamedRoot(new DataOutputStream(bytes), root);
        return NbtReader.readUnnamedRoot(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())));
    }
}
