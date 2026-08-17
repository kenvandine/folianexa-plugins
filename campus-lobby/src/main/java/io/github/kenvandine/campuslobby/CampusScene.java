package io.github.kenvandine.campuslobby;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally generates an NC State Wolfpack-themed lobby scene: a
 * central brick plaza (the "Brickyard"), a Belltower centerpiece, a
 * blocky wolf-mascot statue, a Free-Expression-Tunnel-style mural
 * walkway, and stylized student-union / library building facades.
 *
 * This is a deliberately stylized, iconic representation — recognizable
 * silhouettes and Wolfpack colors, not a literal architectural
 * reconstruction (no real building schematics or geometry data were
 * used; see this repo's README). Every shape is generated from plain
 * arithmetic, so it stays unit-testable without a running server.
 *
 * All coordinates are relative to a scene origin at plaza-floor level:
 * +x east, -x west, +z south, -z north, y up from the floor (y=0).
 */
public final class CampusScene {

    private CampusScene() {
    }

    public record Scene(List<BlockPlacement> blocks, List<SignPlacement> signs, Bounds bounds) {
    }

    /** The axis-aligned extent (inclusive) of every block/sign in a Scene, relative to the scene origin. */
    public record Bounds(int minX, int maxX, int minY, int maxY, int minZ, int maxZ) {
    }

    public static Scene generate(SceneConfig cfg) {
        Canvas canvas = new Canvas();
        buildPlaza(canvas, cfg);
        if (cfg.include().belltower()) {
            buildBelltower(canvas, cfg);
        }
        if (cfg.include().wolfStatue()) {
            buildWolfStatue(canvas, cfg);
        }
        if (cfg.include().tunnel()) {
            buildTunnel(canvas, cfg);
        }
        if (cfg.include().unionFacade()) {
            buildUnionFacade(canvas, cfg);
        }
        if (cfg.include().libraryFacade()) {
            buildLibraryFacade(canvas, cfg);
        }
        return canvas.toScene();
    }

    // -- The Brickyard: central plaza -----------------------------------

    private static void buildPlaza(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        // The Brickyard: authentic alternating terracotta clay paver grid across the entire plaza floor.
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int tile = ((Math.floorDiv(x, 4) + Math.floorDiv(z, 4)) & 1);
                String paver = (tile == 0) ? c.brick() : c.clayNormal();
                canvas.set(x, 0, z, paver);
            }
        }

        // Concentric perimeter borders in Wolfpack colors and colored clay.
        canvas.ring(-r, -r, r, r, 0, c.clayBlack());
        canvas.ring(-(r - 1), -(r - 1), r - 1, r - 1, 0, c.roof());
        canvas.ring(-(r - 2), -(r - 2), r - 2, r - 2, 0, c.clayWhite());
        canvas.ring(-(r - 3), -(r - 3), r - 3, r - 3, 0, c.primaryRed());
        canvas.ring(-(r - 4), -(r - 4), r - 4, r - 4, 0, c.clayNormal());

        // Grand central NC State "Block S" / Wolfpack medallion
        int medR = 9;
        for (int x = -medR; x <= medR; x++) {
            for (int z = -medR; z <= medR; z++) {
                int dist = Math.abs(x) + Math.abs(z);
                if (dist <= 12) {
                    canvas.set(x, 0, z, c.primaryRed());
                }
                if (dist == 12 || (Math.abs(x) == medR && Math.abs(z) <= 3) || (Math.abs(z) == medR && Math.abs(x) <= 3)) {
                    canvas.set(x, 0, z, c.clayWhite());
                }
            }
        }

        // Block S inlay in the medallion (white with black outline)
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= 5; z++) {
                if (isBlockS(x, z)) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (!isBlockS(x + dx, z + dz)) {
                                canvas.set(x + dx, 0, z + dz, c.black());
                            }
                        }
                    }
                }
            }
        }
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= 5; z++) {
                if (isBlockS(x, z)) {
                    canvas.set(x, 0, z, c.white());
                }
            }
        }

        // Grand Cardinal Pedestrian Promenades (+x East, -x West, +z South, -z North)
        int pLimit = r - 5;
        for (int x = -pLimit; x <= pLimit; x++) {
            if (Math.abs(x) > medR - 2) {
                for (int dz = -1; dz <= 1; dz++) {
                    canvas.set(x, 0, dz, c.clayWhite());
                }
                canvas.set(x, 0, -2, c.clayBlack());
                canvas.set(x, 0, 2, c.clayBlack());
                canvas.set(x, 0, -3, c.primaryRed());
                canvas.set(x, 0, 3, c.primaryRed());
            }
        }
        for (int z = -pLimit; z <= pLimit; z++) {
            if (Math.abs(z) > medR - 2) {
                for (int dx = -1; dx <= 1; dx++) {
                    canvas.set(dx, 0, z, c.clayWhite());
                }
                canvas.set(-2, 0, z, c.clayBlack());
                canvas.set(2, 0, z, c.clayBlack());
                canvas.set(-3, 0, z, c.primaryRed());
                canvas.set(3, 0, z, c.primaryRed());
            }
        }

        // Diagonal Walkways connecting center to corners
        for (int d = medR; d <= pLimit - 4; d++) {
            canvas.set(d, 0, d, c.clayWhite());
            canvas.set(-d, 0, d, c.clayWhite());
            canvas.set(d, 0, -d, c.clayWhite());
            canvas.set(-d, 0, -d, c.clayWhite());
        }

        // Campus Lampposts along the grand avenues
        int[] lampDistances = {14, 24, 34, 42};
        for (int ld : lampDistances) {
            if (ld < pLimit - 2) {
                placeLamppost(canvas, ld, 4);
                placeLamppost(canvas, ld, -4);
                placeLamppost(canvas, -ld, 4);
                placeLamppost(canvas, -ld, -4);
                placeLamppost(canvas, 4, ld);
                placeLamppost(canvas, -4, ld);
                placeLamppost(canvas, 4, -ld);
                placeLamppost(canvas, -4, -ld);
            }
        }

        // Raised Campus Planters & Benches in the four quadrants
        int planterD = Math.min(22, r / 2);
        if (planterD >= 12) {
            placePlanter(canvas, planterD, planterD, c);
            placePlanter(canvas, -planterD, planterD, c);
            placePlanter(canvas, planterD, -planterD, c);
            placePlanter(canvas, -planterD, -planterD, c);
        }

        // Wolfpack Flagpoles at the 4 corners
        int flagOffset = Math.max(8, r - 5);
        placeFlagpole(canvas, flagOffset, flagOffset, c.primaryRed(), true);
        placeFlagpole(canvas, -flagOffset, flagOffset, c.white(), false);
        placeFlagpole(canvas, flagOffset, -flagOffset, c.white(), false);
        placeFlagpole(canvas, -flagOffset, -flagOffset, c.primaryRed(), true);
    }

    private static boolean isBlockS(int x, int z) {
        if (z == -5 && x >= -3 && x <= 3) return true;
        if (z == -4 && (x == -3 || x == -2)) return true;
        if (z == -3 && (x == -3 || x == -2)) return true;
        if (z == -2 && (x == -3 || x == -2)) return true;
        if (z == -1 && x >= -3 && x <= 3) return true;
        if (z == 0 && x >= -3 && x <= 3) return true;
        if (z == 1 && (x == 2 || x == 3)) return true;
        if (z == 2 && (x == 2 || x == 3)) return true;
        if (z == 3 && (x == 2 || x == 3)) return true;
        if (z == 4 && x >= -3 && x <= 3) return true;
        return false;
    }

    private static void placeLamppost(Canvas canvas, int x, int z) {
        canvas.fillBox(x, 1, z, x, 3, z, "POLISHED_BLACKSTONE_WALL");
        canvas.set(x, 4, z, "POLISHED_BLACKSTONE");
        canvas.set(x, 3, z + 1, "LANTERN");
        canvas.set(x, 3, z - 1, "LANTERN");
        canvas.set(x, 5, z, "POLISHED_BLACKSTONE_SLAB");
    }

    private static void placePlanter(Canvas canvas, int cx, int cz, SceneConfig.Colors c) {
        canvas.ring(cx - 2, cz - 2, cx + 2, cz + 2, 1, c.clayRed());
        canvas.fillBox(cx - 1, 1, cz - 1, cx + 1, 1, cz + 1, "MOSS_BLOCK");
        canvas.set(cx, 2, cz, "AZALEA_LEAVES");
        canvas.set(cx - 1, 2, cz, "RED_TULIP");
        canvas.set(cx + 1, 2, cz, "WHITE_TULIP");
        canvas.set(cx, 2, cz - 1, "RED_TULIP");
        canvas.set(cx, 2, cz + 1, "WHITE_TULIP");

        canvas.fillBox(cx - 1, 1, cz - 3, cx + 1, 1, cz - 3, "SMOOTH_STONE_SLAB");
        canvas.fillBox(cx - 1, 1, cz + 3, cx + 1, 1, cz + 3, "SMOOTH_STONE_SLAB");
    }

    private static void placeFlagpole(Canvas canvas, int x, int z, String wolfpackColor, boolean isRed) {
        String banner = isRed ? "RED_BANNER" : "WHITE_BANNER";
        canvas.set(x, 1, z, "POLISHED_BLACKSTONE");
        canvas.fillBox(x, 2, z, x, 7, z, "IRON_BARS");
        canvas.set(x, 8, z, "POLISHED_BLACKSTONE");
        canvas.set(x, 7, z + 1, banner);
        canvas.set(x, 6, z + 1, banner);
    }

    // -- Memorial Belltower ----------------------------------------------

    private static void buildBelltower(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        int height = cfg.towerHeight();
        SceneConfig.Colors c = cfg.colors();

        int halfFoot = 4; // 9x9 shaft
        int halfPodium = 6; // 13x13 base podium
        int cz = -Math.max(halfPodium + 4, r - 14); // north of plaza center

        // 1. Grand Stepped Podium (y = 1 to 3)
        // 15x15 outer plinth
        canvas.fillBox(-halfPodium - 1, 1, cz - halfPodium - 1, halfPodium + 1, 1, cz + halfPodium + 1, c.roof());
        // 13x13 terrace
        canvas.fillBox(-halfPodium, 2, cz - halfPodium, halfPodium, 2, cz + halfPodium, c.clayWhite());
        // 11x11 upper platform
        canvas.fillBox(-halfFoot - 1, 3, cz - halfFoot - 1, halfFoot + 1, 3, cz + halfFoot + 1, c.clayRed());

        // Corner plinth pedestals
        canvas.fillBox(-halfPodium, 2, cz - halfPodium, -halfPodium + 1, 3, cz - halfPodium + 1, c.roof());
        canvas.fillBox(halfPodium - 1, 2, cz - halfPodium, halfPodium, 3, cz - halfPodium + 1, c.roof());
        canvas.fillBox(-halfPodium, 2, cz + halfPodium - 1, -halfPodium + 1, 3, cz + halfPodium, c.roof());
        canvas.fillBox(halfPodium - 1, 2, cz + halfPodium - 1, halfPodium, 3, cz + halfPodium, c.roof());

        // 2. Ground Floor Memorial Shrine & Rotunda (y = 4 to 9)
        for (int y = 4; y <= 9; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.clayWhite());
            canvas.set(-halfFoot, y, cz - halfFoot, c.roof());
            canvas.set(halfFoot, y, cz - halfFoot, c.roof());
            canvas.set(-halfFoot, y, cz + halfFoot, c.roof());
            canvas.set(halfFoot, y, cz + halfFoot, c.roof());
        }

        // 4 Grand Archway entrances (South, North, East, West)
        // South arch (facing plaza)
        canvas.fillBox(-1, 4, cz + halfFoot, 1, 7, cz + halfFoot, "AIR");
        canvas.set(0, 8, cz + halfFoot, "AIR");
        // North arch
        canvas.fillBox(-1, 4, cz - halfFoot, 1, 7, cz - halfFoot, "AIR");
        canvas.set(0, 8, cz - halfFoot, "AIR");
        // East arch
        canvas.fillBox(halfFoot, 4, cz - 1, halfFoot, 7, cz + 1, "AIR");
        canvas.set(halfFoot, 8, cz, "AIR");
        // West arch
        canvas.fillBox(-halfFoot, 4, cz - 1, -halfFoot, 7, cz + 1, "AIR");
        canvas.set(-halfFoot, 8, cz, "AIR");

        // Memorial Shrine Interior (Floor mosaic + Glowing Red Light)
        canvas.fillBox(-halfFoot + 1, 3, cz - halfFoot + 1, halfFoot - 1, 3, cz + halfFoot - 1, c.clayBlack());
        canvas.ring(-2, cz - 2, 2, cz + 2, 3, c.primaryRed());
        canvas.set(0, 3, cz, c.clayWhite());

        // Central Memorial Pedestal with glowing Wolfpack red beacon
        canvas.fillBox(-1, 4, cz - 1, 1, 4, cz + 1, c.roof());
        canvas.set(0, 5, cz, "SEA_LANTERN");
        canvas.set(0, 6, cz, "RED_STAINED_GLASS");
        canvas.set(0, 9, cz, "LANTERN");

        // 3. Main Tower Shaft (y = 10 to height - 12)
        int shaftTop = Math.max(12, height - 12);
        for (int y = 10; y <= shaftTop; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.clayWhite());

            canvas.set(-halfFoot, y, cz - halfFoot, c.roof());
            canvas.set(halfFoot, y, cz - halfFoot, c.roof());
            canvas.set(-halfFoot, y, cz + halfFoot, c.roof());
            canvas.set(halfFoot, y, cz + halfFoot, c.roof());

            canvas.set(0, y, cz - halfFoot, c.clayRed());
            canvas.set(0, y, cz + halfFoot, c.clayRed());
            canvas.set(-halfFoot, y, cz, c.clayRed());
            canvas.set(halfFoot, y, cz, c.clayRed());
        }

        // Architectural Belt Courses / Friezes wrapping around the tower
        int band1 = 10 + (shaftTop - 10) / 3;
        int band2 = 10 + 2 * (shaftTop - 10) / 3;
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, band1, c.primaryRed());
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, band2, c.primaryRed());

        // Slit windows with red stained glass on all 4 faces
        int winY1 = band1 - 3;
        int winY2 = band2 - 3;
        if (winY1 > 10) {
            canvas.set(0, winY1, cz + halfFoot, "RED_STAINED_GLASS_PANE");
            canvas.set(0, winY1, cz - halfFoot, "RED_STAINED_GLASS_PANE");
            canvas.set(halfFoot, winY1, cz, "RED_STAINED_GLASS_PANE");
            canvas.set(-halfFoot, winY1, cz, "RED_STAINED_GLASS_PANE");
        }
        if (winY2 > band1) {
            canvas.set(0, winY2, cz + halfFoot, "RED_STAINED_GLASS_PANE");
            canvas.set(0, winY2, cz - halfFoot, "RED_STAINED_GLASS_PANE");
            canvas.set(halfFoot, winY2, cz, "RED_STAINED_GLASS_PANE");
            canvas.set(-halfFoot, winY2, cz, "RED_STAINED_GLASS_PANE");
        }

        // 4. Belfry / Bell Chamber (y = height - 11 to height - 6)
        int belfryBottom = height - 11;
        int belfryTop = height - 6;
        canvas.fillBox(-halfFoot, belfryBottom, cz - halfFoot, halfFoot, belfryBottom, cz + halfFoot, c.roof());
        for (int y = belfryBottom + 1; y <= belfryTop; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.clayWhite());
            canvas.set(-halfFoot, y, cz - halfFoot, c.roof());
            canvas.set(halfFoot, y, cz - halfFoot, c.roof());
            canvas.set(-halfFoot, y, cz + halfFoot, c.roof());
            canvas.set(halfFoot, y, cz + halfFoot, c.roof());
        }
        // Arched belfry openings on all 4 faces
        for (int y = belfryBottom + 1; y <= belfryTop - 1; y++) {
            canvas.fillBox(-1, y, cz + halfFoot, 1, y, cz + halfFoot, "AIR");
            canvas.fillBox(-1, y, cz - halfFoot, 1, y, cz - halfFoot, "AIR");
            canvas.fillBox(halfFoot, y, cz - 1, halfFoot, y, cz + 1, "AIR");
            canvas.fillBox(-halfFoot, y, cz - 1, -halfFoot, y, cz + 1, "AIR");
        }
        // Suspended Bell & Red Accent Lighting in Belfry
        canvas.set(0, belfryTop, cz, "CHAIN");
        canvas.set(0, belfryTop - 1, cz, "CHAIN");
        canvas.set(0, belfryTop - 2, cz, "BELL");
        canvas.set(0, belfryBottom + 1, cz, "RED_STAINED_GLASS");
        canvas.set(0, belfryBottom, cz, "SEA_LANTERN");

        // 5. Four-Sided Clock (y = height - 5 to height - 1)
        int clockY = height - 3;
        for (int y = height - 5; y <= height - 1; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.clayRed());
        }
        // Clock faces on South, North, East, West
        buildClockFace(canvas, 0, clockY, cz + halfFoot, 1, 0, c); // South face
        buildClockFace(canvas, 0, clockY, cz - halfFoot, 1, 0, c); // North face
        buildClockFace(canvas, halfFoot, clockY, cz, 0, 1, c);     // East face
        buildClockFace(canvas, -halfFoot, clockY, cz, 0, 1, c);    // West face

        // 6. Cornice & Parapet (y = height to height + 2)
        canvas.ring(-halfFoot - 1, cz - halfFoot - 1, halfFoot + 1, cz + halfFoot + 1, height, c.roof());
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, height + 1, c.clayWhite());

        // Corner Pinnacles (4 corners)
        int[][] corners = {{-halfFoot, -halfFoot}, {halfFoot, -halfFoot}, {-halfFoot, halfFoot}, {halfFoot, halfFoot}};
        for (int[] corner : corners) {
            int cx = corner[0];
            int cdz = corner[1];
            canvas.set(cx, height + 1, cz + cdz, c.roof());
            canvas.set(cx, height + 2, cz + cdz, c.clayWhite());
            canvas.set(cx, height + 3, cz + cdz, "LIGHTNING_ROD");
        }

        // 7. Stepped Pyramidal Roof & Glowing Red Spire (y = height + 1 to height + 8)
        int roofY = height + 1;
        canvas.fillBox(-3, roofY, cz - 3, 3, roofY, cz + 3, c.roof());
        canvas.fillBox(-2, roofY + 1, cz - 2, 2, roofY + 1, cz + 2, c.roof());
        canvas.fillBox(-1, roofY + 2, cz - 1, 1, roofY + 2, cz + 1, c.roof());
        canvas.set(0, roofY + 3, cz, c.clayRed());

        // NC State Red Victory Lantern Spire
        canvas.set(0, roofY + 4, cz, "SEA_LANTERN");
        canvas.set(0, roofY + 5, cz, "RED_STAINED_GLASS");
        canvas.set(0, roofY + 6, cz, "POLISHED_BLACKSTONE_WALL");
        canvas.set(0, roofY + 7, cz, "LIGHTNING_ROD");

        // Landmark Sign
        canvas.sign(0, 2, cz + halfPodium + 2, 0, "NC State Wolfpack", label(cfg, "belltower", "Memorial Belltower"));
    }

    private static void buildClockFace(Canvas canvas, int cx, int cy, int cz, int dxStep, int dzStep, SceneConfig.Colors c) {
        for (int dy = -1; dy <= 1; dy++) {
            for (int d = -1; d <= 1; d++) {
                canvas.set(cx + d * dxStep, cy + dy, cz + d * dzStep, c.white());
            }
        }
        canvas.set(cx, cy, cz, c.black());
        canvas.set(cx, cy + 1, cz, c.black());
        canvas.set(cx + dxStep, cy, cz + dzStep, c.black());
    }

    // -- Wolf mascot statue ------------------------------------------------

    private static void buildWolfStatue(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int baseX = Math.max(16, r - 16);
        int baseZ = Math.max(12, r / 3);

        // 1. Multi-tiered Pedestal (y = 1 to 2)
        canvas.fillBox(baseX - 4, 1, baseZ - 5, baseX + 4, 1, baseZ + 5, c.roof());
        canvas.fillBox(baseX - 3, 2, baseZ - 4, baseX + 3, 2, baseZ + 4, c.clayRed());
        canvas.ring(baseX - 3, baseZ - 4, baseX + 3, baseZ + 4, 2, c.clayWhite());

        // 2. Sculpted Wolf Body (y = 3 to 13)
        // Four muscular paws & legs
        canvas.fillBox(baseX - 2, 3, baseZ - 3, baseX - 2, 5, baseZ - 2, c.clayBlack());
        canvas.fillBox(baseX + 2, 3, baseZ - 3, baseX + 2, 5, baseZ - 2, c.clayBlack());
        canvas.fillBox(baseX - 2, 3, baseZ + 2, baseX - 2, 5, baseZ + 3, c.clayBlack());
        canvas.fillBox(baseX + 2, 3, baseZ + 2, baseX + 2, 5, baseZ + 3, c.clayBlack());

        // Muscular Torso & Wolfpack Red Coat
        canvas.fillBox(baseX - 2, 6, baseZ - 3, baseX + 2, 8, baseZ + 3, c.primaryRed());
        // White underbelly
        canvas.fillBox(baseX - 1, 6, baseZ - 2, baseX + 1, 6, baseZ + 2, c.clayWhite());
        // Black & white blanket trim
        canvas.ring(baseX - 2, baseZ - 2, baseX + 2, baseZ + 2, 8, c.clayBlack());
        canvas.ring(baseX - 2, baseZ - 1, baseX + 2, baseZ + 1, 8, c.clayWhite());

        // Tail (curving upwards proudly)
        canvas.fillBox(baseX + 3, 7, baseZ + 2, baseX + 4, 9, baseZ + 3, c.clayRed());
        canvas.set(baseX + 4, 10, baseZ + 3, c.clayBlack());
        canvas.set(baseX + 3, 10, baseZ + 3, c.clayWhite());

        // Broad Chest & Raised Neck (tilted upward in a howl)
        canvas.fillBox(baseX - 2, 7, baseZ - 4, baseX + 2, 9, baseZ - 2, c.primaryRed());
        canvas.fillBox(baseX - 1, 8, baseZ - 5, baseX + 1, 10, baseZ - 3, c.primaryRed());
        // White chest ruff
        canvas.fillBox(baseX - 1, 7, baseZ - 4, baseX + 1, 9, baseZ - 4, c.clayWhite());

        // Howling Wolf Head & Snout (tilted upwards)
        canvas.fillBox(baseX - 1, 10, baseZ - 5, baseX + 1, 12, baseZ - 3, c.primaryRed());
        // Raised muzzle / snout
        canvas.fillBox(baseX - 1, 11, baseZ - 6, baseX + 1, 12, baseZ - 5, c.clayWhite());
        canvas.set(baseX, 12, baseZ - 6, c.black()); // nose
        // Glowing red eyes
        canvas.set(baseX - 1, 12, baseZ - 4, c.primaryRed());
        canvas.set(baseX + 1, 12, baseZ - 4, c.primaryRed());
        // Pointed alert ears
        canvas.set(baseX - 1, 13, baseZ - 3, c.clayBlack());
        canvas.set(baseX + 1, 13, baseZ - 3, c.clayBlack());
        canvas.set(baseX - 1, 14, baseZ - 3, c.clayBlack());
        canvas.set(baseX + 1, 14, baseZ - 3, c.clayBlack());

        // Plaque Sign
        canvas.sign(baseX, 1, baseZ + 6, 8, "NC State Wolfpack", label(cfg, "wolf-statue", "Howl at the Wolfpack statue"));
    }

    // -- Free Expression Tunnel-style mural walkway -----------------------

    private static void buildTunnel(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int xCenter = -Math.max(20, r - 12);
        int halfW = 4; // 9 blocks wide (-4 to +4)
        int xMin = xCenter - halfW;
        int xMax = xCenter + halfW;
        int zLen = Math.min(12, r / 3 + 2); // spans -zLen to +zLen

        // Walkway Floor
        canvas.fillBox(xMin, 0, -zLen, xMax, 0, zLen, "SMOOTH_STONE");
        for (int z = -zLen; z <= zLen; z++) {
            canvas.set(xCenter, 0, z, c.clayWhite());
            canvas.set(xMin + 1, 0, z, c.clayBlack());
            canvas.set(xMax - 1, 0, z, c.clayBlack());
        }

        // Outer Vault Walls
        for (int z = -zLen; z <= zLen; z++) {
            canvas.fillBox(xMin, 1, z, xMin, 4, z, c.brick());
            canvas.fillBox(xMax, 1, z, xMax, 4, z, c.brick());
            canvas.set(xMin + 1, 5, z, c.clayNormal());
            canvas.set(xMax - 1, 5, z, c.clayNormal());
            canvas.fillBox(xMin + 2, 6, z, xMax - 2, 6, z, c.roof());
        }

        // Arched Entry Portals (North and South)
        for (int endZ : new int[]{-zLen, zLen}) {
            canvas.ring(xMin, endZ, xMax, endZ, 1, c.clayWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 2, c.clayWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 3, c.clayWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 4, c.clayWhite());
            canvas.fillBox(xMin + 1, 1, endZ, xMax - 1, 4, endZ, "AIR"); // open portal
            canvas.set(xCenter, 5, endZ, c.clayRed()); // keystone
        }

        // Ceiling Lantern Fixtures
        for (int z = -zLen + 3; z <= zLen - 3; z += 4) {
            canvas.set(xCenter, 5, z, "CHAIN");
            canvas.set(xCenter, 4, z, "LANTERN");
        }

        // Vibrant Free Expression Mural Art on interior walls
        String[] muralMaterials = {
                c.primaryRed(), c.white(), c.black(), c.clayRed(),
                "YELLOW_CONCRETE", "ORANGE_CONCRETE", "CYAN_CONCRETE",
                c.primaryRed(), c.white(), c.black(), "LIGHT_BLUE_CONCRETE",
                "PURPLE_CONCRETE", "LIME_CONCRETE", c.clayWhite()
        };
        int idx = 0;
        for (int z = -zLen + 1; z <= zLen - 1; z++) {
            for (int y = 1; y <= 4; y++) {
                String m1 = muralMaterials[Math.floorMod(idx + y, muralMaterials.length)];
                String m2 = muralMaterials[Math.floorMod(idx * 2 + y, muralMaterials.length)];
                canvas.set(xMin + 1, y, z, m1);
                canvas.set(xMax - 1, y, z, m2);
            }
            idx++;
        }

        // Entrance Sign
        canvas.sign(xCenter, 1, zLen + 2, 0, "NC State Wolfpack", label(cfg, "tunnel", "Free Expression Tunnel"));
    }

    // -- Talley Student Union-style facade --------------------------------

    private static void buildUnionFacade(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int z = Math.max(18, r - 4);
        int halfWidth = Math.min(22, r / 2 + 6);
        int height = 18;

        // Backing Wall of Red Terracotta
        canvas.fillBox(-halfWidth, 1, z, halfWidth, height, z, c.brick());

        // Modern White Terracotta Structural Columns
        for (int x = -halfWidth; x <= halfWidth; x += 4) {
            canvas.fillBox(x, 1, z, x, height, z, c.clayWhite());
        }

        // 2-Story Glass Curtain Wall
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            if (Math.abs(x % 4) != 0) {
                // Ground floor windows
                canvas.fillBox(x, 2, z, x, 6, z, c.glass());
                // Second floor windows
                canvas.fillBox(x, 10, z, x, 14, z, c.glass());
            }
        }

        // Second-Story Student Terrace / Balcony at y = 8
        canvas.fillBox(-halfWidth, 8, z - 1, halfWidth, 8, z, c.roof());
        canvas.fillBox(-halfWidth, 9, z - 1, halfWidth, 9, z - 1, "IRON_BARS");

        // Double-Height Main Entrance Portal
        canvas.fillBox(-3, 1, z, 3, 5, z, "AIR");
        canvas.fillBox(-3, 1, z, -3, 5, z, c.clayWhite());
        canvas.fillBox(3, 1, z, 3, 5, z, c.clayWhite());
        // Glass double doors
        canvas.set(-1, 1, z, c.glass());
        canvas.set(0, 1, z, c.glass());
        canvas.set(1, 1, z, c.glass());
        canvas.set(-1, 2, z, c.glass());
        canvas.set(0, 2, z, c.glass());
        canvas.set(1, 2, z, c.glass());

        // Cantilevered Modern Entrance Canopy
        canvas.fillBox(-4, 5, z - 2, 4, 5, z, c.roof());
        canvas.fillBox(-3, 6, z - 2, 3, 6, z, c.clayRed());

        // Wolfpack Header Parapet & Cornice
        canvas.fillBox(-halfWidth, height - 2, z, halfWidth, height, z, c.primaryRed());
        canvas.fillBox(-halfWidth, height + 1, z - 1, halfWidth, height + 1, z, c.roof());

        // Landmark Sign
        canvas.sign(0, 1, z - 4, 0, "NC State Wolfpack", label(cfg, "union-facade", "Talley Student Union"));
    }

    // -- D. H. Hill Jr. Library-style facade ------------------------------

    private static void buildLibraryFacade(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int x = Math.max(18, r - 4);
        int halfWidth = Math.min(22, r / 2 + 6);
        int height = 20;

        // Backing Wall of Red Terracotta
        canvas.fillBox(x, 1, -halfWidth, x, height, halfWidth, c.brick());

        // Collegiate Vertical Louvers & Window Pillars
        for (int z = -halfWidth; z <= halfWidth; z += 4) {
            canvas.fillBox(x, 1, z, x, height, z, c.clayWhite());
        }

        // High Vertical Glass Ribbon Windows with Bookshelves Visible Inside
        for (int z = -halfWidth + 1; z <= halfWidth - 1; z++) {
            if (Math.abs(z % 4) != 0) {
                canvas.fillBox(x, 2, z, x, height - 4, z, c.glass());
                // Bookshelves behind the facade
                canvas.set(x + 1, 2, z, "BOOKSHELF");
                canvas.set(x + 1, 3, z, "BOOKSHELF");
                canvas.set(x + 1, 8, z, "BOOKSHELF");
                canvas.set(x + 1, 9, z, "BOOKSHELF");
            }
        }

        // Grand Library Entrance Portico (centered at z = 0)
        canvas.fillBox(x, 1, -3, x, 6, 3, "AIR");
        canvas.fillBox(x, 1, -3, x, 6, -3, c.clayWhite());
        canvas.fillBox(x, 1, 3, x, 6, 3, c.clayWhite());
        // Glass entrance doors
        canvas.fillBox(x, 1, -1, x, 3, 1, c.glass());
        // Entrance pediment
        canvas.fillBox(x - 1, 6, -4, x, 6, 4, c.roof());
        canvas.fillBox(x - 1, 7, -3, x, 7, 3, c.clayRed());

        // Collegiate Parapet & Wolfpack Header Frieze
        canvas.fillBox(x, height - 2, -halfWidth, x, height, halfWidth, c.primaryRed());
        canvas.fillBox(x - 1, height + 1, -halfWidth, x, height + 1, halfWidth, c.roof());

        // Landmark Sign
        canvas.sign(x - 4, 1, 0, 12, "NC State Wolfpack", label(cfg, "library-facade", "D. H. Hill Jr. Library"));
    }

    private static String label(SceneConfig cfg, String key, String fallback) {
        String custom = cfg.signLabels().get(key);
        return (custom == null || custom.isBlank()) ? fallback : custom;
    }

    /** Accumulates block/sign placements. Package-private, plain Java. */
    private static final class Canvas {
        private final List<BlockPlacement> blocks = new ArrayList<>();
        private final List<SignPlacement> signs = new ArrayList<>();

        private int minX = Integer.MAX_VALUE;
        private int maxX = Integer.MIN_VALUE;
        private int minY = Integer.MAX_VALUE;
        private int maxY = Integer.MIN_VALUE;
        private int minZ = Integer.MAX_VALUE;
        private int maxZ = Integer.MIN_VALUE;

        void set(int x, int y, int z, String material) {
            blocks.add(new BlockPlacement(x, y, z, material));
            grow(x, y, z);
        }

        private void grow(int x, int y, int z) {
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
            minZ = Math.min(minZ, z);
            maxZ = Math.max(maxZ, z);
        }

        void fillBox(int x1, int y1, int z1, int x2, int y2, int z2, String material) {
            int xMin = Math.min(x1, x2), xMax = Math.max(x1, x2);
            int yMin = Math.min(y1, y2), yMax = Math.max(y1, y2);
            int zMin = Math.min(z1, z2), zMax = Math.max(z1, z2);
            for (int x = xMin; x <= xMax; x++) {
                for (int y = yMin; y <= yMax; y++) {
                    for (int z = zMin; z <= zMax; z++) {
                        set(x, y, z, material);
                    }
                }
            }
        }

        /** The rectangular outline (perimeter only) of a box at a single height. */
        void ring(int x1, int z1, int x2, int z2, int y, String material) {
            int xMin = Math.min(x1, x2), xMax = Math.max(x1, x2);
            int zMin = Math.min(z1, z2), zMax = Math.max(z1, z2);
            for (int x = xMin; x <= xMax; x++) {
                set(x, y, zMin, material);
                set(x, y, zMax, material);
            }
            for (int z = zMin; z <= zMax; z++) {
                set(xMin, y, z, material);
                set(xMax, y, z, material);
            }
        }

        void sign(int x, int y, int z, int rotation, String... lines) {
            signs.add(new SignPlacement(x, y, z, rotation, List.of(lines)));
            grow(x, y, z);
        }

        Scene toScene() {
            boolean empty = blocks.isEmpty() && signs.isEmpty();
            Bounds bounds = empty
                    ? new Bounds(0, 0, 0, 0, 0, 0)
                    : new Bounds(minX, maxX, minY, maxY, minZ, maxZ);
            return new Scene(List.copyOf(blocks), List.copyOf(signs), bounds);
        }
    }
}
