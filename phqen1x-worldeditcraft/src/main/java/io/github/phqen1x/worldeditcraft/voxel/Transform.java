package io.github.phqen1x.worldeditcraft.voxel;

/**
 * A rotation about the vertical (Y) axis, composed with an optional
 * mirror flip, applied to both voxel positions and direction-valued
 * block-state properties ({@code facing}, {@code axis}) — a rotated
 * staircase must actually face the new way, not just move to a new
 * position with its old orientation. Flip is applied before rotation.
 *
 * <p>{@code degrees} is normalized to one of 0/90/180/270 — every
 * rotation this plugin ever performs is a multiple of a quarter turn,
 * per {@code rotate_group}'s and {@code /wec paste --rot}'s own
 * constraints.
 */
public record Transform(int degrees, Flip flip) {

    public enum Flip {NONE, X, Z}

    public static final Transform IDENTITY = new Transform(0, Flip.NONE);

    public Transform {
        int normalized = Math.floorMod(degrees, 360);
        if (normalized % 90 != 0) {
            throw new IllegalArgumentException("Rotation must be a multiple of 90 degrees: " + degrees);
        }
        degrees = normalized;
    }

    private int steps() {
        return degrees / 90;
    }

    /** The width (x-extent) of a region of the given input size, after this transform. */
    public int outputWidth(int inputWidth, int inputLength) {
        return (steps() % 2 == 0) ? inputWidth : inputLength;
    }

    /** The length (z-extent) of a region of the given input size, after this transform. */
    public int outputLength(int inputWidth, int inputLength) {
        return (steps() % 2 == 0) ? inputLength : inputWidth;
    }

    /**
     * Maps a position {@code (x, z)} inside a {@code width x length}
     * footprint to its position after this transform. Y is never
     * touched — rotation here is always about the vertical axis.
     */
    public int[] apply(int x, int z, int width, int length) {
        int fx = x;
        int fz = z;
        if (flip == Flip.X) {
            fx = width - 1 - x;
        } else if (flip == Flip.Z) {
            fz = length - 1 - z;
        }

        int steps = Math.floorMod(steps(), 4);
        int w = width;
        int l = length;
        int rx = fx;
        int rz = fz;
        for (int i = 0; i < steps; i++) {
            int newX = (l - 1) - rz;
            int newZ = rx;
            rx = newX;
            rz = newZ;
            int tmp = w;
            w = l;
            l = tmp;
        }
        return new int[]{rx, rz};
    }

    /** Rewrites {@code facing}/{@code axis} block-state properties to match this transform. */
    public BlockStateRef applyToState(BlockStateRef state) {
        BlockStateRef result = state;

        String facing = result.properties().get("facing");
        if (facing != null) {
            result = result.withProperty("facing", transformFacing(facing));
        }

        String axis = result.properties().get("axis");
        if (axis != null) {
            result = result.withProperty("axis", transformAxis(axis));
        }

        return result;
    }

    private String transformFacing(String facing) {
        String value = facing;
        if (flip == Flip.X) {
            value = switch (value) {
                case "east" -> "west";
                case "west" -> "east";
                default -> value;
            };
        } else if (flip == Flip.Z) {
            value = switch (value) {
                case "north" -> "south";
                case "south" -> "north";
                default -> value;
            };
        }

        int steps = Math.floorMod(steps(), 4);
        for (int i = 0; i < steps; i++) {
            value = switch (value) {
                case "north" -> "east";
                case "east" -> "south";
                case "south" -> "west";
                case "west" -> "north";
                default -> value; // up / down unaffected by a Y-axis rotation
            };
        }
        return value;
    }

    private String transformAxis(String axis) {
        // Flip never changes which axis a symmetric block (e.g. a log)
        // lies along. A 90/270 rotation swaps the x and z axes; 180
        // leaves every axis exactly where it was.
        if (Math.floorMod(steps(), 2) == 0) {
            return axis;
        }
        return switch (axis) {
            case "x" -> "z";
            case "z" -> "x";
            default -> axis; // "y" unaffected by a Y-axis rotation
        };
    }

}
