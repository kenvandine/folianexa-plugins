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
        if (cfg.include().portals()) {
            buildNetherPortals(canvas, cfg);
        }
        if (cfg.include().unionFacade()) {
            buildUnionFacade(canvas, cfg);
        }
        if (cfg.include().libraryFacade()) {
            buildLibraryFacade(canvas, cfg);
        }
        return canvas.toScene();
    }

    // -- Enclosure: 4 Solid Concrete Decorated Walls & Stepped Vaulted Ceiling --

    private static void buildEnclosure(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        int height = cfg.towerHeight();
        SceneConfig.Colors c = cfg.colors();

        int wallHeight = 26;

        // 1. Four 100% Solid Closed Boundary Walls (North, South, East, West) — No Glass to outside!
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = -r; x <= r; x++) {
                // North and South base walls
                canvas.set(x, y, -r, getBaseWallPattern(x, y, wallHeight, c));
                canvas.set(x, y, r, getBaseWallPattern(x, y, wallHeight, c));
            }
            for (int z = -r; z <= r; z++) {
                // East and West base walls
                canvas.set(-r, y, z, getBaseWallPattern(z, y, wallHeight, c));
                canvas.set(r, y, z, getBaseWallPattern(z, y, wallHeight, c));
            }
        }

        // 2. High-Detail Wolfpack Pixel Art & Murals on Walls
        // North Wall (z = -r, looking North): Left-to-Right is +x increasing
        drawNorthWallPixelArt(canvas, -r, wallHeight, c);

        // South Wall (z = +r, looking South): Left-to-Right is -x decreasing (fixes backward text!)
        drawSouthWallPixelArt(canvas, r, wallHeight, c);

        // East Wall (x = +r, looking East): Left-to-Right is +z increasing
        drawEastWallPixelArt(canvas, r, wallHeight, c);

        // West Wall (x = -r, looking West): Left-to-Right is -z decreasing (fixes backward text!)
        drawWestWallPixelArt(canvas, -r, wallHeight, c);

        // 3. Grand Non-Cube Stepped Vaulted Concrete Ceiling (100% Solid, NO Glass)
        buildGeometricVaultedCeiling(canvas, r, height, c);
    }

    private static String getBaseWallPattern(int coord, int y, int maxY, SceneConfig.Colors c) {
        // Baseboard (y = 1 to 3)
        if (y <= 2) return c.concreteBlack();
        if (y == 3) return c.primaryRed();
        if (y == 4) return c.concreteWhite();

        // Upper Cornice & Frieze (y = maxY - 3 to maxY)
        if (y == maxY - 2) return c.concreteWhite();
        if (y == maxY - 1) return c.primaryRed();
        if (y >= maxY) return c.concreteBlack();

        // Pilasters every 5 blocks
        if (Math.abs(coord) % 5 == 0) {
            return (y % 4 == 0) ? c.primaryRed() : c.concreteBlack();
        }

        // Geometric chevron background tapestry
        int chevron = (Math.abs(coord) + y) % 6;
        if (chevron == 0 || chevron == 1) return c.concreteBlack();
        if (chevron == 2 || chevron == 3) return c.primaryRed();
        return c.concreteBlack();
    }

    private static void drawNorthWallPixelArt(Canvas canvas, int z, int maxY, SceneConfig.Colors c) {
        // 1. "NC STATE" Pixel Art Banner (y = 18 to 24, looking North -> left is -x, right is +x)
        renderTextOnWall(canvas, "NC STATE", -18, 18, z, true, 1, c.white(), c.primaryRed());

        // 2. Howling Wolf Mascot Pixel Art Head (x = -7 to 7, y = 6 to 16)
        int yStart = 6;
        int[] wolfMask = {
                0b000000000000001, // 16 - Ear tip
                0b000000000000111, // 15 - Ear
                0b000000000011111, // 14 - Head top
                0b000000001111111, // 13 - Brow
                0b000000111111111, // 12 - Raised muzzle
                0b000011111111110, // 11 - Open howling jaw
                0b001111110011110, // 10 - Open mouth
                0b000111111111111, // 9 - Throat & mane
                0b000011111111111, // 8 - Neck
                0b000001111111111, // 7 - Chest fur
                0b000000111111111  // 6 - Base
        };

        for (int row = 0; row < wolfMask.length; row++) {
            int bits = wolfMask[row];
            int y = yStart + (wolfMask.length - 1 - row);
            for (int col = 0; col < 15; col++) {
                if (((bits >> (14 - col)) & 1) == 1) {
                    int x = -7 + col;
                    if (row == 3 && (col == 10 || col == 11)) {
                        canvas.set(x, y, z, c.primaryRed()); // Red glowing eye
                    } else if (row >= 7 && col <= 7) {
                        canvas.set(x, y, z, c.white()); // White fur ruff
                    } else if (row >= 9) {
                        canvas.set(x, y, z, c.primaryRed()); // Red collar
                    } else {
                        canvas.set(x, y, z, c.white());
                    }
                }
            }
        }
    }

    private static void drawSouthWallPixelArt(Canvas canvas, int z, int maxY, SceneConfig.Colors c) {
        // 1. "WOLFPACK" Pixel Art Banner (y = 18 to 24, looking South -> left is +x, right is -x)
        // Starts at x = 16 and advances in stepDir = -1 so it reads correctly left-to-right!
        renderTextOnWall(canvas, "WOLFPACK", 16, 18, z, true, -1, c.white(), c.primaryRed());

        // 2. Central Block "S" Shield with Red & White Frame (x = -6 to 6, y = 6 to 16)
        canvas.fillBox(-6, 6, z, 6, 16, z, c.concreteBlack());
        canvas.ring(-5, z, 5, z, 6, c.primaryRed());
        canvas.ring(-5, z, 5, z, 16, c.primaryRed());
        for (int y = 7; y <= 15; y++) {
            canvas.set(-5, y, z, c.primaryRed());
            canvas.set(5, y, z, c.primaryRed());
        }
        // Inlaid Block S (drawn with correct non-mirrored facing for observer looking South)
        for (int x = -3; x <= 3; x++) {
            for (int dy = -3; dy <= 3; dy++) {
                int y = 11 + dy;
                // When looking South (+z), observer's left is +x, right is -x.
                // Standard 'S' has top opening on the right (-x) and bottom opening on the left (+x).
                if (isBlockSViewedFromFront(x, dy)) {
                    canvas.set(x - 1, y - 1, z, c.primaryRed());
                    canvas.set(x, y, z, c.white());
                }
            }
        }

        // Symmetrical Wolf Claws flanking the shield (x = -13..-8 and x = 8..13)
        drawClawMarks(canvas, -10, 8, z, true, c);
        drawClawMarks(canvas, 10, 8, z, true, c);
    }

    private static void drawEastWallPixelArt(Canvas canvas, int x, int maxY, SceneConfig.Colors c) {
        // 1. Founding Year "1887" Banner (y = 18 to 24, looking East -> left is -z, right is +z)
        renderTextOnWall(canvas, "1887", -10, 18, x, false, 1, c.white(), c.primaryRed());

        // 2. Symmetrical Twin Howling Wolves facing each other (y = 6 to 16)
        drawMiniWolf(canvas, x, 6, -6, false, c);
        drawMiniWolf(canvas, x, 6, 6, true, c);

        // Central Diamond Starburst at z = 0 (y = 8 to 14)
        for (int dy = -3; dy <= 3; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                int dist = Math.abs(dy) + Math.abs(dz);
                if (dist <= 3) {
                    canvas.set(x, 11 + dy, dz, (dist <= 1) ? c.white() : c.primaryRed());
                }
            }
        }
    }

    private static void drawWestWallPixelArt(Canvas canvas, int x, int maxY, SceneConfig.Colors c) {
        // 1. "GO PACK" Banner (y = 18 to 24, looking West -> left is +z, right is -z)
        // Starts at z = 14 and advances in stepDir = -1 so it reads correctly left-to-right!
        renderTextOnWall(canvas, "GO PACK", 14, 18, x, false, -1, c.white(), c.primaryRed());

        // 2. Howling Wolf Silhouette under Crescent Moon (y = 6 to 16)
        // Crescent Moon in White Concrete (z = -6 to -2, y = 11 to 16)
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = dy * dy + dz * dz;
                if (dist <= 5 && (dz <= 0 || dy * dy + (dz - 1) * (dz - 1) > 4)) {
                    canvas.set(x, 13 + dy, -4 + dz, c.white());
                }
            }
        }

        // Howling Wolf howling towards the moon on the right (toward -z)
        drawMiniWolf(canvas, x, 6, 4, true, c);
    }

    private static void drawMiniWolf(Canvas canvas, int fixedCoord, int yBase, int zCenter, boolean flip, SceneConfig.Colors c) {
        int[] miniWolf = {
                0b00001, // ear
                0b00011, // head
                0b01111, // muzzle
                0b11110, // open jaw
                0b01111, // throat
                0b00111, // neck
                0b00111, // body
                0b01111  // base
        };
        for (int r = 0; r < miniWolf.length; r++) {
            int bits = miniWolf[r];
            int y = yBase + (miniWolf.length - 1 - r);
            for (int col = 0; col < 5; col++) {
                if (((bits >> (4 - col)) & 1) == 1) {
                    int offset = flip ? (col - 2) : (2 - col);
                    int z = zCenter + offset;
                    canvas.set(fixedCoord, y, z, (r == 2 && col == 3) ? c.primaryRed() : c.white());
                }
            }
        }
    }

    private static void drawClawMarks(Canvas canvas, int xCenter, int yBase, int zFixed, boolean isZFixed, SceneConfig.Colors c) {
        for (int claw = -2; claw <= 2; claw += 2) {
            for (int i = 0; i < 6; i++) {
                int cx = xCenter + claw + (i / 2);
                int cy = yBase + i;
                int x = isZFixed ? cx : zFixed;
                int z = isZFixed ? zFixed : cx;
                canvas.set(x, cy, z, c.primaryRed());
                canvas.set(x - 1, cy, z, c.white());
            }
        }
    }

    private static void renderTextOnWall(Canvas canvas, String text, int startCoord, int yBase, int fixedCoord, boolean isZFixed, int stepDir, String letterColor, String shadowColor) {
        int cur = startCoord;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ' ') {
                cur += 3 * stepDir;
                continue;
            }
            int[] rows = getCharBitmap(ch);
            for (int r = 0; r < 7; r++) {
                int rowBits = rows[r];
                int y = yBase + (6 - r);
                for (int col = 0; col < 5; col++) {
                    if (((rowBits >> (4 - col)) & 1) == 1) {
                        int cCoord = cur + col * stepDir;
                        int x = isZFixed ? cCoord : fixedCoord;
                        int z = isZFixed ? fixedCoord : cCoord;
                        int sx = isZFixed ? cCoord + stepDir : fixedCoord;
                        int sz = isZFixed ? fixedCoord : cCoord + stepDir;
                        canvas.set(sx, y - 1, sz, shadowColor);
                        canvas.set(x, y, z, letterColor);
                    }
                }
            }
            cur += 6 * stepDir;
        }
    }

    private static int[] getCharBitmap(char ch) {
        return switch (ch) {
            case 'A' -> new int[]{0x0E, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'B' -> new int[]{0x1E, 0x11, 0x11, 0x1E, 0x11, 0x11, 0x1E};
            case 'C' -> new int[]{0x0E, 0x11, 0x10, 0x10, 0x10, 0x11, 0x0E};
            case 'D' -> new int[]{0x1E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x1E};
            case 'E' -> new int[]{0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x1F};
            case 'F' -> new int[]{0x1F, 0x10, 0x10, 0x1E, 0x10, 0x10, 0x10};
            case 'G' -> new int[]{0x0E, 0x11, 0x10, 0x13, 0x11, 0x11, 0x0E};
            case 'H' -> new int[]{0x11, 0x11, 0x11, 0x1F, 0x11, 0x11, 0x11};
            case 'I' -> new int[]{0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x1F};
            case 'K' -> new int[]{0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11};
            case 'L' -> new int[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F};
            case 'M' -> new int[]{0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11};
            case 'N' -> new int[]{0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11};
            case 'O' -> new int[]{0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E};
            case 'P' -> new int[]{0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10};
            case 'R' -> new int[]{0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11};
            case 'S' -> new int[]{0x0E, 0x11, 0x10, 0x0E, 0x01, 0x11, 0x0E};
            case 'T' -> new int[]{0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04};
            case 'U' -> new int[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E};
            case 'W' -> new int[]{0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11};
            case '0' -> new int[]{0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E};
            case '1' -> new int[]{0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E};
            case '7' -> new int[]{0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08};
            case '8' -> new int[]{0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E};
            default -> new int[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        };
    }

    private static void buildGeometricVaultedCeiling(Canvas canvas, int r, int towerHeight, SceneConfig.Colors c) {
        int baseCeilingY = 26;
        int peakCeilingY = 32;

        // 100% Solid Full Concrete Blocks — Organic Stepped Vaulted Gothic Dome!
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int distFromWall = Math.min(r - Math.abs(x), r - Math.abs(z));
                int vaultY = Math.min(peakCeilingY, baseCeilingY + (int) Math.round(distFromWall * 0.7));

                boolean isCentralMedallion = (Math.abs(x) <= 6 && Math.abs(z) <= 6);
                int dist = Math.abs(x) + Math.abs(z);

                if (isCentralMedallion) {
                    if (dist <= 2) {
                        // Central Block S
                        canvas.set(x, vaultY, z, isBlockS(x, z) ? c.white() : c.primaryRed());
                    } else if (dist <= 5) {
                        boolean isStar = (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
                        canvas.set(x, vaultY, z, isStar ? c.white() : c.primaryRed());
                    } else {
                        canvas.set(x, vaultY, z, c.concreteBlack());
                    }
                    if ((Math.abs(x) == 3 && Math.abs(z) == 3) || (x == 0 && Math.abs(z) == 4) || (z == 0 && Math.abs(x) == 4)) {
                        canvas.set(x, vaultY, z, "SEA_LANTERN");
                    }
                } else {
                    boolean isRib = (Math.abs(x) % 5 == 0) || (Math.abs(z) % 5 == 0) || (Math.abs(x) == Math.abs(z));
                    if (isRib) {
                        canvas.set(x, vaultY, z, c.concreteBlack());
                        if (Math.abs(x) % 5 == 0 && Math.abs(z) % 5 == 0) {
                            canvas.set(x, vaultY, z, "SEA_LANTERN");
                        }
                    } else {
                        int pattern = (Math.abs(x) + Math.abs(z)) % 3;
                        if (pattern == 0) canvas.set(x, vaultY, z, c.primaryRed());
                        else if (pattern == 1) canvas.set(x, vaultY, z, c.white());
                        else canvas.set(x, vaultY, z, c.concreteBlack());
                    }
                }

                // Solid ceiling backing cap (vaultY + 1)
                canvas.set(x, vaultY + 1, z, c.concreteBlack());
            }
        }
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
        canvas.ring(-(r - 1), -(r - 1), r - 1, r - 1, 0, c.primaryRed());
        canvas.ring(-(r - 2), -(r - 2), r - 2, r - 2, 0, c.concreteWhite());
        canvas.ring(-(r - 3), -(r - 3), r - 3, r - 3, 0, c.concreteBlack());

        // Inlaid Wolfpack Paw Prints in all four plaza quadrants
        placePawPrint(canvas, 10, 8, c);
        placePawPrint(canvas, -10, 8, c);
        placePawPrint(canvas, 10, -8, c);
        placePawPrint(canvas, -10, -8, c);

        // Grand central NC State "Block S" / Wolfpack medallion
        int medR = 5;
        for (int x = -medR; x <= medR; x++) {
            for (int z = -medR; z <= medR; z++) {
                int dist = Math.abs(x) + Math.abs(z);
                if (dist <= 6) {
                    canvas.set(x, 0, z, c.primaryRed());
                }
                if (dist == 6 || (Math.abs(x) == medR && Math.abs(z) <= 1) || (Math.abs(z) == medR && Math.abs(x) <= 1)) {
                    canvas.set(x, 0, z, c.concreteBlack());
                }
                if (dist == 5) {
                    canvas.set(x, 0, z, c.concreteWhite());
                }
            }
        }

        // Block S inlay in the medallion (white with bold black shadow outline)
        for (int x = -3; x <= 3; x++) {
            for (int z = -4; z <= 4; z++) {
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
        for (int x = -3; x <= 3; x++) {
            for (int z = -4; z <= 4; z++) {
                if (isBlockS(x, z)) {
                    canvas.set(x, 0, z, c.white());
                }
            }
        }

        // Grand Cardinal Pedestrian Promenades (+x East, -x West, +z South, -z North)
        int pLimit = r - 4;
        for (int x = -pLimit; x <= pLimit; x++) {
            if (Math.abs(x) > medR - 1) {
                canvas.set(x, 0, 0, c.concreteWhite());
                canvas.set(x, 0, -1, c.concreteBlack());
                canvas.set(x, 0, 1, c.concreteBlack());
                canvas.set(x, 0, -2, c.primaryRed());
                canvas.set(x, 0, 2, c.primaryRed());
            }
        }
        for (int z = -pLimit; z <= pLimit; z++) {
            if (Math.abs(z) > medR - 1) {
                canvas.set(0, 0, z, c.concreteWhite());
                canvas.set(-1, 0, z, c.concreteBlack());
                canvas.set(1, 0, z, c.concreteBlack());
                canvas.set(-2, 0, z, c.primaryRed());
                canvas.set(2, 0, z, c.primaryRed());
            }
        }

        // 4 Wolfpack Spirit Fire Bowls / Victory Beacons around the medallion
        placeSpiritBeacon(canvas, 5, 5, c);
        placeSpiritBeacon(canvas, -5, 5, c);
        placeSpiritBeacon(canvas, 5, -5, c);
        placeSpiritBeacon(canvas, -5, -5, c);

        // Campus Lampposts along the grand avenues
        int[] lampDistances = {9, 15};
        for (int ld : lampDistances) {
            if (ld < pLimit) {
                placeLamppost(canvas, ld, 3, c);
                placeLamppost(canvas, ld, -3, c);
                placeLamppost(canvas, -ld, 3, c);
                placeLamppost(canvas, -ld, -3, c);
            }
        }

        // Wolfpack Flagpoles at the 4 corners
        int flagOffset = Math.max(6, r - 4);
        placeFlagpole(canvas, flagOffset, flagOffset, c.primaryRed(), true, c);
        placeFlagpole(canvas, -flagOffset, flagOffset, c.white(), false, c);
        placeFlagpole(canvas, flagOffset, -flagOffset, c.white(), false, c);
        placeFlagpole(canvas, -flagOffset, -flagOffset, c.primaryRed(), true, c);
    }

    private static void placePawPrint(Canvas canvas, int cx, int cz, SceneConfig.Colors c) {
        canvas.fillBox(cx - 1, 0, cz, cx + 1, 0, cz + 1, c.concreteWhite());
        canvas.set(cx - 2, 0, cz - 2, c.primaryRed());
        canvas.set(cx - 1, 0, cz - 3, c.primaryRed());
        canvas.set(cx + 1, 0, cz - 3, c.primaryRed());
        canvas.set(cx + 2, 0, cz - 2, c.primaryRed());
    }

    private static void placeSpiritBeacon(Canvas canvas, int x, int z, SceneConfig.Colors c) {
        canvas.set(x, 1, z, c.concreteBlack());
        canvas.set(x, 2, z, "SEA_LANTERN");
        canvas.set(x, 3, z, c.primaryRed());
        canvas.set(x + 1, 2, z, "IRON_BARS");
        canvas.set(x - 1, 2, z, "IRON_BARS");
        canvas.set(x, 2, z + 1, "IRON_BARS");
        canvas.set(x, 2, z - 1, "IRON_BARS");
        canvas.set(x, 4, z, c.concreteBlack());
    }

    private static boolean isBlockS(int x, int z) {
        if (z == -4 && x >= -2 && x <= 2) return true;
        if (z == -3 && (x == -2 || x == -1)) return true;
        if (z == -2 && (x == -2 || x == -1)) return true;
        if (z == -1 && x >= -2 && x <= 2) return true;
        if (z == 0 && x >= -2 && x <= 2) return true;
        if (z == 1 && (x == 1 || x == 2)) return true;
        if (z == 2 && (x == 1 || x == 2)) return true;
        if (z == 3 && (x == 1 || x == 2)) return true;
        if (z == 4 && x >= -2 && x <= 2) return true;
        return false;
    }

    private static boolean isBlockSViewedFromFront(int x, int dy) {
        // dy from +3 (top) to -3 (bottom)
        // When facing South (+z), observer's left is +x, observer's right is -x.
        // For an 'S' to look correct to the observer:
        // Top stroke: full width
        // Upper spine: on observer's left (+x)
        // Middle stroke: full width
        // Lower spine: on observer's right (-x)
        // Bottom stroke: full width
        if (dy == 3 && x >= -2 && x <= 2) return true;
        if (dy == 2 && (x == 1 || x == 2)) return true;
        if (dy == 1 && (x == 1 || x == 2)) return true;
        if (dy == 0 && x >= -2 && x <= 2) return true;
        if (dy == -1 && (x == -2 || x == -1)) return true;
        if (dy == -2 && (x == -2 || x == -1)) return true;
        if (dy == -3 && x >= -2 && x <= 2) return true;
        return false;
    }

    private static void placeLamppost(Canvas canvas, int x, int z, SceneConfig.Colors c) {
        canvas.fillBox(x, 1, z, x, 3, z, c.concreteBlack());
        canvas.set(x, 4, z, c.concreteBlack());
        canvas.set(x, 3, z + 1, "LANTERN");
        canvas.set(x, 3, z - 1, "LANTERN");
        canvas.set(x, 5, z, c.primaryRed());
    }

    private static void placeFlagpole(Canvas canvas, int x, int z, String wolfpackColor, boolean isRed, SceneConfig.Colors c) {
        String banner = isRed ? "RED_BANNER" : "WHITE_BANNER";
        canvas.set(x, 1, z, c.concreteBlack());
        canvas.fillBox(x, 2, z, x, 5, z, "IRON_BARS");
        canvas.set(x, 6, z, c.concreteBlack());
        canvas.set(x, 5, z + 1, banner);
        canvas.set(x, 4, z + 1, banner);
    }

    // -- Three Fancy Nether Portals (Litematica-Inspired Shrine) ---------

    private static void buildNetherPortals(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int pz = Math.min(13, r - 6); // south side of the plaza

        // 1. Shared Grand Stepped Podium (y = 1) in Black & Red Concrete
        canvas.fillBox(-9, 1, pz - 1, 9, 1, pz + 2, c.concreteBlack());
        canvas.ring(-9, pz - 1, 9, pz + 2, 1, c.primaryRed());
        canvas.fillBox(-8, 1, pz - 2, 8, 1, pz - 2, c.concreteWhite());

        // 2. Left Portal (x = -8 to -4, center x = -6, y = 2 to 5)
        buildPortalArch(canvas, -6, pz, 4, false, c);

        // 3. Middle Portal (x = -2 to 2, center x = 0, y = 2 to 6, taller grand arch)
        buildPortalArch(canvas, 0, pz, 5, true, c);

        // 4. Right Portal (x = 4 to 8, center x = 6, y = 2 to 5)
        buildPortalArch(canvas, 6, pz, 4, false, c);

        // 5. Inter-Portal Gothic Buttress Columns (1-block gaps at x = -3 and x = 3)
        for (int colX : new int[]{-3, 3}) {
            canvas.fillBox(colX, 2, pz, colX, 6, pz, c.concreteBlack());
            canvas.set(colX, 2, pz - 1, c.primaryRed());
            canvas.set(colX, 4, pz - 1, c.concreteWhite());
            canvas.set(colX, 6, pz - 1, c.primaryRed());
            canvas.set(colX, 7, pz, c.concreteWhite());
            canvas.set(colX, 8, pz, "LANTERN");
        }

        // 6. Standing Signs in front of each portal
        canvas.sign(-6, 1, pz - 3, 0, "NC State Portal", label(cfg, "portal-left", "Resource Realm"));
        canvas.sign(0, 1, pz - 3, 0, "NC State Portal", label(cfg, "portal-center", "Survival World"));
        canvas.sign(6, 1, pz - 3, 0, "NC State Portal", label(cfg, "portal-right", "Minigames Hub"));
    }

    private static void buildPortalArch(Canvas canvas, int cx, int cz, int innerHeight, boolean isMainCenter, SceneConfig.Colors c) {
        int halfW = 1; // 3 blocks wide portal opening: cx - 1 to cx + 1
        int bottomY = 2;
        int topY = bottomY + innerHeight - 1;

        // Obsidian Portal Frame
        canvas.fillBox(cx - halfW, bottomY - 1, cz, cx + halfW, bottomY - 1, cz, "OBSIDIAN");
        canvas.fillBox(cx - halfW - 1, bottomY, cz, cx - halfW - 1, topY, cz, "OBSIDIAN");
        canvas.fillBox(cx + halfW + 1, bottomY, cz, cx + halfW + 1, topY, cz, "OBSIDIAN");
        canvas.fillBox(cx - halfW, topY + 1, cz, cx + halfW, topY + 1, cz, "OBSIDIAN");

        // Nether Portal Blocks in the interior opening
        for (int x = cx - halfW; x <= cx + halfW; x++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(x, y, cz, "NETHER_PORTAL");
            }
        }

        // Crying Obsidian & Concrete Buttresses flanking the pillars
        canvas.set(cx - halfW - 1, bottomY, cz - 1, "CRYING_OBSIDIAN");
        canvas.set(cx + halfW + 1, bottomY, cz - 1, "CRYING_OBSIDIAN");
        canvas.set(cx - halfW - 1, bottomY + 1, cz - 1, c.primaryRed());
        canvas.set(cx + halfW + 1, bottomY + 1, cz - 1, c.primaryRed());
        canvas.set(cx - halfW - 1, topY, cz - 1, c.concreteWhite());
        canvas.set(cx + halfW + 1, topY, cz - 1, c.concreteWhite());

        // Gothic Crown / Gable above portal
        int gableY = topY + 2;
        canvas.fillBox(cx - halfW - 1, gableY, cz, cx + halfW + 1, gableY, cz, c.concreteBlack());
        canvas.fillBox(cx - halfW, gableY + 1, cz, cx + halfW, gableY + 1, cz, c.primaryRed());
        canvas.set(cx, gableY + 2, cz, c.concreteWhite());

        if (isMainCenter) {
            // Grand central Wolfpack crest & victory beacon
            canvas.set(cx, gableY + 1, cz - 1, c.white());
            canvas.set(cx, gableY + 3, cz, "SEA_LANTERN");
            canvas.set(cx, gableY + 4, cz, c.primaryRed());
            canvas.set(cx, gableY + 5, cz, "LIGHTNING_ROD");
        } else {
            canvas.set(cx, gableY + 3, cz, c.primaryRed());
        }

        // Suspended Soul Lantern in front of arch
        canvas.set(cx, topY + 1, cz - 1, "CHAIN");
        canvas.set(cx, topY, cz - 1, "SOUL_LANTERN");
    }

    // -- Memorial Belltower ----------------------------------------------

    private static void buildBelltower(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        int height = cfg.towerHeight();
        SceneConfig.Colors c = cfg.colors();

        int halfFoot = 2; // 5x5 shaft
        int halfPodium = 3; // 7x7 base podium
        int cz = -Math.max(halfPodium + 2, r - 9); // north of plaza center

        // 1. Stepped Podium (y = 1 to 2) in pure concrete
        canvas.fillBox(-halfPodium, 1, cz - halfPodium, halfPodium, 1, cz + halfPodium, c.concreteBlack());
        canvas.fillBox(-halfFoot - 1, 2, cz - halfFoot - 1, halfFoot + 1, 2, cz + halfFoot + 1, c.primaryRed());

        // 2. Ground Floor Memorial Shrine & Rotunda (y = 3 to 6)
        for (int y = 3; y <= 6; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.concreteWhite());
            canvas.set(-halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(-halfFoot, y, cz + halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz + halfFoot, c.concreteBlack());
        }

        // 4 Archway entrances
        canvas.fillBox(-1, 3, cz + halfFoot, 1, 5, cz + halfFoot, "AIR");
        canvas.fillBox(-1, 3, cz - halfFoot, 1, 5, cz - halfFoot, "AIR");
        canvas.fillBox(halfFoot, 3, cz - 1, halfFoot, 5, cz + 1, "AIR");
        canvas.fillBox(-halfFoot, 3, cz - 1, -halfFoot, 5, cz + 1, "AIR");

        // Central Memorial Pedestal with glowing Wolfpack beacon
        canvas.set(0, 3, cz, "SEA_LANTERN");
        canvas.set(0, 4, cz, c.primaryRed());
        canvas.set(0, 6, cz, "LANTERN");

        // 3. Main Tower Shaft (y = 7 to height - 6)
        int shaftTop = Math.max(8, height - 6);
        for (int y = 7; y <= shaftTop; y++) {
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

        // Architectural Belt Course
        int band = 7 + (shaftTop - 7) / 2;
        canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, band, c.primaryRed());

        // 4. Belfry / Bell Chamber (y = height - 5 to height - 3)
        int belfryBottom = height - 5;
        int belfryTop = height - 3;
        canvas.fillBox(-halfFoot, belfryBottom, cz - halfFoot, halfFoot, belfryBottom, cz + halfFoot, c.concreteBlack());
        for (int y = belfryBottom + 1; y <= belfryTop; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.concreteWhite());
            canvas.set(-halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz - halfFoot, c.concreteBlack());
            canvas.set(-halfFoot, y, cz + halfFoot, c.concreteBlack());
            canvas.set(halfFoot, y, cz + halfFoot, c.concreteBlack());
        }
        // Suspended Bell
        canvas.set(0, belfryTop, cz, "CHAIN");
        canvas.set(0, belfryTop - 1, cz, "BELL");
        canvas.set(0, belfryBottom + 1, cz, c.primaryRed());
        canvas.set(0, belfryBottom, cz, "SEA_LANTERN");

        // 5. Four-Sided Clock (y = height - 2 to height - 1)
        int clockY = height - 2;
        for (int y = height - 2; y <= height - 1; y++) {
            canvas.ring(-halfFoot, cz - halfFoot, halfFoot, cz + halfFoot, y, c.primaryRed());
        }
        buildClockFace(canvas, 0, clockY, cz + halfFoot, 1, 0, c);
        buildClockFace(canvas, 0, clockY, cz - halfFoot, 1, 0, c);
        buildClockFace(canvas, halfFoot, clockY, cz, 0, 1, c);
        buildClockFace(canvas, -halfFoot, clockY, cz, 0, 1, c);

        // 6. Cornice & Parapet (y = height)
        canvas.ring(-halfFoot - 1, cz - halfFoot - 1, halfFoot + 1, cz + halfFoot + 1, height, c.concreteBlack());

        // Corner Pinnacles
        int[][] corners = {{-halfFoot, -halfFoot}, {halfFoot, -halfFoot}, {-halfFoot, halfFoot}, {halfFoot, halfFoot}};
        for (int[] corner : corners) {
            int cx = corner[0];
            int cdz = corner[1];
            canvas.set(cx, height + 1, cz + cdz, c.concreteWhite());
            canvas.set(cx, height + 2, cz + cdz, "LIGHTNING_ROD");
        }

        // 7. Stepped Pyramidal Roof & Glowing Red Spire (y = height + 1 to height + 5)
        int roofY = height + 1;
        canvas.fillBox(-1, roofY, cz - 1, 1, roofY, cz + 1, c.concreteBlack());
        canvas.set(0, roofY + 1, cz, c.primaryRed());
        canvas.set(0, roofY + 2, cz, "SEA_LANTERN");
        canvas.set(0, roofY + 3, cz, c.primaryRed());
        canvas.set(0, roofY + 4, cz, "LIGHTNING_ROD");

        // Landmark Sign
        canvas.sign(0, 1, cz + halfPodium + 2, 0, "NC State Wolfpack", label(cfg, "belltower", "Memorial Belltower"));
    }

    private static void buildClockFace(Canvas canvas, int cx, int cy, int cz, int dxStep, int dzStep, SceneConfig.Colors c) {
        canvas.set(cx, cy, cz, c.black());
        canvas.set(cx + dxStep, cy, cz + dzStep, c.white());
        canvas.set(cx - dxStep, cy, cz - dzStep, c.white());
        canvas.set(cx, cy + 1, cz, c.white());
    }

    // -- Wolf mascot statue ------------------------------------------------

    private static void buildWolfStatue(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int baseX = Math.max(8, r - 10);
        int baseZ = -2;

        // 1. Multi-tiered Pedestal (y = 1 to 2) in pure concrete
        canvas.fillBox(baseX - 2, 1, baseZ - 3, baseX + 2, 1, baseZ + 3, c.concreteBlack());
        canvas.fillBox(baseX - 1, 2, baseZ - 2, baseX + 1, 2, baseZ + 2, c.primaryRed());

        // 2. Sculpted Wolf Body in pure Concrete (y = 3 to 9)
        // Four paws & legs
        canvas.set(baseX - 1, 3, baseZ - 2, c.concreteBlack());
        canvas.set(baseX + 1, 3, baseZ - 2, c.concreteBlack());
        canvas.set(baseX - 1, 3, baseZ + 2, c.concreteBlack());
        canvas.set(baseX + 1, 3, baseZ + 2, c.concreteBlack());

        // Torso & Wolfpack Red Coat
        canvas.fillBox(baseX - 1, 4, baseZ - 2, baseX + 1, 5, baseZ + 2, c.primaryRed());
        canvas.set(baseX, 4, baseZ, c.concreteWhite()); // underbelly

        // Tail
        canvas.set(baseX + 2, 5, baseZ + 2, c.primaryRed());
        canvas.set(baseX + 2, 6, baseZ + 2, c.concreteBlack());

        // Chest & Neck (tilted upward in a howl)
        canvas.fillBox(baseX - 1, 5, baseZ - 2, baseX + 1, 6, baseZ - 2, c.primaryRed());
        canvas.set(baseX, 5, baseZ - 2, c.concreteWhite());

        // Howling Wolf Head & Snout
        canvas.fillBox(baseX - 1, 7, baseZ - 3, baseX + 1, 8, baseZ - 2, c.primaryRed());
        canvas.set(baseX, 7, baseZ - 4, c.concreteWhite()); // muzzle
        canvas.set(baseX, 8, baseZ - 4, c.black()); // nose
        canvas.set(baseX - 1, 8, baseZ - 2, c.primaryRed()); // eyes
        canvas.set(baseX + 1, 8, baseZ - 2, c.primaryRed());
        canvas.set(baseX - 1, 9, baseZ - 2, c.concreteBlack()); // ears
        canvas.set(baseX + 1, 9, baseZ - 2, c.concreteBlack());

        // Plaque Sign
        canvas.sign(baseX, 1, baseZ + 4, 8, "NC State Wolfpack", label(cfg, "wolf-statue", "Howl at the Wolfpack statue"));
    }

    // -- Free Expression Tunnel-style mural walkway -----------------------

    private static void buildTunnel(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int xCenter = -Math.max(9, r - 9);
        int halfW = 2;
        int xMin = xCenter - halfW;
        int xMax = xCenter + halfW;
        int zLen = Math.min(4, r / 4 + 1);

        // Walkway Floor in pure concrete
        canvas.fillBox(xMin, 0, -zLen, xMax, 0, zLen, c.concreteBlack());
        for (int z = -zLen; z <= zLen; z++) {
            canvas.set(xCenter, 0, z, c.concreteWhite());
            canvas.set(xMin + 1, 0, z, c.primaryRed());
            canvas.set(xMax - 1, 0, z, c.primaryRed());
        }

        // Outer Vault Walls in pure concrete
        for (int z = -zLen; z <= zLen; z++) {
            canvas.fillBox(xMin, 1, z, xMin, 3, z, c.concreteBlack());
            canvas.fillBox(xMax, 1, z, xMax, 3, z, c.concreteBlack());
            canvas.set(xMin + 1, 4, z, c.primaryRed());
            canvas.set(xMax - 1, 4, z, c.primaryRed());
            canvas.set(xCenter, 4, z, c.concreteBlack());
        }

        // Arched Entry Portals (North and South)
        for (int endZ : new int[]{-zLen, zLen}) {
            canvas.ring(xMin, endZ, xMax, endZ, 1, c.concreteWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 2, c.concreteWhite());
            canvas.fillBox(xMin + 1, 1, endZ, xMax - 1, 2, endZ, "AIR");
            canvas.set(xCenter, 3, endZ, c.primaryRed());
        }

        // Ceiling Lantern Fixtures
        for (int z = -zLen + 1; z <= zLen - 1; z += 2) {
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
            for (int y = 1; y <= 2; y++) {
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

        int z = Math.max(10, r - 3);
        int halfWidth = Math.min(10, r / 2);
        int height = 12;

        // Backing Wall of Red Concrete
        canvas.fillBox(-halfWidth, 1, z, halfWidth, height, z, c.primaryRed());

        // Modern White Concrete Structural Columns
        for (int x = -halfWidth; x <= halfWidth; x += 3) {
            canvas.fillBox(x, 1, z, x, height, z, c.concreteWhite());
        }

        // 2-Story Modern Tinted Panels
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            if (Math.abs(x % 3) != 0) {
                canvas.fillBox(x, 2, z, x, 4, z, c.concreteBlack());
                canvas.fillBox(x, 7, z, x, 9, z, c.concreteBlack());
            }
        }

        // Second-Story Balcony
        canvas.fillBox(-halfWidth, 5, z - 1, halfWidth, 5, z, c.concreteBlack());
        canvas.fillBox(-halfWidth, 6, z - 1, halfWidth, 6, z - 1, "IRON_BARS");

        // Main Entrance Portal
        canvas.fillBox(-1, 1, z, 1, 3, z, "AIR");
        canvas.set(0, 4, z - 1, c.primaryRed());

        // Header Parapet
        canvas.fillBox(-halfWidth, height, z, halfWidth, height, z, c.concreteBlack());

        // Landmark Sign
        canvas.sign(0, 1, z - 2, 0, "NC State Wolfpack", label(cfg, "union-facade", "Talley Student Union"));
    }

    // -- D. H. Hill Jr. Library-style facade ------------------------------

    private static void buildLibraryFacade(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int x = Math.max(10, r - 3);
        int halfWidth = Math.min(10, r / 2);
        int height = 12;

        // Backing Wall of Red Concrete
        canvas.fillBox(x, 1, -halfWidth, x, height, halfWidth, c.primaryRed());

        // Collegiate Vertical Louvers & Window Pillars
        for (int z = -halfWidth; z <= halfWidth; z += 3) {
            canvas.fillBox(x, 1, z, x, height, z, c.concreteWhite());
        }

        // Bookshelves Visible Inside
        for (int z = -halfWidth + 1; z <= halfWidth - 1; z++) {
            if (Math.abs(z % 3) != 0) {
                canvas.fillBox(x, 2, z, x, height - 3, z, c.concreteBlack());
                canvas.set(x - 1, 2, z, "BOOKSHELF");
                canvas.set(x - 1, 6, z, "BOOKSHELF");
            }
        }

        // Library Entrance Portico
        canvas.fillBox(x, 1, -1, x, 3, 1, "AIR");
        canvas.set(x - 1, 4, 0, c.primaryRed());

        // Header Parapet
        canvas.fillBox(x, height, -halfWidth, x, height, halfWidth, c.concreteBlack());

        // Landmark Sign
        canvas.sign(x - 2, 1, 0, 12, "NC State Wolfpack", label(cfg, "library-facade", "D. H. Hill Jr. Library"));
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
