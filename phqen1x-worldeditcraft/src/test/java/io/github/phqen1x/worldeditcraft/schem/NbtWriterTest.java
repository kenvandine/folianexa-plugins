package io.github.phqen1x.worldeditcraft.schem;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Asserts the exact wire bytes {@link NbtWriter} produces, decoded with
 * plain {@link DataInputStream} calls rather than {@link NbtReader} — so
 * a bug shared between writer and reader can't hide a real format error.
 */
class NbtWriterTest {

    @Test
    void writesNamedIntTag() throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeNamed(new DataOutputStream(bytes), "Version", new NbtTag.IntTag(3));

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(NbtTag.INT, in.readByte());
        assertEquals("Version", in.readUTF());
        assertEquals(3, in.readInt());
    }

    @Test
    void writesUnsignedShortDimensionsAsRawShortBits() throws IOException {
        // Width/Height/Length are documented as "unsigned short" — a value
        // above Short.MAX_VALUE must still round-trip via bit pattern, not throw.
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeNamed(new DataOutputStream(bytes), "Width", new NbtTag.ShortTag((short) 40000));

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.readByte();
        in.readUTF();
        int unsigned = in.readShort() & 0xFFFF;
        assertEquals(40000, unsigned);
    }

    @Test
    void writesCompoundWithEndMarker() throws IOException {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        entries.put("Name", new NbtTag.StringTag("hall"));
        entries.put("Date", new NbtTag.LongTag(1000L));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeNamed(new DataOutputStream(bytes), "Metadata", new NbtTag.CompoundTag(entries));

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(NbtTag.COMPOUND, in.readByte());
        assertEquals("Metadata", in.readUTF());
        assertEquals(NbtTag.STRING, in.readByte());
        assertEquals("Name", in.readUTF());
        assertEquals("hall", in.readUTF());
        assertEquals(NbtTag.LONG, in.readByte());
        assertEquals("Date", in.readUTF());
        assertEquals(1000L, in.readLong());
        assertEquals(NbtTag.END, in.readByte());
    }

    @Test
    void writesListWithElementTypeAndCountButNoPerElementNamesOrTypes() throws IOException {
        NbtTag.ListTag list = new NbtTag.ListTag(NbtTag.INT, NbtWriter.asList(new NbtTag.IntTag(1), new NbtTag.IntTag(2), new NbtTag.IntTag(3)));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeNamed(new DataOutputStream(bytes), "Offset", list);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(NbtTag.LIST, in.readByte());
        assertEquals("Offset", in.readUTF());
        assertEquals(NbtTag.INT, in.readByte()); // element type
        assertEquals(3, in.readInt()); // element count
        assertEquals(1, in.readInt());
        assertEquals(2, in.readInt());
        assertEquals(3, in.readInt());
    }

    @Test
    void writesEmptyListWithEndElementType() throws IOException {
        NbtTag.ListTag empty = new NbtTag.ListTag(NbtTag.COMPOUND, NbtWriter.asList());

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeNamed(new DataOutputStream(bytes), "Entities", empty);

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.readByte();
        in.readUTF();
        assertEquals(NbtTag.END, in.readByte());
        assertEquals(0, in.readInt());
    }

    @Test
    void writesByteArrayLengthPrefixed() throws IOException {
        byte[] payload = {1, 2, 3, 4, 5};
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeNamed(new DataOutputStream(bytes), "Data", new NbtTag.ByteArrayTag(payload));

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        in.readByte();
        in.readUTF();
        assertEquals(5, in.readInt());
        byte[] read = new byte[5];
        in.readFully(read);
        assertEquals("[1, 2, 3, 4, 5]", java.util.Arrays.toString(read));
    }

    @Test
    void writesUnnamedRootCompound() throws IOException {
        Map<String, NbtTag> entries = new LinkedHashMap<>();
        entries.put("Version", new NbtTag.IntTag(3));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        NbtWriter.writeUnnamedRoot(new DataOutputStream(bytes), new NbtTag.CompoundTag(entries));

        DataInputStream in = new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(NbtTag.COMPOUND, in.readByte());
        assertEquals("", in.readUTF()); // unnamed root
        assertEquals(NbtTag.INT, in.readByte());
        assertEquals("Version", in.readUTF());
        assertEquals(3, in.readInt());
        assertEquals(NbtTag.END, in.readByte());
    }
}
