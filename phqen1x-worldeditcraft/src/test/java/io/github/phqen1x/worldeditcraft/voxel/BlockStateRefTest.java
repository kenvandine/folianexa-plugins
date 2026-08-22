package io.github.phqen1x.worldeditcraft.voxel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class BlockStateRefTest {

    @Test
    void parsesPlainBlockId() {
        BlockStateRef ref = BlockStateRef.parse("minecraft:stone");
        assertEquals("minecraft", ref.namespace());
        assertEquals("stone", ref.id());
        assertEquals("minecraft:stone", ref.toString());
    }

    @Test
    void parsesBlockIdWithProperties() {
        BlockStateRef ref = BlockStateRef.parse("minecraft:oak_stairs[facing=north,half=top]");
        assertEquals("north", ref.properties().get("facing"));
        assertEquals("top", ref.properties().get("half"));
        assertEquals("minecraft:oak_stairs[facing=north,half=top]", ref.toString());
    }

    @Test
    void defaultsToMinecraftNamespaceWhenOmitted() {
        BlockStateRef ref = BlockStateRef.parse("stone");
        assertEquals("minecraft", ref.namespace());
        assertEquals("stone", ref.id());
    }

    @Test
    void sortsPropertiesAlphabeticallyRegardlessOfInputOrder() {
        BlockStateRef fromZThenA = BlockStateRef.parse("minecraft:oak_log[axis=y]");
        BlockStateRef reordered = BlockStateRef.parse("minecraft:oak_stairs[half=top,facing=north]");
        assertEquals("minecraft:oak_log[axis=y]", fromZThenA.toString());
        assertEquals("minecraft:oak_stairs[facing=north,half=top]", reordered.toString());
    }

    @Test
    void canonicalStringIsStableAcrossEquivalentInputOrders() {
        BlockStateRef a = BlockStateRef.parse("minecraft:oak_stairs[facing=north,half=top]");
        BlockStateRef b = BlockStateRef.parse("minecraft:oak_stairs[half=top,facing=north]");
        assertEquals(a, b);
        assertEquals(a.toString(), b.toString());
    }

    @Test
    void rejectsBlankInput() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateRef.parse(""));
        assertThrows(IllegalArgumentException.class, () -> BlockStateRef.parse("   "));
    }

    @Test
    void rejectsMissingClosingBracket() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateRef.parse("minecraft:oak_stairs[facing=north"));
    }

    @Test
    void rejectsPropertyWithoutEquals() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateRef.parse("minecraft:oak_stairs[facingnorth]"));
    }

    @Test
    void rejectsMissingBlockId() {
        assertThrows(IllegalArgumentException.class, () -> BlockStateRef.parse("minecraft:"));
    }
}
