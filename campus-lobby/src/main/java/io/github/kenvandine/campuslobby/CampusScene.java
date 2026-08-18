package io.github.kenvandine.campuslobby;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally generates an NC State Wolfpack-themed enclosed lobby scene:
 * an enclosed grand campus arena with four decorated walls, high coffered
 * ceiling with chandeliers & skylights, a central concrete Brickyard plaza
 * with paw prints & Block 'S' medallion, a Memorial Belltower centerpiece,
 * a howling wolf-mascot statue, a Free Expression Tunnel, and stylized
 * student-union and library facades — all in vibrant Wolfpack Red, White,
 * and Black concrete.
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
        if (cfg.include().enclosure()) {
            buildEnclosure(canvas, cfg);
        }
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

    // -- Enclosure: 4 Decorated Perimeter Walls & Grand Ceiling ----------

    private static void buildEnclosure(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        int height = cfg.towerHeight();
        SceneConfig.Colors c = cfg.colors();

        int ceilingY = Math.max(48, height + 12);

        // 1. Four Decorated Boundary Walls (North, South, East, West)
        for (int y = 1; y <= ceilingY; y++) {
            for (int x = -r; x <= r; x++) {
                decorateWallBlock(canvas, x, y, -r, true, y, ceilingY, c); // North wall
                decorateWallBlock(canvas, x, y, r, true, y, ceilingY, c);  // South wall
            }
            for (int z = -r; z <= r; z++) {
                decorateWallBlock(canvas, -r, y, z, false, y, ceilingY, c); // West wall
                decorateWallBlock(canvas, r, y, z, false, y, ceilingY, c);  // East wall
            }
        }

        // Add Wolfpack Spirit Murals on the walls
        drawTextOnNorthWall(canvas, -r, 26, c);
        drawTextOnSouthWall(canvas, r, 26, c);
        drawTextOnEastWall(canvas, r, 26, c);
        drawTextOnWestWall(canvas, -r, 26, c);

        // Wall Banners along the perimeter
        for (int d = -r + 4; d <= r - 4; d += 6) {
            canvas.set(d, 10, -r + 1, (Math.abs(d) % 12 == 0) ? "RED_BANNER" : "WHITE_BANNER");
            canvas.set(d, 10, r - 1, (Math.abs(d) % 12 == 0) ? "RED_BANNER" : "WHITE_BANNER");
            canvas.set(-r + 1, 10, d, (Math.abs(d) % 12 == 0) ? "RED_BANNER" : "WHITE_BANNER");
            canvas.set(r - 1, 10, d, (Math.abs(d) % 12 == 0) ? "RED_BANNER" : "WHITE_BANNER");
        }

        // 2. High Vaulted Atrium Ceiling with Coffers, Trusses & Skylights
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean isBeam = (Math.abs(x) % 6 == 0) || (Math.abs(z) % 6 == 0);
                boolean isMainCross = (Math.abs(x) <= 2) || (Math.abs(z) <= 2);
                boolean isDiagonalCross = (Math.abs(x) == Math.abs(z));

                if (isMainCross) {
                    if (Math.abs(x) == 0 || Math.abs(z) == 0) {
                        canvas.set(x, ceilingY, z, "WHITE_STAINED_GLASS_PANE");
                        if (Math.abs(x) % 8 == 0 && Math.abs(z) % 8 == 0) {
                            canvas.set(x, ceilingY + 1, z, "SEA_LANTERN");
                        }
                    } else {
                        canvas.set(x, ceilingY, z, c.roof());
                    }
                } else if (isBeam) {
                    canvas.set(x, ceilingY, z, c.roof());
                    canvas.set(x, ceilingY - 1, z, c.concreteBlack());
                } else if (isDiagonalCross) {
                    canvas.set(x, ceilingY, z, c.primaryRed());
                } else {
                    int panelTile = ((Math.floorDiv(x, 6) + Math.floorDiv(z, 6)) & 1);
                    String panelMat = (panelTile == 0) ? c.concreteBlack() : c.concreteRed();
                    canvas.set(x, ceilingY, z, panelMat);
                }
            }
        }

        // Outer roof trim
        canvas.ring(-r, -r, r, r, ceilingY + 1, c.roof());
        canvas.ring(-r, -r, r, r, ceilingY + 2, c.concreteBlack());

        // Hanging grand chandeliers from ceiling
        int[] chandelierDistances = {12, 22};
        for (int cx : chandelierDistances) {
            for (int cz : chandelierDistances) {
                placeChandelier(canvas, cx, ceilingY - 1, cz);
                placeChandelier(canvas, -cx, ceilingY - 1, cz);
                placeChandelier(canvas, cx, ceilingY - 1, -cz);
                placeChandelier(canvas, -cx, ceilingY - 1, -cz);
            }
        }
    }

    private static void decorateWallBlock(Canvas canvas, int x, int y, int z, boolean isNorthSouth, int yPos, int maxY, SceneConfig.Colors c) {
        int coordinate = isNorthSouth ? x : z;
        boolean isPilaster = (Math.abs(coordinate) % 6 == 0) || (Math.abs(coordinate) == Math.abs(isNorthSouth ? z : x));

        // Lower wainscoting (y = 1 to 4)
        if (yPos <= 3) {
            canvas.set(x, y, z, c.roof());
        } else if (yPos == 4) {
            canvas.set(x, y, z, isPilaster ? c.roof() : c.concreteBlack());
        } else if (yPos == 5) {
            canvas.set(x, y, z, c.concreteWhite());
        } else if (yPos >= maxY - 3) {
            // Upper frieze / cornice
            if (yPos == maxY - 3) {
                canvas.set(x, y, z, c.concreteWhite());
            } else if (yPos == maxY - 2 || yPos == maxY - 1) {
                canvas.set(x, y, z, c.primaryRed());
            } else {
                canvas.set(x, y, z, c.roof());
            }
        } else {
            // Mid wall field (y = 6 to maxY - 4)
            if (isPilaster) {
                canvas.set(x, y, z, c.roof());
                if (yPos % 8 == 0) {
                    canvas.set(x, yPos, isNorthSouth ? (z > 0 ? z - 1 : z + 1) : z, "LANTERN");
                }
            } else {
                int bayPos = Math.floorMod(coordinate, 6);
                boolean isWindowSlot = (bayPos >= 2 && bayPos <= 4) && (yPos >= 14 && yPos <= maxY - 8);
                if (isWindowSlot) {
                    if (bayPos == 3) {
                        canvas.set(x, y, z, "RED_STAINED_GLASS_PANE");
                    } else {
                        canvas.set(x, y, z, "BLACK_STAINED_GLASS_PANE");
                    }
                } else {
                    int pattern = (yPos / 3 + Math.abs(coordinate) / 3) % 2;
                    canvas.set(x, y, z, (pattern == 0) ? c.concreteRed() : c.concreteBlack());
                }
            }
        }
    }

    private static void drawTextOnNorthWall(Canvas canvas, int z, int yBase, SceneConfig.Colors c) {
        canvas.fillBox(-16, yBase - 1, z, 16, yBase + 5, z, c.concreteBlack());
        canvas.ring(-17, z, 17, z, yBase - 2, c.concreteRed());
        canvas.fillBox(-3, yBase, z, 3, yBase + 4, z, c.primaryRed());
        canvas.fillBox(-2, yBase + 1, z, 2, yBase + 3, z, c.white());
        canvas.set(0, yBase + 2, z, c.concreteBlack());
    }

    private static void drawTextOnSouthWall(Canvas canvas, int z, int yBase, SceneConfig.Colors c) {
        canvas.fillBox(-14, yBase - 1, z, 14, yBase + 4, z, c.concreteBlack());
        canvas.fillBox(-12, yBase, z, 12, yBase + 3, z, c.primaryRed());
        canvas.fillBox(-10, yBase + 1, z, -2, yBase + 2, z, c.white());
        canvas.fillBox(2, yBase + 1, z, 10, yBase + 2, z, c.white());
    }

    private static void drawTextOnEastWall(Canvas canvas, int x, int yBase, SceneConfig.Colors c) {
        canvas.fillBox(x, yBase - 1, -12, x, yBase + 4, 12, c.concreteBlack());
        canvas.fillBox(x, yBase, -10, x, yBase + 3, 10, c.primaryRed());
        canvas.fillBox(x, yBase + 1, -8, x, yBase + 2, 8, c.white());
    }

    private static void drawTextOnWestWall(Canvas canvas, int x, int yBase, SceneConfig.Colors c) {
        canvas.fillBox(x, yBase - 1, -12, x, yBase + 4, 12, c.concreteBlack());
        canvas.fillBox(x, yBase, -10, x, yBase + 3, 10, c.primaryRed());
        canvas.fillBox(x, yBase + 1, -8, x, yBase + 2, 8, c.white());
    }

    private static void placeChandelier(Canvas canvas, int x, int topY, int z) {
        for (int y = topY; y >= topY - 3; y--) {
            canvas.set(x, y, z, "CHAIN");
        }
        int lightY = topY - 4;
        canvas.set(x, lightY, z, "POLISHED_BLACKSTONE");
        canvas.set(x + 1, lightY, z, "LANTERN");
        canvas.set(x - 1, lightY, z, "LANTERN");
        canvas.set(x, lightY, z + 1, "LANTERN");
        canvas.set(x, lightY, z - 1, "LANTERN");
        canvas.set(x, lightY - 1, z, "SEA_LANTERN");
        canvas.set(x, lightY - 2, z, "RED_STAINED_GLASS");
    }

    // -- The Brickyard: central plaza -----------------------------------

    private static void buildPlaza(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        // The Brickyard: High-contrast pure Red Concrete & Black Concrete 4x4 parquet paving!
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int tile = ((Math.floorDiv(x, 4) + Math.floorDiv(z, 4)) & 1);
                String paver = (tile == 0) ? c.concreteRed() : c.concreteBlack();
                canvas.set(x, 0, z, paver);
            }
        }

        // Concentric perimeter borders in bold Wolfpack Red, Black, and White
        canvas.ring(-r, -r, r, r, 0, c.concreteBlack());
        canvas.ring(-(r - 1), -(r - 1), r - 1, r - 1, 0, c.roof());
        canvas.ring(-(r - 2), -(r - 2), r - 2, r - 2, 0, c.primaryRed());
        canvas.ring(-(r - 3), -(r - 3), r - 3, r - 3, 0, c.concreteWhite());
        canvas.ring(-(r - 4), -(r - 4), r - 4, r - 4, 0, c.concreteBlack());

        // Inlaid Wolfpack Paw Prints in all four plaza quadrants
        placePawPrint(canvas, 16, 16, c);
        placePawPrint(canvas, -16, 16, c);
        placePawPrint(canvas, 16, -16, c);
        placePawPrint(canvas, -16, -16, c);

        placePawPrint(canvas, 22, 8, c);
        placePawPrint(canvas, -22, 8, c);
        placePawPrint(canvas, 22, -8, c);
        placePawPrint(canvas, -22, -8, c);

        // Grand central NC State "Block S" / Wolfpack medallion
        int medR = 8;
        for (int x = -medR; x <= medR; x++) {
            for (int z = -medR; z <= medR; z++) {
                int dist = Math.abs(x) + Math.abs(z);
                if (dist <= 10) {
                    canvas.set(x, 0, z, c.primaryRed());
                }
                if (dist == 10 || (Math.abs(x) == medR && Math.abs(z) <= 2) || (Math.abs(z) == medR && Math.abs(x) <= 2)) {
                    canvas.set(x, 0, z, c.concreteBlack());
                }
                if (dist == 9) {
                    canvas.set(x, 0, z, c.concreteWhite());
                }
            }
        }

        // Block S inlay in the medallion (white with bold black shadow outline)
        for (int x = -4; x <= 4; x++) {
            for (int z = -6; z <= 5; z++) {
                if (isBlockS(x, z)) {
                    for (int dx = -1; dx <= 1; dx++) {
                        for (int dz = -1; dz <= 1; dz++) {
                            if (!isBlockS(x + dx, z + dz)) {
                                canvas.set(x + dx, 0, z + dz, c.concreteBlack());
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
                    canvas.set(x, 0, dz, c.concreteWhite());
                }
                canvas.set(x, 0, -2, c.concreteBlack());
                canvas.set(x, 0, 2, c.concreteBlack());
                canvas.set(x, 0, -3, c.primaryRed());
                canvas.set(x, 0, 3, c.primaryRed());
            }
        }
        for (int z = -pLimit; z <= pLimit; z++) {
            if (Math.abs(z) > medR - 2) {
                for (int dx = -1; dx <= 1; dx++) {
                    canvas.set(dx, 0, z, c.concreteWhite());
                }
                canvas.set(-2, 0, z, c.concreteBlack());
                canvas.set(2, 0, z, c.concreteBlack());
                canvas.set(-3, 0, z, c.primaryRed());
                canvas.set(3, 0, z, c.primaryRed());
            }
        }

        // Diagonal Walkways connecting center to corners
        for (int d = medR; d <= pLimit - 3; d++) {
            canvas.set(d, 0, d, c.concreteWhite());
            canvas.set(-d, 0, d, c.concreteWhite());
            canvas.set(d, 0, -d, c.concreteWhite());
            canvas.set(-d, 0, -d, c.concreteWhite());
        }

        // 4 Wolfpack Spirit Fire Bowls / Victory Beacons around the medallion
        placeSpiritBeacon(canvas, 7, 7, c);
        placeSpiritBeacon(canvas, -7, 7, c);
        placeSpiritBeacon(canvas, 7, -7, c);
        placeSpiritBeacon(canvas, -7, -7, c);

        // Campus Lampposts along the grand avenues
        int[] lampDistances = {12, 20, 26};
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
        int planterD = 14;
        placePlanter(canvas, planterD, planterD, c);
        placePlanter(canvas, -planterD, planterD, c);
        placePlanter(canvas, planterD, -planterD, c);
        placePlanter(canvas, -planterD, -planterD, c);

        // Wolfpack Flagpoles at the 4 corners
        int flagOffset = Math.max(6, r - 5);
        placeFlagpole(canvas, flagOffset, flagOffset, c.primaryRed(), true);
        placeFlagpole(canvas, -flagOffset, flagOffset, c.white(), false);
        placeFlagpole(canvas, flagOffset, -flagOffset, c.white(), false);
        placeFlagpole(canvas, -flagOffset, -flagOffset, c.primaryRed(), true);
    }

    private static void placePawPrint(Canvas canvas, int cx, int cz, SceneConfig.Colors c) {
        canvas.fillBox(cx - 1, 0, cz, cx + 1, 0, cz + 1, c.concreteWhite());
        canvas.set(cx - 2, 0, cz - 2, c.primaryRed());
        canvas.set(cx - 1, 0, cz - 3, c.primaryRed());
        canvas.set(cx + 1, 0, cz - 3, c.primaryRed());
        canvas.set(cx + 2, 0, cz - 2, c.primaryRed());
    }

    private static void placeSpiritBeacon(Canvas canvas, int x, int z, SceneConfig.Colors c) {
        canvas.set(x, 1, z, c.roof());
        canvas.set(x, 2, z, "SEA_LANTERN");
        canvas.set(x, 3, z, "RED_STAINED_GLASS");
        canvas.set(x + 1, 2, z, "IRON_BARS");
        canvas.set(x - 1, 2, z, "IRON_BARS");
        canvas.set(x, 2, z + 1, "IRON_BARS");
        canvas.set(x, 2, z - 1, "IRON_BARS");
        canvas.set(x, 4, z, "POLISHED_BLACKSTONE_SLAB");
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
        canvas.ring(cx - 2, cz - 2, cx + 2, cz + 2, 1, c.concreteBlack());
        canvas.ring(cx - 2, cz - 2, cx + 2, cz + 2, 2, c.primaryRed());
        canvas.fillBox(cx - 1, 1, cz - 1, cx + 1, 2, cz + 1, "MOSS_BLOCK");
        canvas.set(cx, 3, cz, "AZALEA_LEAVES");
        canvas.set(cx - 1, 3, cz, "RED_TULIP");
        canvas.set(cx + 1, 3, cz, "WHITE_TULIP");
        canvas.set(cx, 3, cz - 1, "RED_TULIP");
        canvas.set(cx, 3, cz + 1, "WHITE_TULIP");

        canvas.fillBox(cx - 1, 1, cz - 3, cx + 1, 1, cz - 3, "SMOOTH_STONE_SLAB");
        canvas.fillBox(cx - 1, 1, cz + 3, cx + 1, 1, cz + 3, "SMOOTH_STONE_SLAB");
    }

    private static void placeFlagpole(Canvas canvas, int x, int z, String wolfpackColor, boolean isRed) {
        String banner = isRed ? "RED_BANNER" : "WHITE_BANNER";
        canvas.set(x, 1, z, "POLISHED_BLACKSTONE");
        canvas.fillBox(x, 2, z, x, 6, z, "IRON_BARS");
        canvas.set(x, 7, z, "POLISHED_BLACKSTONE");
        canvas.set(x, 6, z + 1, banner);
        canvas.set(x, 5, z + 1, banner);
    }

    // -- Memorial Belltower ----------------------------------------------

    private static void buildBelltower(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        int height = cfg.towerHeight();
        SceneConfig.Colors c = cfg.colors();

        int halfFoot = 3; // 7x7 shaft
        int halfPodium = 5; // 11x11 base podium
        int cz = -Math.max(halfPodium + 3, r - 10); // north of plaza center

        // 1. Grand Stepped Podium (y = 1 to 3)
        canvas.fillBox(-halfPodium - 1, 1, cz - halfPodium - 1, halfPodium + 1, 1, cz + halfPodium + 1, c.roof());
        canvas.fillBox(-halfPodium, 2, cz - halfPodium, halfPodium, 2, cz + halfPodium, c.concreteWhite());
        canvas.fillBox(-halfFoot - 1, 3, cz - halfFoot - 1, halfFoot + 1, 3, cz + halfFoot + 1, c.primaryRed());

        // Corner plinth pedestals
        canvas.fillBox(-halfPodium, 2, cz - halfPodium, -halfPodium + 1, 3, cz - halfPodium + 1, c.concreteBlack());
        canvas.fillBox(halfPodium - 1, 2, cz - halfPodium, halfPodium, 3, cz - halfPodium + 1, c.concreteBlack());
        canvas.fillBox(-halfPodium, 2, cz + halfPodium - 1, -halfPodium + 1, 3, cz + halfPodium, c.concreteBlack());
        canvas.fillBox(halfPodium - 1, 2, cz + halfPodium - 1, halfPodium, 3, cz + halfPodium, c.concreteBlack());

        // 2. Ground Floor Memorial Shrine & Rotunda (y = 4 to 8)
        for (int y = 4; y <= 8; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.concreteWhite());
            canvas.set(-halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(-halfFoot, y, cz + halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz + halfFoot, c.concreteBlack());
        }

        // 4 Grand Archway entrances (South, North, East, West)
        canvas.fillBox(-1, 4, cz + halfFoot, 1, 6, cz + halfFoot, "AIR");
        canvas.set(0, 7, cz + halfFoot, "AIR");
        canvas.fillBox(-1, 4, cz - halfFoot, 1, 6, cz - halfFoot, "AIR");
        canvas.set(0, 7, cz - halfFoot, "AIR");
        canvas.fillBox(halfFoot, 4, cz - 1, halfFoot, 6, cz + 1, "AIR");
        canvas.set(halfFoot, 7, cz, "AIR");
        canvas.fillBox(-halfFoot, 4, cz - 1, -halfFoot, 6, cz + 1, "AIR");
        canvas.set(-halfFoot, 7, cz, "AIR");

        // Memorial Shrine Interior (Floor mosaic + Glowing Red Light)
        canvas.fillBox(-halfFoot + 1, 3, cz - halfFoot + 1, halfFoot - 1, 3, cz + halfFoot - 1, c.concreteBlack());
        canvas.ring(-1, cz - 1, 1, cz + 1, 3, c.primaryRed());
        canvas.set(0, 3, cz, c.concreteWhite());

        // Central Memorial Pedestal with glowing Wolfpack red beacon
        canvas.set(0, 4, cz, c.roof());
        canvas.set(0, 5, cz, "SEA_LANTERN");
        canvas.set(0, 6, cz, "RED_STAINED_GLASS");
        canvas.set(0, 8, cz, "LANTERN");

        // 3. Main Tower Shaft (y = 9 to height - 8)
        int shaftTop = Math.max(10, height - 8);
        for (int y = 9; y <= shaftTop; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.concreteWhite());

            canvas.set(-halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(-halfFoot, y, cz + halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz + halfFoot, c.concreteBlack());

            canvas.set(0, y, cz - halfFoot, c.primaryRed());
            canvas.set(0, y, cz + halfFoot, c.primaryRed());
            canvas.set(-halfFoot, y, cz, c.primaryRed());
            canvas.set(halfFoot, y, cz, c.primaryRed());
        }

        // Architectural Belt Courses
        int band1 = 9 + (shaftTop - 9) / 3;
        int band2 = 9 + 2 * (shaftTop - 9) / 3;
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, band1, c.primaryRed());
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, band2, c.concreteBlack());

        // Slit windows with red stained glass
        if (band1 - 2 > 9) {
            canvas.set(0, band1 - 2, cz + halfFoot, "RED_STAINED_GLASS_PANE");
            canvas.set(0, band1 - 2, cz - halfFoot, "RED_STAINED_GLASS_PANE");
            canvas.set(halfFoot, band1 - 2, cz, "RED_STAINED_GLASS_PANE");
            canvas.set(-halfFoot, band1 - 2, cz, "RED_STAINED_GLASS_PANE");
        }

        // 4. Belfry / Bell Chamber (y = height - 7 to height - 4)
        int belfryBottom = height - 7;
        int belfryTop = height - 4;
        canvas.fillBox(-halfFoot, belfryBottom, cz - halfFoot, halfFoot, belfryBottom, cz + halfFoot, c.roof());
        for (int y = belfryBottom + 1; y <= belfryTop; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.concreteWhite());
            canvas.set(-halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(-halfFoot, y, cz + halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz + halfFoot, c.concreteBlack());
        }
        // Arched belfry openings
        for (int y = belfryBottom + 1; y <= belfryTop - 1; y++) {
            canvas.fillBox(-1, y, cz + halfFoot, 1, y, cz + halfFoot, "AIR");
            canvas.fillBox(-1, y, cz - halfFoot, 1, y, cz - halfFoot, "AIR");
            canvas.fillBox(halfFoot, y, cz - 1, halfFoot, y, cz + 1, "AIR");
            canvas.fillBox(-halfFoot, y, cz - 1, -halfFoot, y, cz + 1, "AIR");
        }
        // Suspended Bell & Red Accent Lighting
        canvas.set(0, belfryTop, cz, "CHAIN");
        canvas.set(0, belfryTop - 1, cz, "BELL");
        canvas.set(0, belfryBottom + 1, cz, "RED_STAINED_GLASS");
        canvas.set(0, belfryBottom, cz, "SEA_LANTERN");

        // 5. Four-Sided Clock (y = height - 3 to height - 1)
        int clockY = height - 2;
        for (int y = height - 3; y <= height - 1; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.primaryRed());
        }
        buildClockFace(canvas, 0, clockY, cz + halfFoot, 1, 0, c);
        buildClockFace(canvas, 0, clockY, cz - halfFoot, 1, 0, c);
        buildClockFace(canvas, halfFoot, clockY, cz, 0, 1, c);
        buildClockFace(canvas, -halfFoot, clockY, cz, 0, 1, c);

        // 6. Cornice & Parapet (y = height to height + 2)
        canvas.ring(-halfFoot - 1, cz - halfFoot - 1, halfFoot + 1, cz + halfFoot + 1, height, c.roof());
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, height + 1, c.concreteWhite());

        // Corner Pinnacles
        int[][] corners = {{-halfFoot, -halfFoot}, {halfFoot, -halfFoot}, {-halfFoot, halfFoot}, {halfFoot, halfFoot}};
        for (int[] corner : corners) {
            int cx = corner[0];
            int cdz = corner[1];
            canvas.set(cx, height + 1, cz + cdz, c.concreteBlack());
            canvas.set(cx, height + 2, cz + cdz, c.concreteWhite());
            canvas.set(cx, height + 3, cz + cdz, "LIGHTNING_ROD");
        }

        // 7. Stepped Pyramidal Roof & Glowing Red Spire (y = height + 1 to height + 7)
        int roofY = height + 1;
        canvas.fillBox(-2, roofY, cz - 2, 2, roofY, cz + 2, c.roof());
        canvas.fillBox(-1, roofY + 1, cz - 1, 1, roofY + 1, cz + 1, c.roof());
        canvas.set(0, roofY + 2, cz, c.primaryRed());

        // Victory Spire
        canvas.set(0, roofY + 3, cz, "SEA_LANTERN");
        canvas.set(0, roofY + 4, cz, "RED_STAINED_GLASS");
        canvas.set(0, roofY + 5, cz, "POLISHED_BLACKSTONE_WALL");
        canvas.set(0, roofY + 6, cz, "LIGHTNING_ROD");

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

        int baseX = Math.max(12, r - 14);
        int baseZ = Math.max(10, r / 3);

        // 1. Multi-tiered Pedestal (y = 1 to 2)
        canvas.fillBox(baseX - 3, 1, baseZ - 4, baseX + 3, 1, baseZ + 4, c.roof());
        canvas.fillBox(baseX - 2, 2, baseZ - 3, baseX + 2, 2, baseZ + 3, c.primaryRed());
        canvas.ring(baseX - 2, baseZ - 3, baseX + 2, baseZ + 3, 2, c.concreteWhite());

        // 2. Sculpted Wolf Body in pure Concrete (y = 3 to 12)
        // Four muscular legs & paws in black concrete
        canvas.fillBox(baseX - 2, 3, baseZ - 2, baseX - 2, 4, baseZ - 2, c.concreteBlack());
        canvas.fillBox(baseX + 2, 3, baseZ - 2, baseX + 2, 4, baseZ - 2, c.concreteBlack());
        canvas.fillBox(baseX - 2, 3, baseZ + 2, baseX - 2, 4, baseZ + 2, c.concreteBlack());
        canvas.fillBox(baseX + 2, 3, baseZ + 2, baseX + 2, 4, baseZ + 2, c.concreteBlack());

        // Muscular Torso & Wolfpack Red Coat
        canvas.fillBox(baseX - 2, 5, baseZ - 2, baseX + 2, 7, baseZ + 2, c.primaryRed());
        // White underbelly
        canvas.fillBox(baseX - 1, 5, baseZ - 1, baseX + 1, 5, baseZ + 1, c.concreteWhite());
        // Black blanket trim
        canvas.ring(baseX - 2, baseZ - 2, baseX + 2, baseZ + 2, 7, c.concreteBlack());

        // Tail (curving upwards proudly)
        canvas.fillBox(baseX + 3, 6, baseZ + 1, baseX + 3, 8, baseZ + 2, c.primaryRed());
        canvas.set(baseX + 3, 9, baseZ + 2, c.concreteBlack());
        canvas.set(baseX + 2, 9, baseZ + 2, c.concreteWhite());

        // Broad Chest & Raised Neck (tilted upward in a howl)
        canvas.fillBox(baseX - 1, 6, baseZ - 3, baseX + 1, 8, baseZ - 2, c.primaryRed());
        canvas.fillBox(baseX - 1, 7, baseZ - 4, baseX + 1, 9, baseZ - 3, c.primaryRed());
        // White chest ruff
        canvas.fillBox(baseX - 1, 6, baseZ - 3, baseX + 1, 8, baseZ - 3, c.concreteWhite());

        // Howling Wolf Head & Snout (tilted upwards)
        canvas.fillBox(baseX - 1, 9, baseZ - 4, baseX + 1, 10, baseZ - 3, c.primaryRed());
        // Raised muzzle
        canvas.fillBox(baseX - 1, 9, baseZ - 5, baseX + 1, 10, baseZ - 4, c.concreteWhite());
        canvas.set(baseX, 10, baseZ - 5, c.black()); // nose
        // Glowing red eyes
        canvas.set(baseX - 1, 10, baseZ - 3, c.primaryRed());
        canvas.set(baseX + 1, 10, baseZ - 3, c.primaryRed());
        // Pointed alert ears
        canvas.set(baseX - 1, 11, baseZ - 3, c.concreteBlack());
        canvas.set(baseX + 1, 11, baseZ - 3, c.concreteBlack());
        canvas.set(baseX - 1, 12, baseZ - 3, c.concreteBlack());
        canvas.set(baseX + 1, 12, baseZ - 3, c.concreteBlack());

        // Plaque Sign
        canvas.sign(baseX, 1, baseZ + 5, 8, "NC State Wolfpack", label(cfg, "wolf-statue", "Howl at the Wolfpack statue"));
    }

    // -- Free Expression Tunnel-style mural walkway -----------------------

    private static void buildTunnel(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int xCenter = -Math.max(16, r - 12);
        int halfW = 3;
        int xMin = xCenter - halfW;
        int xMax = xCenter + halfW;
        int zLen = Math.min(8, r / 4 + 1);

        // Walkway Floor
        canvas.fillBox(xMin, 0, -zLen, xMax, 0, zLen, "POLISHED_BLACKSTONE");
        for (int z = -zLen; z <= zLen; z++) {
            canvas.set(xCenter, 0, z, c.concreteWhite());
            canvas.set(xMin + 1, 0, z, c.primaryRed());
            canvas.set(xMax - 1, 0, z, c.primaryRed());
        }

        // Outer Vault Walls
        for (int z = -zLen; z <= zLen; z++) {
            canvas.fillBox(xMin, 1, z, xMin, 4, z, c.concreteBlack());
            canvas.fillBox(xMax, 1, z, xMax, 4, z, c.concreteBlack());
            canvas.set(xMin + 1, 5, z, c.primaryRed());
            canvas.set(xMax - 1, 5, z, c.primaryRed());
            canvas.fillBox(xMin + 2, 5, z, xMax - 2, 5, z, c.roof());
        }

        // Arched Entry Portals (North and South)
        for (int endZ : new int[]{-zLen, zLen}) {
            canvas.ring(xMin, endZ, xMax, endZ, 1, c.concreteWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 2, c.concreteWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 3, c.concreteWhite());
            canvas.fillBox(xMin + 1, 1, endZ, xMax - 1, 3, endZ, "AIR");
            canvas.set(xCenter, 4, endZ, c.primaryRed());
        }

        // Ceiling Lantern Fixtures
        for (int z = -zLen + 2; z <= zLen - 2; z += 3) {
            canvas.set(xCenter, 4, z, "CHAIN");
            canvas.set(xCenter, 3, z, "LANTERN");
        }

        // Vibrant Free Expression Mural Art on interior walls
        String[] muralMaterials = {
                c.primaryRed(), c.white(), c.black(),
                "YELLOW_CONCRETE", "ORANGE_CONCRETE", "CYAN_CONCRETE",
                c.primaryRed(), c.white(), c.black(), "LIGHT_BLUE_CONCRETE",
                "PURPLE_CONCRETE", "LIME_CONCRETE", c.concreteWhite()
        };
        int idx = 0;
        for (int z = -zLen + 1; z <= zLen - 1; z++) {
            for (int y = 1; y <= 3; y++) {
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

        int z = Math.max(14, r - 4);
        int halfWidth = Math.min(18, r / 2 + 3);
        int height = 16;

        // Backing Wall of Red Concrete
        canvas.fillBox(-halfWidth, 1, z, halfWidth, height, z, c.primaryRed());

        // Modern White Concrete Structural Columns
        for (int x = -halfWidth; x <= halfWidth; x += 4) {
            canvas.fillBox(x, 1, z, x, height, z, c.concreteWhite());
        }

        // 2-Story Glass Curtain Wall
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            if (Math.abs(x % 4) != 0) {
                canvas.fillBox(x, 2, z, x, 5, z, c.glass());
                canvas.fillBox(x, 9, z, x, 12, z, c.glass());
            }
        }

        // Second-Story Student Terrace / Balcony at y = 7
        canvas.fillBox(-halfWidth, 7, z - 1, halfWidth, 7, z, c.roof());
        canvas.fillBox(-halfWidth, 8, z - 1, halfWidth, 8, z - 1, "IRON_BARS");

        // Double-Height Main Entrance Portal
        canvas.fillBox(-2, 1, z, 2, 4, z, "AIR");
        canvas.fillBox(-2, 1, z, -2, 4, z, c.concreteWhite());
        canvas.fillBox(2, 1, z, 2, 4, z, c.concreteWhite());
        canvas.set(-1, 1, z, c.glass());
        canvas.set(0, 1, z, c.glass());
        canvas.set(1, 1, z, c.glass());
        canvas.set(-1, 2, z, c.glass());
        canvas.set(0, 2, z, c.glass());
        canvas.set(1, 2, z, c.glass());

        // Cantilevered Modern Entrance Canopy
        canvas.fillBox(-3, 4, z - 2, 3, 4, z, c.roof());
        canvas.fillBox(-2, 5, z - 2, 2, 5, z, c.primaryRed());

        // Wolfpack Header Parapet & Cornice
        canvas.fillBox(-halfWidth, height - 2, z, halfWidth, height, z, c.concreteBlack());
        canvas.fillBox(-halfWidth, height + 1, z - 1, halfWidth, height + 1, z, c.roof());

        // Landmark Sign
        canvas.sign(0, 1, z - 3, 0, "NC State Wolfpack", label(cfg, "union-facade", "Talley Student Union"));
    }

    // -- D. H. Hill Jr. Library-style facade ------------------------------

    private static void buildLibraryFacade(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int x = Math.max(14, r - 4);
        int halfWidth = Math.min(18, r / 2 + 3);
        int height = 18;

        // Backing Wall of Red Concrete
        canvas.fillBox(x, 1, -halfWidth, x, height, halfWidth, c.primaryRed());

        // Collegiate Vertical Louvers & Window Pillars
        for (int z = -halfWidth; z <= halfWidth; z += 4) {
            canvas.fillBox(x, 1, z, x, height, z, c.concreteWhite());
        }

        // High Vertical Glass Ribbon Windows with Bookshelves Visible Inside
        for (int z = -halfWidth + 1; z <= halfWidth - 1; z++) {
            if (Math.abs(z % 4) != 0) {
                canvas.fillBox(x, 2, z, x, height - 4, z, c.glass());
                canvas.set(x + 1, 2, z, "BOOKSHELF");
                canvas.set(x + 1, 3, z, "BOOKSHELF");
                canvas.set(x + 1, 7, z, "BOOKSHELF");
                canvas.set(x + 1, 8, z, "BOOKSHELF");
            }
        }

        // Grand Library Entrance Portico (centered at z = 0)
        canvas.fillBox(x, 1, -2, x, 5, 2, "AIR");
        canvas.fillBox(x, 1, -2, x, 5, -2, c.concreteWhite());
        canvas.fillBox(x, 1, 2, x, 5, 2, c.concreteWhite());
        canvas.fillBox(x, 1, -1, x, 3, 1, c.glass());
        canvas.fillBox(x - 1, 5, -3, x, 5, 3, c.roof());
        canvas.fillBox(x - 1, 6, -2, x, 6, 2, c.primaryRed());

        // Collegiate Parapet & Wolfpack Header Frieze
        canvas.fillBox(x, height - 2, -halfWidth, x, height, halfWidth, c.concreteBlack());
        canvas.fillBox(x - 1, height + 1, -halfWidth, x, height + 1, halfWidth, c.roof());

        // Landmark Sign
        canvas.sign(x - 3, 1, 0, 12, "NC State Wolfpack", label(cfg, "library-facade", "D. H. Hill Jr. Library"));
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
