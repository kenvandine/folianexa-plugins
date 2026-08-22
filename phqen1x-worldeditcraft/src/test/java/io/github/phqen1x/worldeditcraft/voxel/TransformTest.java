package io.github.phqen1x.worldeditcraft.voxel;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TransformTest {

    @Test
    void fourNinetyDegreeRotationsComposeToIdentity() {
        int width = 5;
        int length = 3;
        Transform rot90 = new Transform(90, Transform.Flip.NONE);

        int x = 1;
        int z = 2;
        int w = width;
        int l = length;
        for (int i = 0; i < 4; i++) {
            int[] next = rot90.apply(x, z, w, l);
            int newW = rot90.outputWidth(w, l);
            int newL = rot90.outputLength(w, l);
            x = next[0];
            z = next[1];
            w = newW;
            l = newL;
        }

        assertEquals(1, x);
        assertEquals(2, z);
        assertEquals(width, w);
        assertEquals(length, l);
    }

    @Test
    void rotationIsABijectionOverTheFootprint() {
        int width = 4;
        int length = 6;
        Transform rot90 = new Transform(90, Transform.Flip.NONE);

        Set<Long> seen = new HashSet<>();
        for (int x = 0; x < width; x++) {
            for (int z = 0; z < length; z++) {
                int[] mapped = rot90.apply(x, z, width, length);
                long key = (((long) mapped[0]) << 32) | (mapped[1] & 0xFFFFFFFFL);
                assertEquals(true, seen.add(key), "duplicate mapped position for (%d,%d)".formatted(x, z));
            }
        }
        assertEquals(width * length, seen.size());
    }

    @Test
    void rotate90SwapsFootprintDimensions() {
        Transform rot90 = new Transform(90, Transform.Flip.NONE);
        assertEquals(6, rot90.outputWidth(4, 6));
        assertEquals(4, rot90.outputLength(4, 6));
    }

    @Test
    void rotate180PreservesFootprintDimensions() {
        Transform rot180 = new Transform(180, Transform.Flip.NONE);
        assertEquals(4, rot180.outputWidth(4, 6));
        assertEquals(6, rot180.outputLength(4, 6));
    }

    @Test
    void facingNorthRotatedNinetyDegreesBecomesEast() {
        Transform rot90 = new Transform(90, Transform.Flip.NONE);
        BlockStateRef facingNorth = BlockStateRef.parse("minecraft:oak_stairs[facing=north,half=bottom]");
        BlockStateRef rotated = rot90.applyToState(facingNorth);
        assertEquals("east", rotated.properties().get("facing"));
    }

    @Test
    void facingCyclesThroughAllFourDirectionsOverFourRotations() {
        BlockStateRef state = BlockStateRef.parse("minecraft:oak_stairs[facing=north]");
        String[] expected = {"north", "east", "south", "west", "north"};
        for (int i = 0; i < 4; i++) {
            Transform rot = new Transform(90 * i, Transform.Flip.NONE);
            assertEquals(expected[i], rot.applyToState(state).properties().get("facing"));
        }
    }

    @Test
    void verticalFacingsAreUnaffectedByYAxisRotation() {
        BlockStateRef facingUp = BlockStateRef.parse("minecraft:dropper[facing=up]");
        Transform rot90 = new Transform(90, Transform.Flip.NONE);
        assertEquals("up", rot90.applyToState(facingUp).properties().get("facing"));
    }

    @Test
    void flipXMirrorsEastWestFacingButLeavesAxisXUnchanged() {
        Transform flipX = new Transform(0, Transform.Flip.X);

        BlockStateRef facingEast = BlockStateRef.parse("minecraft:dropper[facing=east]");
        assertEquals("west", flipX.applyToState(facingEast).properties().get("facing"));

        BlockStateRef axisX = BlockStateRef.parse("minecraft:oak_log[axis=x]");
        assertEquals("x", flipX.applyToState(axisX).properties().get("axis"));
    }

    @Test
    void flipZMirrorsNorthSouthFacingButLeavesEastWestAlone() {
        Transform flipZ = new Transform(0, Transform.Flip.Z);

        BlockStateRef facingNorth = BlockStateRef.parse("minecraft:dropper[facing=north]");
        assertEquals("south", flipZ.applyToState(facingNorth).properties().get("facing"));

        BlockStateRef facingEast = BlockStateRef.parse("minecraft:dropper[facing=east]");
        assertEquals("east", flipZ.applyToState(facingEast).properties().get("facing"));
    }

    @Test
    void rotate90SwapsAxisXAndZButNotY() {
        Transform rot90 = new Transform(90, Transform.Flip.NONE);
        assertEquals("z", rot90.applyToState(BlockStateRef.parse("minecraft:oak_log[axis=x]")).properties().get("axis"));
        assertEquals("x", rot90.applyToState(BlockStateRef.parse("minecraft:oak_log[axis=z]")).properties().get("axis"));
        assertEquals("y", rot90.applyToState(BlockStateRef.parse("minecraft:oak_log[axis=y]")).properties().get("axis"));
    }

    @Test
    void rejectsNonNinetyMultipleDegrees() {
        assertThrows(IllegalArgumentException.class, () -> new Transform(45, Transform.Flip.NONE));
    }
}
