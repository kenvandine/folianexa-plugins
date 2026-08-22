package io.github.phqen1x.worldeditcraft.voxel;

/** An integer axis-aligned bounding box, inclusive on both ends. */
public record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {

    public static Bounds of(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Bounds(
                Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2),
                Math.max(x1, x2), Math.max(y1, y2), Math.max(z1, z2)
        );
    }

    public int width() {
        return maxX - minX + 1;
    }

    public int height() {
        return maxY - minY + 1;
    }

    public int length() {
        return maxZ - minZ + 1;
    }

    public boolean contains(int x, int y, int z) {
        return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
    }

    /** Clamps a point into this box on each axis independently. */
    public int[] clamp(int x, int y, int z) {
        return new int[]{
                Math.max(minX, Math.min(maxX, x)),
                Math.max(minY, Math.min(maxY, y)),
                Math.max(minZ, Math.min(maxZ, z))
        };
    }

    /** Intersects with a grid of the given size, anchored at [0,0,0]-[w-1,h-1,l-1]. */
    public Bounds clampToGrid(int width, int height, int length) {
        return Bounds.of(
                Math.max(0, minX), Math.max(0, minY), Math.max(0, minZ),
                Math.min(width - 1, maxX), Math.min(height - 1, maxY), Math.min(length - 1, maxZ)
        );
    }
}
