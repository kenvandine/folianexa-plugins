package io.github.phqen1x.worldeditcraft.schem;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

class VarIntCodecTest {

    @Test
    void roundTripsBoundaryValues() {
        int[] values = {0, 1, 127, 128, 255, 16383, 16384, Integer.MAX_VALUE};
        for (int value : values) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            VarIntCodec.write(out, value);
            byte[] bytes = out.toByteArray();

            VarIntCodec.Cursor cursor = new VarIntCodec.Cursor();
            int read = VarIntCodec.read(bytes, cursor);

            assertEquals(value, read, "round-trip for " + value);
            assertEquals(bytes.length, cursor.pos, "cursor should consume exactly the written bytes for " + value);
        }
    }

    @Test
    void oneByteBoundaryCases() {
        assertArrayEquals(new byte[]{0}, packed(0));
        assertArrayEquals(new byte[]{1}, packed(1));
        assertArrayEquals(new byte[]{127}, packed(127));
    }

    @Test
    void twoByteBoundaryCases() {
        // 128 = 0b10000000 -> low 7 bits (0000000) with continuation bit, then high bit (1)
        assertArrayEquals(new byte[]{(byte) 0x80, 0x01}, packed(128));
        // 255 = 0b11111111 -> (0x7F | 0x80), then 1
        assertArrayEquals(new byte[]{(byte) 0xFF, 0x01}, packed(255));
        // 16383 = 0b11_1111_1111_1111 -> two bytes, both with all low bits set
        assertArrayEquals(new byte[]{(byte) 0xFF, 0x7F}, packed(16383));
    }

    @Test
    void threeByteBoundaryCase() {
        // 16384 = 2^14 -> first two bytes are continuation-only zero groups
        assertArrayEquals(new byte[]{(byte) 0x80, (byte) 0x80, 0x01}, packed(16384));
    }

    @Test
    void multipleValuesReadSequentiallyFromOneBuffer() {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VarIntCodec.write(out, 1);
        VarIntCodec.write(out, 300);
        VarIntCodec.write(out, 0);
        byte[] bytes = out.toByteArray();

        VarIntCodec.Cursor cursor = new VarIntCodec.Cursor();
        assertEquals(1, VarIntCodec.read(bytes, cursor));
        assertEquals(300, VarIntCodec.read(bytes, cursor));
        assertEquals(0, VarIntCodec.read(bytes, cursor));
        assertEquals(bytes.length, cursor.pos);
    }

    private static byte[] packed(int value) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        VarIntCodec.write(out, value);
        return out.toByteArray();
    }
}
