package io.github.kenvandine.campuslobby;

import java.util.ArrayList;
import java.util.List;

/**
 * Procedurally generates an NC State Wolfpack-themed enclosed lobby scene
 * for the Sullivan SMP (Sullivan Residence Hall, NC State University):
 * an enclosed grand campus arena with four decorated walls, high coffered
 * ceiling with chandeliers & skylights, a central concrete Brickyard plaza
 * with paw prints & Block 'S' medallion, a Memorial Belltower centerpiece,
 * a howling wolf-mascot statue, a Free Expression Tunnel mural art gallery,
 * a D.H. Hill Jr. Library study nook, a cozy Sullivan Residence Hall & Talley
 * lounge with fireplace hearth, and three hyper-fancy decorative Nether Portals
 * on the North, West, and East sides of the lobby — all styled in vibrant
 * Wolfpack Red, White, and Black concrete.
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
        if (cfg.compact()) {
            buildCompactLobby(canvas, cfg);
            return canvas.toScene();
        }
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

        int wallHeight = 22;

        // 1. Four 100% Solid Closed Boundary Walls (North, South, East, West) — No Glass to outside!
        for (int y = 1; y <= wallHeight; y++) {
            for (int x = -r; x <= r; x++) {
                canvas.set(x, y, -r, getBaseWallPattern(x, y, wallHeight, c));
                canvas.set(x, y, r, getBaseWallPattern(x, y, wallHeight, c));
            }
            for (int z = -r; z <= r; z++) {
                canvas.set(-r, y, z, getBaseWallPattern(z, y, wallHeight, c));
                canvas.set(r, y, z, getBaseWallPattern(z, y, wallHeight, c));
            }
        }

        // 2. High-Detail Wolfpack Pixel Art & Murals on Walls
        // North Wall (z = -r, looking North): Left-to-Right is +x increasing
        drawNorthWallPixelArt(canvas, -r, wallHeight, c);

        // South Wall (z = +r, looking South): Left-to-Right is -x decreasing (proper left-to-right reading)
        drawSouthWallPixelArt(canvas, r, wallHeight, c);

        // East Wall (x = +r, looking East): Left-to-Right is +z increasing
        drawEastWallPixelArt(canvas, r, wallHeight, c);

        // West Wall (x = -r, looking West): Left-to-Right is -z decreasing (proper left-to-right reading)
        drawWestWallPixelArt(canvas, -r, wallHeight, c);

        // 3. Grand Non-Cube Stepped Vaulted Concrete Ceiling (100% Solid, NO Glass)
        buildGeometricVaultedCeiling(canvas, r, height, c);
    }

    private static String getBaseWallPattern(int coord, int y, int maxY, SceneConfig.Colors c) {
        // Baseboard (y = 1 to 3)
        if (y <= 2) return c.concreteBlack();
        if (y == 3) return c.primaryRed();
        if (y == 4) return c.concreteWhite();

        // Upper Cornice & Frieze (y = maxY - 2 to maxY)
        if (y == maxY - 2) return c.concreteWhite();
        if (y == maxY - 1) return c.primaryRed();
        if (y >= maxY) return c.concreteBlack();

        // Pilasters every 4 blocks
        if (Math.abs(coord) % 4 == 0) {
            return (y % 4 == 0) ? c.primaryRed() : c.concreteBlack();
        }

        // Geometric chevron background tapestry
        int chevron = (Math.abs(coord) + y) % 5;
        if (chevron == 0 || chevron == 1) return c.concreteBlack();
        if (chevron == 2 || chevron == 3) return c.primaryRed();
        return c.concreteBlack();
    }

    private static void drawNorthWallPixelArt(Canvas canvas, int z, int maxY, SceneConfig.Colors c) {
        // 1. "NC STATE" Pixel Art Banner (y = 15 to 21, looking North -> left is -x, right is +x)
        renderTextOnWall(canvas, "NC STATE", -14, 15, z, true, 1, c.white(), c.primaryRed());

        // 2. Howling Wolf Mascot Pixel Art Head (x = -5 to 5, y = 4 to 13)
        int yStart = 4;
        int[] wolfMask = {
                0b0000000001, // 13 - Ear tip
                0b0000000111, // 12 - Ear
                0b0000001111, // 11 - Head top
                0b0000011111, // 10 - Brow
                0b0001111111, // 9 - Raised muzzle
                0b0011111110, // 8 - Open howling jaw
                0b0111110110, // 7 - Open mouth
                0b0001111111, // 6 - Throat & mane
                0b0000111111, // 5 - Neck
                0b0000011111  // 4 - Base
        };

        for (int row = 0; row < wolfMask.length; row++) {
            int bits = wolfMask[row];
            int y = yStart + (wolfMask.length - 1 - row);
            for (int col = 0; col < 10; col++) {
                if (((bits >> (9 - col)) & 1) == 1) {
                    int x = -5 + col;
                    if (row == 3 && (col == 6 || col == 7)) {
                        canvas.set(x, y, z, c.primaryRed()); // Red glowing eye
                    } else if (row >= 6 && col <= 4) {
                        canvas.set(x, y, z, c.white()); // White fur ruff
                    } else if (row >= 8) {
                        canvas.set(x, y, z, c.primaryRed()); // Red collar
                    } else {
                        canvas.set(x, y, z, c.white());
                    }
                }
            }
        }
    }

    private static void drawSouthWallPixelArt(Canvas canvas, int z, int maxY, SceneConfig.Colors c) {
        // 1. "WOLFPACK" Pixel Art Banner (y = 15 to 21, looking South -> left is +x, right is -x)
        renderTextOnWall(canvas, "WOLFPACK", 13, 15, z, true, -1, c.white(), c.primaryRed());

        // 2. Central Block "S" Shield with Red & White Frame (x = -4 to 4, y = 4 to 13)
        canvas.fillBox(-4, 4, z, 4, 13, z, c.concreteBlack());
        canvas.ring(-4, z, 4, z, 4, c.primaryRed());
        canvas.ring(-4, z, 4, z, 13, c.primaryRed());
        for (int y = 5; y <= 12; y++) {
            canvas.set(-4, y, z, c.primaryRed());
            canvas.set(4, y, z, c.primaryRed());
        }
        // Inlaid Block S (drawn with correct non-mirrored facing for observer looking South)
        for (int x = -2; x <= 2; x++) {
            for (int dy = -2; dy <= 2; dy++) {
                int y = 8 + dy;
                if (isBlockSViewedFromFront(x, dy)) {
                    canvas.set(x - 1, y - 1, z, c.primaryRed());
                    canvas.set(x, y, z, c.white());
                }
            }
        }

        // Symmetrical Wolf Claws flanking the shield (x = -9..-6 and x = 6..9)
        drawClawMarks(canvas, -8, 5, z, true, c);
        drawClawMarks(canvas, 8, 5, z, true, c);
    }

    private static void drawEastWallPixelArt(Canvas canvas, int x, int maxY, SceneConfig.Colors c) {
        // 1. Founding Year "1887" Banner (y = 15 to 21, looking East -> left is -z, right is +z)
        renderTextOnWall(canvas, "1887", -7, 15, x, false, 1, c.white(), c.primaryRed());

        // 2. Symmetrical Twin Howling Wolves facing each other (y = 4 to 13)
        drawMiniWolf(canvas, x, 4, -5, false, c);
        drawMiniWolf(canvas, x, 4, 5, true, c);

        // Central Diamond Starburst at z = 0 (y = 6 to 11)
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = Math.abs(dy) + Math.abs(dz);
                if (dist <= 2) {
                    canvas.set(x, 8 + dy, dz, (dist <= 1) ? c.white() : c.primaryRed());
                }
            }
        }
    }

    private static void drawWestWallPixelArt(Canvas canvas, int x, int maxY, SceneConfig.Colors c) {
        // 1. "GO PACK" Banner (y = 15 to 21, looking West -> left is +z, right is -z)
        renderTextOnWall(canvas, "GO PACK", 10, 15, x, false, -1, c.white(), c.primaryRed());

        // 2. Howling Wolf Silhouette under Crescent Moon (y = 4 to 13)
        // Crescent Moon in White Concrete (z = -5 to -2, y = 8 to 12)
        for (int dy = -2; dy <= 2; dy++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = dy * dy + dz * dz;
                if (dist <= 4 && (dz <= 0 || dy * dy + (dz - 1) * (dz - 1) > 3)) {
                    canvas.set(x, 10 + dy, -3 + dz, c.white());
                }
            }
        }

        // Howling Wolf howling towards the moon on the right (toward -z)
        drawMiniWolf(canvas, x, 4, 3, true, c);
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
        for (int claw = -1; claw <= 1; claw += 2) {
            for (int i = 0; i < 5; i++) {
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
                cur += 2 * stepDir;
                continue;
            }
            int[] rows = getCharBitmap(ch);
            for (int r = 0; r < 7; r++) {
                int rowBits = rows[r];
                int y = yBase + (6 - r);
                for (int col = 0; col < 4; col++) {
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
            cur += 5 * stepDir;
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
            case 'J' -> new int[]{0x01, 0x01, 0x01, 0x01, 0x11, 0x11, 0x0E};
            case 'K' -> new int[]{0x11, 0x12, 0x14, 0x18, 0x14, 0x12, 0x11};
            case 'L' -> new int[]{0x10, 0x10, 0x10, 0x10, 0x10, 0x10, 0x1F};
            case 'M' -> new int[]{0x11, 0x1B, 0x15, 0x15, 0x11, 0x11, 0x11};
            case 'N' -> new int[]{0x11, 0x19, 0x15, 0x13, 0x11, 0x11, 0x11};
            case 'O' -> new int[]{0x0E, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E};
            case 'P' -> new int[]{0x1E, 0x11, 0x11, 0x1E, 0x10, 0x10, 0x10};
            case 'Q' -> new int[]{0x0E, 0x11, 0x11, 0x11, 0x15, 0x09, 0x16};
            case 'R' -> new int[]{0x1E, 0x11, 0x11, 0x1E, 0x14, 0x12, 0x11};
            case 'S' -> new int[]{0x0E, 0x11, 0x10, 0x0E, 0x01, 0x11, 0x0E};
            case 'T' -> new int[]{0x1F, 0x04, 0x04, 0x04, 0x04, 0x04, 0x04};
            case 'U' -> new int[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x11, 0x0E};
            case 'V' -> new int[]{0x11, 0x11, 0x11, 0x11, 0x11, 0x0A, 0x04};
            case 'W' -> new int[]{0x11, 0x11, 0x11, 0x15, 0x15, 0x1B, 0x11};
            case 'X' -> new int[]{0x11, 0x11, 0x0A, 0x04, 0x0A, 0x11, 0x11};
            case 'Y' -> new int[]{0x11, 0x11, 0x0A, 0x04, 0x04, 0x04, 0x04};
            case 'Z' -> new int[]{0x1F, 0x01, 0x02, 0x04, 0x08, 0x10, 0x1F};
            case '!' -> new int[]{0x04, 0x04, 0x04, 0x04, 0x04, 0x00, 0x04};
            case '0' -> new int[]{0x0E, 0x11, 0x13, 0x15, 0x19, 0x11, 0x0E};
            case '1' -> new int[]{0x04, 0x0C, 0x04, 0x04, 0x04, 0x04, 0x0E};
            case '2' -> new int[]{0x0E, 0x11, 0x01, 0x06, 0x08, 0x10, 0x1F};
            case '3' -> new int[]{0x1E, 0x01, 0x01, 0x0E, 0x01, 0x01, 0x1E};
            case '4' -> new int[]{0x02, 0x06, 0x0A, 0x12, 0x1F, 0x02, 0x02};
            case '5' -> new int[]{0x1F, 0x10, 0x1E, 0x01, 0x01, 0x11, 0x0E};
            case '6' -> new int[]{0x06, 0x08, 0x10, 0x1E, 0x11, 0x11, 0x0E};
            case '7' -> new int[]{0x1F, 0x01, 0x02, 0x04, 0x08, 0x08, 0x08};
            case '8' -> new int[]{0x0E, 0x11, 0x11, 0x0E, 0x11, 0x11, 0x0E};
            case '9' -> new int[]{0x0E, 0x11, 0x11, 0x0F, 0x01, 0x02, 0x0C};
            default -> new int[]{0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};
        };
    }

    private static void buildGeometricVaultedCeiling(Canvas canvas, int r, int towerHeight, SceneConfig.Colors c) {
        int baseCeilingY = 22;
        int peakCeilingY = 28;

        // 100% Solid Full Concrete Blocks — Organic Stepped Vaulted Gothic Dome!
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int distFromWall = Math.min(r - Math.abs(x), r - Math.abs(z));
                int vaultY = Math.min(peakCeilingY, baseCeilingY + (int) Math.round(distFromWall * 0.6));

                boolean isCentralMedallion = (Math.abs(x) <= 4 && Math.abs(z) <= 4);
                int dist = Math.abs(x) + Math.abs(z);

                if (isCentralMedallion) {
                    if (dist <= 2) {
                        canvas.set(x, vaultY, z, isBlockS(x, z) ? c.white() : c.primaryRed());
                    } else if (dist <= 4) {
                        boolean isStar = (x == 0 || z == 0 || Math.abs(x) == Math.abs(z));
                        canvas.set(x, vaultY, z, isStar ? c.white() : c.primaryRed());
                    } else {
                        canvas.set(x, vaultY, z, c.concreteBlack());
                    }
                    if ((Math.abs(x) == 2 && Math.abs(z) == 2) || (x == 0 && Math.abs(z) == 3) || (z == 0 && Math.abs(x) == 3)) {
                        canvas.set(x, vaultY, z, "SEA_LANTERN");
                    }
                } else {
                    boolean isRib = (Math.abs(x) % 4 == 0) || (Math.abs(z) % 4 == 0) || (Math.abs(x) == Math.abs(z));
                    if (isRib) {
                        canvas.set(x, vaultY, z, c.concreteBlack());
                        if (Math.abs(x) % 4 == 0 && Math.abs(z) % 4 == 0) {
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
        placePawPrint(canvas, 5, 5, c);
        placePawPrint(canvas, -5, 5, c);
        placePawPrint(canvas, 5, -5, c);
        placePawPrint(canvas, -5, -5, c);

        // Grand central NC State "Block S" / Wolfpack medallion
        int medR = 4;
        for (int x = -medR; x <= medR; x++) {
            for (int z = -medR; z <= medR; z++) {
                int dist = Math.abs(x) + Math.abs(z);
                if (dist <= 5) {
                    canvas.set(x, 0, z, c.primaryRed());
                }
                if (dist == 5 || (Math.abs(x) == medR && Math.abs(z) <= 1) || (Math.abs(z) == medR && Math.abs(x) <= 1)) {
                    canvas.set(x, 0, z, c.concreteBlack());
                }
                if (dist == 4) {
                    canvas.set(x, 0, z, c.concreteWhite());
                }
            }
        }

        // Block S inlay in the medallion (white with bold black shadow outline)
        for (int x = -2; x <= 2; x++) {
            for (int z = -3; z <= 3; z++) {
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
        for (int x = -2; x <= 2; x++) {
            for (int z = -3; z <= 3; z++) {
                if (isBlockS(x, z)) {
                    canvas.set(x, 0, z, c.white());
                }
            }
        }

        // Grand Cardinal Pedestrian Promenades (+x East, -x West, +z South, -z North)
        int pLimit = r - 3;
        for (int x = -pLimit; x <= pLimit; x++) {
            if (Math.abs(x) > medR - 1) {
                canvas.set(x, 0, 0, c.concreteWhite());
                canvas.set(x, 0, -1, c.concreteBlack());
                canvas.set(x, 0, 1, c.concreteBlack());
            }
        }
        for (int z = -pLimit; z <= pLimit; z++) {
            if (Math.abs(z) > medR - 1) {
                canvas.set(0, 0, z, c.concreteWhite());
                canvas.set(-1, 0, z, c.concreteBlack());
                canvas.set(1, 0, z, c.concreteBlack());
            }
        }

        // 4 Wolfpack Spirit Fire Bowls / Victory Beacons around the medallion
        placeSpiritBeacon(canvas, 4, 4, c);
        placeSpiritBeacon(canvas, -4, 4, c);
        placeSpiritBeacon(canvas, 4, -4, c);
        placeSpiritBeacon(canvas, -4, -4, c);

        // Cozy plaza park benches & flower planters in open niches
        placePlazaBenchAndPlanters(canvas, 2, 6, c);
        placePlazaBenchAndPlanters(canvas, -2, 6, c);
        placePlazaBenchAndPlanters(canvas, 2, -6, c);
        placePlazaBenchAndPlanters(canvas, -2, -6, c);

        // Wolfpack Flagpoles at the 4 corners
        int flagOffset = Math.max(5, r - 3);
        placeFlagpole(canvas, flagOffset, flagOffset, c.primaryRed(), true, c);
        placeFlagpole(canvas, -flagOffset, flagOffset, c.white(), false, c);
        placeFlagpole(canvas, flagOffset, -flagOffset, c.white(), false, c);
        placeFlagpole(canvas, -flagOffset, -flagOffset, c.primaryRed(), true, c);
    }

    private static void placePawPrint(Canvas canvas, int cx, int cz, SceneConfig.Colors c) {
        canvas.fillBox(cx - 1, 0, cz, cx + 1, 0, cz + 1, c.concreteWhite());
        canvas.set(cx - 1, 0, cz - 1, c.primaryRed());
        canvas.set(cx + 1, 0, cz - 1, c.primaryRed());
        canvas.set(cx, 0, cz - 2, c.primaryRed());
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

    private static void placePlazaBenchAndPlanters(Canvas canvas, int x, int z, SceneConfig.Colors c) {
        canvas.set(x, 1, z, "DARK_OAK_STAIRS");
        canvas.set(x, 1, z + 1, "MOSS_BLOCK");
        canvas.set(x, 2, z + 1, "FLOWERING_AZALEA");
        canvas.set(x, 1, z - 1, "MOSS_BLOCK");
        canvas.set(x, 2, z - 1, "RED_TULIP");
    }

    private static boolean isBlockS(int x, int z) {
        if (z == -3 && x >= -2 && x <= 2) return true;
        if (z == -2 && (x == -2 || x == -1)) return true;
        if (z == -1 && (x == -2 || x == -1)) return true;
        if (z == 0 && x >= -2 && x <= 2) return true;
        if (z == 1 && (x == 1 || x == 2)) return true;
        if (z == 2 && (x == 1 || x == 2)) return true;
        if (z == 3 && x >= -2 && x <= 2) return true;
        return false;
    }

    private static boolean isBlockSViewedFromFront(int x, int dy) {
        if (dy == 2 && x >= -1 && x <= 1) return true;
        if (dy == 1 && (x == 1)) return true;
        if (dy == 0 && x >= -1 && x <= 1) return true;
        if (dy == -1 && (x == -1)) return true;
        if (dy == -2 && x >= -1 && x <= 1) return true;
        return false;
    }

    private static void placeFlagpole(Canvas canvas, int x, int z, String wolfpackColor, boolean isRed, SceneConfig.Colors c) {
        String banner = isRed ? "RED_BANNER" : "WHITE_BANNER";
        canvas.set(x, 1, z, c.concreteBlack());
        canvas.fillBox(x, 2, z, x, 4, z, "IRON_BARS");
        canvas.set(x, 5, z, c.concreteBlack());
        canvas.set(x, 4, z + 1, banner);
    }

    // -- Three Hyper-Fancy Nether Portals (North, West, and East) -------

    private static void buildNetherPortals(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int portalOffset = Math.max(10, r - 3);

        // 1. North Nether Portal ("Survival World / Sullivan Wilds")
        // Centered at x = 0, z = -portalOffset (facing South towards center)
        buildFancyPortalZAxis(canvas, 0, -portalOffset, 1,
                "portal-north", "portal-center", "Survival World", cfg);

        // 2. West Nether Portal ("Resource Realm / West Campus")
        // Centered at x = -portalOffset, z = 0 (facing East towards center)
        buildFancyPortalXAxis(canvas, -portalOffset, 0, 1,
                "portal-west", "portal-left", "Resource Realm", cfg);

        // 3. East Nether Portal ("Minigames Hub / Reynolds Arena")
        // Centered at x = portalOffset, z = 0 (facing West towards center)
        buildFancyPortalXAxis(canvas, portalOffset, 0, -1,
                "portal-east", "portal-right", "Minigames Hub", cfg);
    }

    /**
     * Builds a hyper-fancy Wolfpack Nether Portal oriented along the X-axis (facing +/- Z).
     */
    private static void buildFancyPortalZAxis(Canvas canvas, int cx, int cz, int facingDir,
                                              String primaryKey, String fallbackKey, String defaultDest,
                                              SceneConfig cfg) {
        SceneConfig.Colors c = cfg.colors();
        int halfW = 1; // 3 blocks wide portal opening: cx - 1 to cx + 1
        int bottomY = 2;
        int topY = 6;  // 5 blocks high portal opening

        // 1. Layered Gothic Podium Steps (y = 1) in Black Concrete, Red Concrete, and Blackstone Slabs
        canvas.fillBox(cx - 3, 1, cz - 1, cx + 3, 1, cz + 1, c.concreteBlack());
        canvas.ring(cx - 3, cz - 1, cx + 3, cz + 1, 1, c.primaryRed());
        for (int x = cx - 3; x <= cx + 3; x++) {
            String slabMat = (Math.abs(x - cx) % 2 == 0) ? "POLISHED_BLACKSTONE_SLAB" : "SMOOTH_STONE_SLAB";
            canvas.set(x, 1, cz + facingDir, slabMat);
        }

        // 2. Obsidian & Crying Obsidian Frame
        canvas.fillBox(cx - halfW, bottomY - 1, cz, cx + halfW, bottomY - 1, cz, "OBSIDIAN");
        canvas.fillBox(cx - halfW - 1, bottomY, cz, cx - halfW - 1, topY, cz, "OBSIDIAN");
        canvas.fillBox(cx + halfW + 1, bottomY, cz, cx + halfW + 1, topY, cz, "OBSIDIAN");
        canvas.fillBox(cx - halfW, topY + 1, cz, cx + halfW, topY + 1, cz, "OBSIDIAN");

        // Corner crying obsidian accents
        canvas.set(cx - halfW - 1, bottomY - 1, cz, "CRYING_OBSIDIAN");
        canvas.set(cx + halfW + 1, bottomY - 1, cz, "CRYING_OBSIDIAN");
        canvas.set(cx - halfW - 1, topY + 1, cz, "CRYING_OBSIDIAN");
        canvas.set(cx + halfW + 1, topY + 1, cz, "CRYING_OBSIDIAN");

        // 3. Nether Portal Blocks inside opening
        for (int x = cx - halfW; x <= cx + halfW; x++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(x, y, cz, "NETHER_PORTAL");
            }
        }

        // 4. Flanking Spires & Crying Obsidian Columns
        for (int side : new int[]{-1, 1}) {
            int colX = cx + side * (halfW + 2);
            canvas.fillBox(colX, 1, cz, colX, topY + 1, cz, c.concreteBlack());
            canvas.set(colX, 2, cz + facingDir, "CRYING_OBSIDIAN");
            canvas.set(colX, 3, cz + facingDir, "CRIMSON_SLAB");
            canvas.set(colX, 5, cz + facingDir, "SMOOTH_STONE_SLAB");
            canvas.set(colX, topY + 2, cz, c.primaryRed());
            canvas.set(colX, topY + 3, cz, "SEA_LANTERN");
            canvas.set(colX, topY + 4, cz, "POLISHED_BLACKSTONE_SLAB");
        }

        // 5. Gothic Pediment & Wolfpack Crown
        int gableY = topY + 2;
        canvas.fillBox(cx - halfW - 1, gableY, cz, cx + halfW + 1, gableY, cz, c.concreteBlack());
        canvas.fillBox(cx - halfW, gableY + 1, cz, cx + halfW, gableY + 1, cz, c.primaryRed());
        canvas.set(cx, gableY + 2, cz, c.concreteWhite());

        // Stepped decorative stairs / slabs on the gable
        canvas.set(cx - halfW - 1, gableY + 1, cz + facingDir, "CRIMSON_SLAB");
        canvas.set(cx + halfW + 1, gableY + 1, cz + facingDir, "CRIMSON_SLAB");
        canvas.set(cx, gableY + 2, cz + facingDir, "SMOOTH_STONE_SLAB");

        // Imperial Wolfpack Crown with Starburst & Lightning Finial
        canvas.set(cx, gableY + 1, cz + facingDir, c.white()); // Block S crest
        canvas.set(cx, gableY + 3, cz, "SEA_LANTERN");
        canvas.set(cx, gableY + 4, cz, c.primaryRed());
        canvas.set(cx, gableY + 5, cz, "LIGHTNING_ROD");

        // 6. Suspended Soul Lantern Fixtures
        canvas.set(cx - halfW, topY + 1, cz + facingDir, "CHAIN");
        canvas.set(cx - halfW, topY, cz + facingDir, "SOUL_LANTERN");
        canvas.set(cx + halfW, topY + 1, cz + facingDir, "CHAIN");
        canvas.set(cx + halfW, topY, cz + facingDir, "SOUL_LANTERN");

        // 7. Decorative Starway Approach Floor Path
        drawPortalApproachStarZ(canvas, cx, cz + 4 * facingDir, facingDir, c);

        // 8. Standing Destination Sign
        int signZ = cz + 3 * facingDir;
        int signRot = (facingDir > 0) ? 0 : 8;
        String destName = label(cfg, primaryKey, label(cfg, fallbackKey, defaultDest));
        canvas.sign(cx, 1, signZ, signRot, "NC State Portal", destName);
    }

    /**
     * Builds a hyper-fancy Wolfpack Nether Portal oriented along the Z-axis (facing +/- X).
     */
    private static void buildFancyPortalXAxis(Canvas canvas, int cx, int cz, int facingDir,
                                              String primaryKey, String fallbackKey, String defaultDest,
                                              SceneConfig cfg) {
        SceneConfig.Colors c = cfg.colors();
        int halfW = 1; // 3 blocks wide portal opening: cz - 1 to cz + 1
        int bottomY = 2;
        int topY = 6;  // 5 blocks high portal opening

        // 1. Layered Gothic Podium Steps (y = 1) in Black Concrete, Red Concrete, and Blackstone Slabs
        canvas.fillBox(cx - 1, 1, cz - 3, cx + 1, 1, cz + 3, c.concreteBlack());
        canvas.ring(cx - 1, cz - 3, cx + 1, cz + 3, 1, c.primaryRed());
        for (int z = cz - 3; z <= cz + 3; z++) {
            String slabMat = (Math.abs(z - cz) % 2 == 0) ? "POLISHED_BLACKSTONE_SLAB" : "SMOOTH_STONE_SLAB";
            canvas.set(cx + facingDir, 1, z, slabMat);
        }

        // 2. Obsidian & Crying Obsidian Frame
        canvas.fillBox(cx, bottomY - 1, cz - halfW, cx, bottomY - 1, cz + halfW, "OBSIDIAN");
        canvas.fillBox(cx, bottomY, cz - halfW - 1, cx, topY, cz - halfW - 1, "OBSIDIAN");
        canvas.fillBox(cx, bottomY, cz + halfW + 1, cx, topY, cz + halfW + 1, "OBSIDIAN");
        canvas.fillBox(cx, topY + 1, cz - halfW, cx, topY + 1, cz + halfW, "OBSIDIAN");

        // Corner crying obsidian accents
        canvas.set(cx, bottomY - 1, cz - halfW - 1, "CRYING_OBSIDIAN");
        canvas.set(cx, bottomY - 1, cz + halfW + 1, "CRYING_OBSIDIAN");
        canvas.set(cx, topY + 1, cz - halfW - 1, "CRYING_OBSIDIAN");
        canvas.set(cx, topY + 1, cz + halfW + 1, "CRYING_OBSIDIAN");

        // 3. Nether Portal Blocks inside opening
        for (int z = cz - halfW; z <= cz + halfW; z++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(cx, y, z, "NETHER_PORTAL");
            }
        }

        // 4. Flanking Spires & Crying Obsidian Columns
        for (int side : new int[]{-1, 1}) {
            int colZ = cz + side * (halfW + 2);
            canvas.fillBox(cx, 1, colZ, cx, topY + 1, colZ, c.concreteBlack());
            canvas.set(cx + facingDir, 2, colZ, "CRYING_OBSIDIAN");
            canvas.set(cx + facingDir, 3, colZ, "CRIMSON_SLAB");
            canvas.set(cx + facingDir, 5, colZ, "SMOOTH_STONE_SLAB");
            canvas.set(cx, topY + 2, colZ, c.primaryRed());
            canvas.set(cx, topY + 3, colZ, "SEA_LANTERN");
            canvas.set(cx, topY + 4, colZ, "POLISHED_BLACKSTONE_SLAB");
        }

        // 5. Gothic Pediment & Wolfpack Crown
        int gableY = topY + 2;
        canvas.fillBox(cx, gableY, cz - halfW - 1, cx, gableY, cz + halfW + 1, c.concreteBlack());
        canvas.fillBox(cx, gableY + 1, cz - halfW, cx, gableY + 1, cz + halfW, c.primaryRed());
        canvas.set(cx, gableY + 2, cz, c.concreteWhite());

        // Stepped decorative stairs / slabs on the gable
        canvas.set(cx + facingDir, gableY + 1, cz - halfW - 1, "CRIMSON_SLAB");
        canvas.set(cx + facingDir, gableY + 1, cz + halfW + 1, "CRIMSON_SLAB");
        canvas.set(cx + facingDir, gableY + 2, cz, "SMOOTH_STONE_SLAB");

        // Imperial Wolfpack Crown with Starburst & Lightning Finial
        canvas.set(cx + facingDir, gableY + 1, cz, c.white()); // Block S crest
        canvas.set(cx, gableY + 3, cz, "SEA_LANTERN");
        canvas.set(cx, gableY + 4, cz, c.primaryRed());
        canvas.set(cx, gableY + 5, cz, "LIGHTNING_ROD");

        // 6. Suspended Soul Lantern Fixtures
        canvas.set(cx + facingDir, topY + 1, cz - halfW, "CHAIN");
        canvas.set(cx + facingDir, topY, cz - halfW, "SOUL_LANTERN");
        canvas.set(cx + facingDir, topY + 1, cz + halfW, "CHAIN");
        canvas.set(cx + facingDir, topY, cz + halfW, "SOUL_LANTERN");

        // 7. Decorative Starway Approach Floor Path
        drawPortalApproachStarX(canvas, cx + 4 * facingDir, cz, facingDir, c);

        // 8. Standing Destination Sign
        int signX = cx + 3 * facingDir;
        int signRot = (facingDir > 0) ? 4 : 12;
        String destName = label(cfg, primaryKey, label(cfg, fallbackKey, defaultDest));
        canvas.sign(signX, 1, cz, signRot, "NC State Portal", destName);
    }

    private static void drawPortalApproachStarZ(Canvas canvas, int cx, int cz, int facingDir, SceneConfig.Colors c) {
        canvas.set(cx, 0, cz, "SEA_LANTERN");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist == 1) {
                    canvas.set(cx + dx, 0, cz + dz, c.white());
                } else if (dist == 2) {
                    boolean isRay = (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
                    canvas.set(cx + dx, 0, cz + dz, isRay ? c.primaryRed() : c.concreteBlack());
                }
            }
        }
        // Energy lines connecting portal to star
        for (int d = 1; d <= 2; d++) {
            canvas.set(cx - 1, 0, cz - d * facingDir, "CRIMSON_SLAB");
            canvas.set(cx + 1, 0, cz - d * facingDir, "SMOOTH_STONE_SLAB");
        }
    }

    private static void drawPortalApproachStarX(Canvas canvas, int cx, int cz, int facingDir, SceneConfig.Colors c) {
        canvas.set(cx, 0, cz, "SEA_LANTERN");
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                int dist = Math.abs(dx) + Math.abs(dz);
                if (dist == 1) {
                    canvas.set(cx + dx, 0, cz + dz, c.white());
                } else if (dist == 2) {
                    boolean isRay = (dx == 0 || dz == 0 || Math.abs(dx) == Math.abs(dz));
                    canvas.set(cx + dx, 0, cz + dz, isRay ? c.primaryRed() : c.concreteBlack());
                }
            }
        }
        // Energy lines connecting portal to star
        for (int d = 1; d <= 2; d++) {
            canvas.set(cx - d * facingDir, 0, cz - 1, "CRIMSON_SLAB");
            canvas.set(cx - d * facingDir, 0, cz + 1, "SMOOTH_STONE_SLAB");
        }
    }

    // -- Memorial Belltower (North-West Quadrant) ------------------------

    private static void buildBelltower(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        int height = cfg.towerHeight();
        SceneConfig.Colors c = cfg.colors();

        int halfFoot = 2; // 5x5 shaft
        int halfPodium = 2; // 5x5 base podium
        int bx = -Math.max(6, r - 9);
        int bz = -Math.max(6, r - 9);

        // 1. Stepped Podium (y = 1) in pure concrete
        canvas.fillBox(bx - halfPodium, 1, bz - halfPodium, bx + halfPodium, 1, bz + halfPodium, c.concreteBlack());
        canvas.ring(bx - halfFoot, bz - halfFoot, bx + halfFoot, bz + halfFoot, 1, c.primaryRed());

        // 2. Ground Floor Memorial Rotunda (y = 2 to 5)
        for (int y = 2; y <= 5; y++) {
            canvas.ring(bx - halfFoot, bz - halfFoot, bx + halfFoot, bz + halfFoot, y, c.concreteWhite());
            canvas.set(bx - halfFoot, y, bz - halfFoot, c.concreteBlack());
            canvas.set(bx + halfFoot, y, bz - halfFoot, c.concreteBlack());
            canvas.set(bx - halfFoot, y, bz + halfFoot, c.concreteBlack());
            canvas.set(bx + halfFoot, y, bz + halfFoot, c.concreteBlack());
        }

        // 4 Archway entrances
        canvas.fillBox(bx - 1, 2, bz + halfFoot, bx + 1, 4, bz + halfFoot, "AIR");
        canvas.fillBox(bx - 1, 2, bz - halfFoot, bx + 1, 4, bz - halfFoot, "AIR");
        canvas.fillBox(bx + halfFoot, 2, bz - 1, bx + halfFoot, 4, bz + 1, "AIR");
        canvas.fillBox(bx - halfFoot, 2, bz - 1, bx - halfFoot, 4, bz + 1, "AIR");

        // Central Memorial Beacon
        canvas.set(bx, 2, bz, "SEA_LANTERN");
        canvas.set(bx, 3, bz, c.primaryRed());
        canvas.set(bx, 5, bz, "LANTERN");

        // 3. Main Tower Shaft (y = 6 to height - 5)
        int shaftTop = Math.max(6, height - 5);
        for (int y = 6; y <= shaftTop; y++) {
            canvas.ring(bx - halfFoot, bz - halfFoot, bx + halfFoot, bz + halfFoot, y, c.concreteWhite());

            canvas.set(bx - halfFoot, y, bz - halfFoot, c.concreteBlack());
            canvas.set(bx + halfFoot, y, bz - halfFoot, c.concreteBlack());
            canvas.set(bx - halfFoot, y, bz + halfFoot, c.concreteBlack());
            canvas.set(bx + halfFoot, y, bz + halfFoot, c.concreteBlack());

            canvas.set(bx, y, bz - halfFoot, c.primaryRed());
            canvas.set(bx, y, bz + halfFoot, c.primaryRed());
            canvas.set(bx - halfFoot, y, bz, c.primaryRed());
            canvas.set(bx + halfFoot, y, bz, c.primaryRed());
        }

        // 4. Belfry / Bell Chamber (y = height - 4 to height - 3)
        int belfryBottom = height - 4;
        int belfryTop = height - 3;
        canvas.fillBox(bx - halfFoot, belfryBottom, bz - halfFoot, bx + halfFoot, belfryBottom, bz + halfFoot, c.concreteBlack());
        for (int y = belfryBottom + 1; y <= belfryTop; y++) {
            canvas.ring(bx - halfFoot, bz - halfFoot, bx + halfFoot, bz + halfFoot, y, c.concreteWhite());
            canvas.set(bx - halfFoot, y, bz - halfFoot, c.concreteBlack());
            canvas.set(bx + halfFoot, y, bz - halfFoot, c.concreteBlack());
            canvas.set(bx - halfFoot, y, bz + halfFoot, c.concreteBlack());
            canvas.set(bx + halfFoot, y, bz + halfFoot, c.concreteBlack());
        }
        // Suspended Bell
        canvas.set(bx, belfryTop, bz, "CHAIN");
        canvas.set(bx, belfryTop - 1, bz, "BELL");
        canvas.set(bx, belfryBottom + 1, bz, c.primaryRed());
        canvas.set(bx, belfryBottom, bz, "SEA_LANTERN");

        // 5. Four-Sided Clock (y = height - 2 to height - 1)
        int clockY = height - 2;
        for (int y = height - 2; y <= height - 1; y++) {
            canvas.ring(bx - halfFoot, bz - halfFoot, bx + halfFoot, bz + halfFoot, y, c.primaryRed());
        }
        buildClockFace(canvas, bx, clockY, bz + halfFoot, 1, 0, c);
        buildClockFace(canvas, bx, clockY, bz - halfFoot, 1, 0, c);
        buildClockFace(canvas, bx + halfFoot, clockY, bz, 0, 1, c);
        buildClockFace(canvas, bx - halfFoot, clockY, bz, 0, 1, c);

        // 6. Cornice & Parapet (y = height)
        canvas.ring(bx - halfFoot - 1, bz - halfFoot - 1, bx + halfFoot + 1, bz + halfFoot + 1, height, c.concreteBlack());

        // Corner Pinnacles
        int[][] corners = {{-halfFoot, -halfFoot}, {halfFoot, -halfFoot}, {-halfFoot, halfFoot}, {halfFoot, halfFoot}};
        for (int[] corner : corners) {
            int cx = bx + corner[0];
            int cdz = bz + corner[1];
            canvas.set(cx, height + 1, cdz, c.concreteWhite());
            canvas.set(cx, height + 2, cdz, "LIGHTNING_ROD");
        }

        // 7. Stepped Pyramidal Roof & Glowing Red Spire (y = height + 1 to height + 4)
        int roofY = height + 1;
        canvas.fillBox(bx - 1, roofY, bz - 1, bx + 1, roofY, bz + 1, c.concreteBlack());
        canvas.set(bx, roofY + 1, bz, c.primaryRed());
        canvas.set(bx, roofY + 2, bz, "SEA_LANTERN");
        canvas.set(bx, roofY + 3, bz, c.primaryRed());
        canvas.set(bx, roofY + 4, bz, "LIGHTNING_ROD");

        // Landmark Sign
        canvas.sign(bx, 1, bz + halfPodium + 2, 0, "NC State Wolfpack", label(cfg, "belltower", "Memorial Belltower"));
    }

    private static void buildClockFace(Canvas canvas, int cx, int cy, int cz, int dxStep, int dzStep, SceneConfig.Colors c) {
        canvas.set(cx, cy, cz, c.black());
        canvas.set(cx + dxStep, cy, cz + dzStep, c.white());
        canvas.set(cx - dxStep, cy, cz - dzStep, c.white());
        canvas.set(cx, cy + 1, cz, c.white());
    }

    // -- Wolf mascot statue (North-East Quadrant) -------------------------

    private static void buildWolfStatue(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int baseX = Math.max(6, r - 9);
        int baseZ = -Math.max(6, r - 9);

        // 1. Multi-tiered Pedestal (y = 1 to 2) in pure concrete
        canvas.fillBox(baseX - 2, 1, baseZ - 2, baseX + 2, 1, baseZ + 2, c.concreteBlack());
        canvas.fillBox(baseX - 1, 2, baseZ - 1, baseX + 1, 2, baseZ + 1, c.primaryRed());

        // 2. Sculpted Wolf Body in pure Concrete (y = 3 to 8)
        canvas.set(baseX - 1, 3, baseZ - 1, c.concreteBlack());
        canvas.set(baseX + 1, 3, baseZ - 1, c.concreteBlack());
        canvas.set(baseX - 1, 3, baseZ + 1, c.concreteBlack());
        canvas.set(baseX + 1, 3, baseZ + 1, c.concreteBlack());

        // Torso & Wolfpack Red Coat
        canvas.fillBox(baseX - 1, 4, baseZ - 1, baseX + 1, 5, baseZ + 1, c.primaryRed());
        canvas.set(baseX, 4, baseZ, c.concreteWhite());

        // Tail
        canvas.set(baseX + 2, 5, baseZ + 1, c.primaryRed());

        // Chest & Neck
        canvas.fillBox(baseX - 1, 5, baseZ - 1, baseX + 1, 6, baseZ - 1, c.primaryRed());
        canvas.set(baseX, 5, baseZ - 1, c.concreteWhite());

        // Howling Wolf Head & Snout
        canvas.fillBox(baseX - 1, 7, baseZ - 2, baseX + 1, 7, baseZ - 1, c.primaryRed());
        canvas.set(baseX, 7, baseZ - 3, c.concreteWhite());
        canvas.set(baseX, 8, baseZ - 3, c.black());
        canvas.set(baseX - 1, 7, baseZ - 1, c.primaryRed());
        canvas.set(baseX + 1, 7, baseZ - 1, c.primaryRed());
        canvas.set(baseX - 1, 8, baseZ - 1, c.concreteBlack());
        canvas.set(baseX + 1, 8, baseZ - 1, c.concreteBlack());

        // Plaque Sign
        canvas.sign(baseX, 1, baseZ + 3, 0, "NC State Wolfpack", label(cfg, "wolf-statue", "Howl at the Wolfpack statue"));
    }

    // -- Free Expression Tunnel-style mural walkway (South-West Area) ----

    private static void buildTunnel(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int xCenter = -Math.max(7, r - 8);
        int zCenter = 6;
        int halfW = 2;
        int xMin = xCenter - halfW;
        int xMax = xCenter + halfW;
        int zLen = 2;

        // Walkway Floor in pure concrete
        canvas.fillBox(xMin, 0, zCenter - zLen, xMax, 0, zCenter + zLen, c.concreteBlack());
        for (int z = zCenter - zLen; z <= zCenter + zLen; z++) {
            canvas.set(xCenter, 0, z, c.concreteWhite());
            canvas.set(xMin + 1, 0, z, c.primaryRed());
            canvas.set(xMax - 1, 0, z, c.primaryRed());
        }

        // Outer Vault Walls in pure concrete
        for (int z = zCenter - zLen; z <= zCenter + zLen; z++) {
            canvas.fillBox(xMin, 1, z, xMin, 3, z, c.concreteBlack());
            canvas.fillBox(xMax, 1, z, xMax, 3, z, c.concreteBlack());
            canvas.set(xMin + 1, 4, z, c.primaryRed());
            canvas.set(xMax - 1, 4, z, c.primaryRed());
            canvas.set(xCenter, 4, z, c.concreteBlack());
        }

        // Arched Entry Portals (North and South)
        for (int endZ : new int[]{zCenter - zLen, zCenter + zLen}) {
            canvas.ring(xMin, endZ, xMax, endZ, 1, c.concreteWhite());
            canvas.ring(xMin, endZ, xMax, endZ, 2, c.concreteWhite());
            canvas.fillBox(xMin + 1, 1, endZ, xMax - 1, 2, endZ, "AIR");
            canvas.set(xCenter, 3, endZ, c.primaryRed());
        }

        // Ceiling Lantern Fixtures
        for (int z = zCenter - zLen + 1; z <= zCenter + zLen - 1; z += 2) {
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
        for (int z = zCenter - zLen + 1; z <= zCenter + zLen - 1; z++) {
            for (int y = 1; y <= 2; y++) {
                String m1 = muralMaterials[Math.floorMod(idx + y, muralMaterials.length)];
                String m2 = muralMaterials[Math.floorMod(idx * 2 + y, muralMaterials.length)];
                canvas.set(xMin + 1, y, z, m1);
                canvas.set(xMax - 1, y, z, m2);
            }
            idx++;
        }

        // Entrance Sign
        canvas.sign(xCenter, 1, zCenter - zLen - 1, 0, "NC State Wolfpack", label(cfg, "tunnel", "Free Expression Tunnel"));
    }

    // -- Sullivan Residence Hall & Talley Student Union Lounge (South Side) --

    private static void buildUnionFacade(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int z = Math.max(8, r - 3);
        int halfWidth = Math.min(7, r / 2);
        int height = 10;

        // 1. Backing Wall of Red Concrete
        canvas.fillBox(-halfWidth, 1, z, halfWidth, height, z, c.primaryRed());

        // 2. Modern White Concrete Structural Columns
        for (int x = -halfWidth; x <= halfWidth; x += 3) {
            canvas.fillBox(x, 1, z, x, height, z, c.concreteWhite());
        }

        // 3. Modern Tinted Panels
        for (int x = -halfWidth + 1; x <= halfWidth - 1; x++) {
            if (Math.abs(x % 3) != 0) {
                canvas.fillBox(x, 2, z, x, 4, z, c.concreteBlack());
                canvas.fillBox(x, 6, z, x, 8, z, c.concreteBlack());
            }
        }

        // 4. Second-Story Balcony
        canvas.fillBox(-halfWidth, 5, z - 1, halfWidth, 5, z, c.concreteBlack());
        canvas.fillBox(-halfWidth, 6, z - 1, halfWidth, 6, z - 1, "IRON_BARS");

        // 5. Sullivan Residence Hall Cozy Fireplace & Hearth Centerpiece
        // Fireplace hearth base (x = -2 to 2, y = 1 to 3)
        canvas.fillBox(-2, 1, z - 1, 2, 1, z, c.concreteBlack());
        canvas.set(0, 1, z - 1, "CRIMSON_SLAB");
        canvas.set(-1, 1, z - 1, "POLISHED_BLACKSTONE_SLAB");
        canvas.set(1, 1, z - 1, "POLISHED_BLACKSTONE_SLAB");
        canvas.set(0, 2, z - 1, "IRON_BARS"); // Fireplace grate
        canvas.fillBox(-1, 2, z, 1, 4, z, c.concreteBlack()); // Firebox backing
        canvas.fillBox(-2, 2, z, -2, 4, z, c.primaryRed());   // Left jamb
        canvas.fillBox(2, 2, z, 2, 4, z, c.primaryRed());    // Right jamb

        // Mantelpiece (y = 4) with potted tulips & lanterns
        canvas.fillBox(-2, 4, z - 1, 2, 4, z - 1, "SMOOTH_STONE_SLAB");
        canvas.set(-1, 5, z - 1, "RED_TULIP");
        canvas.set(1, 5, z - 1, "WHITE_TULIP");
        canvas.set(0, 5, z - 1, "LANTERN");

        // 6. Cozy Sullivan Lounge Furniture & Armchairs
        // Left armchair
        canvas.set(-4, 1, z - 2, "CRIMSON_STAIRS");
        canvas.set(-5, 1, z - 2, "BOOKSHELF");
        canvas.set(-5, 2, z - 2, "LANTERN");

        // Right armchair
        canvas.set(4, 1, z - 2, "CRIMSON_STAIRS");
        canvas.set(5, 1, z - 2, "BOOKSHELF");
        canvas.set(5, 2, z - 2, "LANTERN");

        // Cozy Wolfpack Rug in front of fireplace
        canvas.fillBox(-3, 0, z - 3, 3, 0, z - 1, c.primaryRed());
        canvas.fillBox(-2, 0, z - 3, 2, 0, z - 2, c.concreteWhite());
        canvas.set(0, 0, z - 2, c.concreteBlack());

        // Header Parapet
        canvas.fillBox(-halfWidth, height, z, halfWidth, height, z, c.concreteBlack());

        // Landmark Sign
        canvas.sign(0, 1, z - 4, 0, "NC State Wolfpack", label(cfg, "union-facade", "Talley Student Union"));
    }

    // -- D. H. Hill Jr. Library-style facade (South-East Area) ------------

    private static void buildLibraryFacade(Canvas canvas, SceneConfig cfg) {
        int r = cfg.plazaRadius();
        SceneConfig.Colors c = cfg.colors();

        int xCenter = Math.max(7, r - 8);
        int zCenter = 6;
        int halfWidth = 2;
        int height = 8;

        // Backing Wall of Red Concrete
        canvas.fillBox(xCenter - halfWidth, 1, zCenter + halfWidth, xCenter + halfWidth, height, zCenter + halfWidth, c.primaryRed());

        // Collegiate White Concrete Columns
        canvas.fillBox(xCenter - halfWidth, 1, zCenter + halfWidth, xCenter - halfWidth, height, zCenter + halfWidth, c.concreteWhite());
        canvas.fillBox(xCenter + halfWidth, 1, zCenter + halfWidth, xCenter + halfWidth, height, zCenter + halfWidth, c.concreteWhite());

        // Bookshelves & Study Desks
        for (int x = xCenter - halfWidth + 1; x <= xCenter + halfWidth - 1; x++) {
            canvas.fillBox(x, 1, zCenter + halfWidth, x, 4, zCenter + halfWidth, "BOOKSHELF");
            canvas.set(x, 1, zCenter + halfWidth - 1, "SMOOTH_STONE_SLAB"); // Study desk
            canvas.set(x, 2, zCenter + halfWidth - 1, "LANTERN");
        }

        // Potted Azalea Plants
        canvas.set(xCenter - halfWidth, 1, zCenter + halfWidth - 1, "MOSS_BLOCK");
        canvas.set(xCenter - halfWidth, 2, zCenter + halfWidth - 1, "FLOWERING_AZALEA");
        canvas.set(xCenter + halfWidth, 1, zCenter + halfWidth - 1, "MOSS_BLOCK");
        canvas.set(xCenter + halfWidth, 2, zCenter + halfWidth - 1, "AZALEA_LEAVES");

        // Header Parapet
        canvas.fillBox(xCenter - halfWidth, height, zCenter + halfWidth, xCenter + halfWidth, height, zCenter + halfWidth, c.concreteBlack());

        // Landmark Sign
        canvas.sign(xCenter, 1, zCenter - 1, 0, "NC State Wolfpack", label(cfg, "library-facade", "D. H. Hill Jr. Library"));
    }

    // =========================================================================
    // COMPACT LOBBY — 15x15x15 Wolfpack-themed enclosed server-selector hub
    // =========================================================================

    private static void buildCompactLobby(Canvas canvas, SceneConfig cfg) {
        SceneConfig.Colors c = cfg.colors();
        int r = 7; // fixed: gives -7..7 = 15x15 footprint
        int h = 14; // ceiling y (0=floor, 14=ceiling)

        buildCompactFloor(canvas, r, c);
        buildCompactWalls(canvas, r, h, c);
        buildCompactCeiling(canvas, r, h, c);
        buildCompactPortalNorth(canvas, r, c, cfg);
        buildCompactPortalWest(canvas, r, c, cfg);
        buildCompactPortalEast(canvas, r, c, cfg);
    }

    private static void buildCompactFloor(Canvas canvas, int r, SceneConfig.Colors c) {
        // Brickyard-style 2x2 tile parquet floor
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                int tile = ((Math.floorDiv(x, 2) + Math.floorDiv(z, 2)) & 1);
                canvas.set(x, 0, z, tile == 0 ? c.concreteRed() : c.concreteBlack());
            }
        }
        // Perimeter border ring
        canvas.ring(-r, -r, r, r, 0, c.concreteBlack());
        canvas.ring(-(r - 1), -(r - 1), r - 1, r - 1, 0, c.primaryRed());

        // Central NC State medallion
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                canvas.set(x, 0, z, c.primaryRed());
            }
        }
        // Block S inlay on floor
        for (int x = -1; x <= 1; x++) {
            for (int z = -2; z <= 2; z++) {
                if (isCompactBlockS(x, z)) {
                    canvas.set(x, 0, z, c.white());
                }
            }
        }

        // Paw prints in 4 quadrants
        placeCompactPawPrint(canvas, 4, 4, c);
        placeCompactPawPrint(canvas, -4, 4, c);
        placeCompactPawPrint(canvas, 4, -4, c);
        placeCompactPawPrint(canvas, -4, -4, c);

        // Spirit beacons (y=1) on corners flanking center
        placeCompactBeacon(canvas, 3, 3, c);
        placeCompactBeacon(canvas, -3, 3, c);
        placeCompactBeacon(canvas, 3, -3, c);
        placeCompactBeacon(canvas, -3, -3, c);
    }

    private static boolean isCompactBlockS(int x, int z) {
        if (z == -2 && x >= -1 && x <= 1) return true;
        if (z == -1 && x == -1) return true;
        if (z == 0 && x >= -1 && x <= 1) return true;
        if (z == 1 && x == 1) return true;
        if (z == 2 && x >= -1 && x <= 1) return true;
        return false;
    }

    private static void placeCompactPawPrint(Canvas canvas, int cx, int cz, SceneConfig.Colors c) {
        canvas.set(cx, 0, cz, c.concreteWhite());
        canvas.set(cx - 1, 0, cz, c.concreteWhite());
        canvas.set(cx + 1, 0, cz, c.concreteWhite());
        canvas.set(cx, 0, cz - 1, c.primaryRed());
    }

    private static void placeCompactBeacon(Canvas canvas, int x, int z, SceneConfig.Colors c) {
        canvas.set(x, 1, z, "SEA_LANTERN");
        canvas.set(x, 2, z, c.primaryRed());
        canvas.set(x + 1, 1, z, "IRON_BARS");
        canvas.set(x - 1, 1, z, "IRON_BARS");
        canvas.set(x, 1, z + 1, "IRON_BARS");
        canvas.set(x, 1, z - 1, "IRON_BARS");
    }

    private static void buildCompactWalls(Canvas canvas, int r, int h, SceneConfig.Colors c) {
        int wallTop = h - 1; // y=13 is highest wall block (ceiling is y=14)

        // === NORTH WALL (z=-r, x from -7 to 7) ===
        for (int y = 1; y <= wallTop; y++) {
            for (int x = -r; x <= r; x++) {
                canvas.set(x, y, -r, getCompactWallBase(x, y, wallTop, c));
            }
        }
        drawCompactWolfNorth(canvas, -r, c);

        // === SOUTH WALL (z=+r, x from -7 to 7) ===
        for (int y = 1; y <= wallTop; y++) {
            for (int x = -r; x <= r; x++) {
                canvas.set(x, y, r, getCompactWallBase(x, y, wallTop, c));
            }
        }
        drawCompactBlockSShield(canvas, r, c);
        drawCompactClawMarks(canvas, -5, 3, r, c);
        drawCompactClawMarks(canvas, 5, 3, r, c);

        // === WEST WALL (x=-r, z from -7 to 7) ===
        for (int y = 1; y <= wallTop; y++) {
            for (int z = -r; z <= r; z++) {
                canvas.set(-r, y, z, getCompactWallBase(z, y, wallTop, c));
            }
        }
        drawCompactMoonWolf(canvas, -r, c);

        // === EAST WALL (x=+r, z from -7 to 7) ===
        for (int y = 1; y <= wallTop; y++) {
            for (int z = -r; z <= r; z++) {
                canvas.set(r, y, z, getCompactWallBase(z, y, wallTop, c));
            }
        }
        drawCompactStarburst(canvas, r, c);
    }

    private static String getCompactWallBase(int coord, int y, int wallTop, SceneConfig.Colors c) {
        // Baseboard
        if (y == 1) return c.concreteBlack();
        if (y == 2) return c.primaryRed();
        // Cornice
        if (y == wallTop) return c.concreteBlack();
        if (y == wallTop - 1) return c.primaryRed();
        // Pilaster columns every 4 blocks
        if (Math.abs(coord) % 4 == 0) {
            return (y % 3 == 0) ? c.primaryRed() : c.concreteBlack();
        }
        // Chevron tapestry
        int ch = (Math.abs(coord) + y) % 4;
        if (ch == 0) return c.concreteBlack();
        if (ch == 1 || ch == 2) return c.primaryRed();
        return c.concreteBlack();
    }

    private static void drawCompactWolfNorth(Canvas canvas, int z, SceneConfig.Colors c) {
        // Each row: index 0 = bottom (y=2), index 6 = top (y=8)
        // Bit 6=leftmost(x=-3), bit 0=rightmost(x=3)
        int[][] wolf = {
            {2, 0b0001000}, // ear tip
            {3, 0b0001110}, // ear
            {4, 0b0011111}, // head
            {5, 0b0111111}, // brow + eyes
            {6, 0b1111110}, // muzzle
            {7, 0b0110000}, // open jaw
            {8, 0b0011111}, // base / mane
        };
        for (int[] row : wolf) {
            int y = row[0];
            int bits = row[1];
            for (int col = 0; col < 7; col++) {
                if (((bits >> (6 - col)) & 1) == 1) {
                    int x = -3 + col;
                    String mat;
                    if (y == 5 && (col == 4 || col == 5)) {
                        mat = c.primaryRed(); // glowing eyes
                    } else if (y <= 3) {
                        mat = c.concreteBlack(); // ear shadow
                    } else {
                        mat = c.white();
                    }
                    canvas.set(x, y, z, mat);
                }
            }
        }
    }

    private static void drawCompactBlockSShield(Canvas canvas, int z, SceneConfig.Colors c) {
        // Shield frame (x=-4 to 4, y=2 to 8)
        canvas.fillBox(-4, 2, z, 4, 8, z, c.concreteBlack());
        // Red border
        for (int x = -4; x <= 4; x++) {
            canvas.set(x, 2, z, c.primaryRed());
            canvas.set(x, 8, z, c.primaryRed());
        }
        for (int y = 3; y <= 7; y++) {
            canvas.set(-4, y, z, c.primaryRed());
            canvas.set(4, y, z, c.primaryRed());
        }
        // NC State Block S in the center (white on black, x=-2 to 2, y=3 to 7)
        // Top row y=7
        canvas.set(-2, 7, z, c.white()); canvas.set(-1, 7, z, c.white()); canvas.set(0, 7, z, c.white()); canvas.set(1, 7, z, c.white()); canvas.set(2, 7, z, c.white());
        // y=6: x=-2,-1 (top-left)
        canvas.set(-2, 6, z, c.white()); canvas.set(-1, 6, z, c.white());
        // Middle row y=5
        canvas.set(-2, 5, z, c.white()); canvas.set(-1, 5, z, c.white()); canvas.set(0, 5, z, c.white()); canvas.set(1, 5, z, c.white()); canvas.set(2, 5, z, c.white());
        // y=4: x=1,2 (bottom-right)
        canvas.set(1, 4, z, c.white()); canvas.set(2, 4, z, c.white());
        // Bottom row y=3
        canvas.set(-2, 3, z, c.white()); canvas.set(-1, 3, z, c.white()); canvas.set(0, 3, z, c.white()); canvas.set(1, 3, z, c.white()); canvas.set(2, 3, z, c.white());
    }

    private static void drawCompactClawMarks(Canvas canvas, int xCenter, int yBase, int z, SceneConfig.Colors c) {
        for (int i = 0; i < 4; i++) {
            canvas.set(xCenter - 1, yBase + i, z, c.primaryRed());
            canvas.set(xCenter + 1, yBase + i, z, c.primaryRed());
            canvas.set(xCenter, yBase + i, z, c.white());
        }
    }

    private static void drawCompactMoonWolf(Canvas canvas, int x, SceneConfig.Colors c) {
        // Crescent moon (z=-4 to -1, y=6 to 9) — white circle with bite taken out
        int[][] moonPixels = {
            {0, -4, 8}, {0, -3, 8}, {0, -2, 8},
            {0, -4, 7}, {0, -2, 7},
            {0, -4, 6}, {0, -3, 6},
        };
        for (int[] px : moonPixels) {
            canvas.set(x, px[2], px[1], c.white());
        }
        // Howling wolf toward the moon (z=1 to 4, y=2 to 6)
        int[][] miniWolf = {
            {4, 0b001},  // ear
            {5, 0b011},  // head
            {6, 0b111},  // body
            {7, 0b111},  // howl
            {8, 0b011},  // base
        };
        for (int[] row : miniWolf) {
            int y = row[0];
            int bits = row[1];
            for (int col = 0; col < 3; col++) {
                if (((bits >> (2 - col)) & 1) == 1) {
                    int z = 2 + col;
                    canvas.set(x, y, z, (y == 7 && col == 1) ? c.primaryRed() : c.white());
                }
            }
        }
    }

    private static void drawCompactStarburst(Canvas canvas, int x, SceneConfig.Colors c) {
        // Large diamond at center (z=0, y=5)
        int centerY = 5;
        int centerZ = 0;
        for (int dy = -3; dy <= 3; dy++) {
            for (int dz = -3; dz <= 3; dz++) {
                int dist = Math.abs(dy) + Math.abs(dz);
                if (dist <= 3) {
                    String mat = (dist <= 1) ? "SEA_LANTERN" :
                                 (dist == 2) ? c.white() : c.primaryRed();
                    canvas.set(x, centerY + dy, centerZ + dz, mat);
                }
            }
        }
    }

    private static void buildCompactCeiling(Canvas canvas, int r, int h, SceneConfig.Colors c) {
        // Solid ceiling at y=h
        for (int x = -r; x <= r; x++) {
            for (int z = -r; z <= r; z++) {
                boolean isBeam = (Math.abs(x) % 3 == 0) || (Math.abs(z) % 3 == 0);
                boolean isCenterMedallion = (Math.abs(x) <= 2 && Math.abs(z) <= 2);
                if (isCenterMedallion) {
                    int dist = Math.abs(x) + Math.abs(z);
                    canvas.set(x, h, z, dist <= 1 ? "SEA_LANTERN" : c.primaryRed());
                } else if (isBeam) {
                    canvas.set(x, h, z, c.concreteBlack());
                } else {
                    int pattern = (Math.abs(x) + Math.abs(z)) % 3;
                    canvas.set(x, h, z, pattern == 0 ? c.primaryRed() : (pattern == 1 ? c.white() : c.concreteBlack()));
                }
            }
        }
        // Chandelier chains and lanterns hanging from ceiling
        canvas.set(0, h - 1, 0, "CHAIN");
        canvas.set(0, h - 2, 0, "LANTERN");
        // Corner ceiling lanterns
        for (int[] corner : new int[][]{{-4, -4}, {4, -4}, {-4, 4}, {4, 4}}) {
            canvas.set(corner[0], h - 1, corner[1], "CHAIN");
            canvas.set(corner[0], h - 2, corner[1], "SOUL_LANTERN");
        }
    }

    private static void buildCompactPortalNorth(Canvas canvas, int r, SceneConfig.Colors c, SceneConfig cfg) {
        int cz = -r; // north wall z
        int cx = 0;  // centered on x=0
        int bottomY = 1;
        int topY = 5; // 5 blocks tall portal

        // Carve the opening in the wall (3 wide x 5 tall: x=-1,0,1 y=1..5)
        for (int x = -1; x <= 1; x++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(x, y, cz, "AIR");
            }
        }

        // Obsidian frame — bottom sill
        for (int x = -1; x <= 1; x++) canvas.set(x, bottomY - 1, cz, "OBSIDIAN");
        // Sides
        for (int y = bottomY; y <= topY; y++) {
            canvas.set(-2, y, cz, "OBSIDIAN");
            canvas.set(2, y, cz, "OBSIDIAN");
        }
        // Top lintel
        for (int x = -1; x <= 1; x++) canvas.set(x, topY + 1, cz, "OBSIDIAN");
        // Crying obsidian corners
        canvas.set(-2, bottomY - 1, cz, "CRYING_OBSIDIAN");
        canvas.set(2, bottomY - 1, cz, "CRYING_OBSIDIAN");
        canvas.set(-2, topY + 1, cz, "CRYING_OBSIDIAN");
        canvas.set(2, topY + 1, cz, "CRYING_OBSIDIAN");

        // Nether portal blocks
        for (int x = -1; x <= 1; x++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(x, y, cz, "NETHER_PORTAL");
            }
        }

        // Left pillar decoration (x=-3)
        canvas.fillBox(-3, 1, cz, -3, topY + 2, cz, c.concreteBlack());
        canvas.set(-3, 2, cz, "CRYING_OBSIDIAN");
        canvas.set(-3, 4, cz, "SOUL_LANTERN");
        canvas.set(-3, topY + 1, cz, c.primaryRed());
        canvas.set(-3, topY + 2, cz, "SEA_LANTERN");
        // Right pillar decoration (x=3)
        canvas.fillBox(3, 1, cz, 3, topY + 2, cz, c.concreteBlack());
        canvas.set(3, 2, cz, "CRYING_OBSIDIAN");
        canvas.set(3, 4, cz, "SOUL_LANTERN");
        canvas.set(3, topY + 1, cz, c.primaryRed());
        canvas.set(3, topY + 2, cz, "SEA_LANTERN");

        // Gothic pediment above portal
        for (int x = -2; x <= 2; x++) canvas.set(x, topY + 1, cz, c.concreteBlack());
        for (int x = -1; x <= 1; x++) canvas.set(x, topY + 2, cz, c.primaryRed());
        canvas.set(cx, topY + 3, cz, c.white());
        canvas.set(cx, topY + 4, cz, "LIGHTNING_ROD");

        // Approach floor decorations
        canvas.set(cx, 0, cz + 1, "SEA_LANTERN");
        canvas.set(cx - 1, 0, cz + 1, "CRIMSON_SLAB");
        canvas.set(cx + 1, 0, cz + 1, "CRIMSON_SLAB");
        canvas.set(cx - 1, 0, cz + 2, c.primaryRed());
        canvas.set(cx, 0, cz + 2, c.white());
        canvas.set(cx + 1, 0, cz + 2, c.primaryRed());

        // Soul lanterns hung above portal approach
        canvas.set(cx - 1, topY, cz + 1, "CHAIN");
        canvas.set(cx - 1, topY - 1, cz + 1, "SOUL_LANTERN");
        canvas.set(cx + 1, topY, cz + 1, "CHAIN");
        canvas.set(cx + 1, topY - 1, cz + 1, "SOUL_LANTERN");

        // Destination sign
        String dest = label(cfg, "portal-north", label(cfg, "portal-center", "Overworld"));
        canvas.sign(cx, 1, cz + 3, 0, "NC State Portal", dest);
    }

    private static void buildCompactPortalWest(Canvas canvas, int r, SceneConfig.Colors c, SceneConfig cfg) {
        int cx = -r; // west wall x
        int cz = 0;  // centered on z=0
        int bottomY = 1;
        int topY = 5;

        // Carve wall opening (z=-1,0,1, y=1..5)
        for (int z = -1; z <= 1; z++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(cx, y, z, "AIR");
            }
        }

        // Obsidian frame
        for (int z = -1; z <= 1; z++) canvas.set(cx, bottomY - 1, z, "OBSIDIAN");
        for (int y = bottomY; y <= topY; y++) {
            canvas.set(cx, y, -2, "OBSIDIAN");
            canvas.set(cx, y, 2, "OBSIDIAN");
        }
        for (int z = -1; z <= 1; z++) canvas.set(cx, topY + 1, z, "OBSIDIAN");
        canvas.set(cx, bottomY - 1, -2, "CRYING_OBSIDIAN");
        canvas.set(cx, bottomY - 1, 2, "CRYING_OBSIDIAN");
        canvas.set(cx, topY + 1, -2, "CRYING_OBSIDIAN");
        canvas.set(cx, topY + 1, 2, "CRYING_OBSIDIAN");

        // Nether portal blocks (axis Z for west-facing portal)
        for (int z = -1; z <= 1; z++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(cx, y, z, "NETHER_PORTAL");
            }
        }

        // Flanking pillars (z=-3 and z=3)
        canvas.fillBox(cx, 1, -3, cx, topY + 2, -3, c.concreteBlack());
        canvas.set(cx, 2, -3, "CRYING_OBSIDIAN");
        canvas.set(cx, 4, -3, "SOUL_LANTERN");
        canvas.set(cx, topY + 1, -3, c.primaryRed());
        canvas.set(cx, topY + 2, -3, "SEA_LANTERN");

        canvas.fillBox(cx, 1, 3, cx, topY + 2, 3, c.concreteBlack());
        canvas.set(cx, 2, 3, "CRYING_OBSIDIAN");
        canvas.set(cx, 4, 3, "SOUL_LANTERN");
        canvas.set(cx, topY + 1, 3, c.primaryRed());
        canvas.set(cx, topY + 2, 3, "SEA_LANTERN");

        // Gothic pediment above
        for (int z = -2; z <= 2; z++) canvas.set(cx, topY + 1, z, c.concreteBlack());
        for (int z = -1; z <= 1; z++) canvas.set(cx, topY + 2, z, c.primaryRed());
        canvas.set(cx, topY + 3, cz, c.white());
        canvas.set(cx, topY + 4, cz, "LIGHTNING_ROD");

        // Approach floor decorations
        canvas.set(cx + 1, 0, cz, "SEA_LANTERN");
        canvas.set(cx + 1, 0, cz - 1, "CRIMSON_SLAB");
        canvas.set(cx + 1, 0, cz + 1, "CRIMSON_SLAB");
        canvas.set(cx + 2, 0, cz - 1, c.primaryRed());
        canvas.set(cx + 2, 0, cz, c.white());
        canvas.set(cx + 2, 0, cz + 1, c.primaryRed());

        // Soul lantern chains
        canvas.set(cx + 1, topY, -1, "CHAIN");
        canvas.set(cx + 1, topY - 1, -1, "SOUL_LANTERN");
        canvas.set(cx + 1, topY, 1, "CHAIN");
        canvas.set(cx + 1, topY - 1, 1, "SOUL_LANTERN");

        // Destination sign
        String dest = label(cfg, "portal-west", label(cfg, "portal-left", "Overworld"));
        canvas.sign(cx + 3, 1, cz, 4, "NC State Portal", dest);
    }

    private static void buildCompactPortalEast(Canvas canvas, int r, SceneConfig.Colors c, SceneConfig cfg) {
        int cx = r;  // east wall x
        int cz = 0;
        int bottomY = 1;
        int topY = 5;

        // Carve wall opening
        for (int z = -1; z <= 1; z++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(cx, y, z, "AIR");
            }
        }

        // Obsidian frame
        for (int z = -1; z <= 1; z++) canvas.set(cx, bottomY - 1, z, "OBSIDIAN");
        for (int y = bottomY; y <= topY; y++) {
            canvas.set(cx, y, -2, "OBSIDIAN");
            canvas.set(cx, y, 2, "OBSIDIAN");
        }
        for (int z = -1; z <= 1; z++) canvas.set(cx, topY + 1, z, "OBSIDIAN");
        canvas.set(cx, bottomY - 1, -2, "CRYING_OBSIDIAN");
        canvas.set(cx, bottomY - 1, 2, "CRYING_OBSIDIAN");
        canvas.set(cx, topY + 1, -2, "CRYING_OBSIDIAN");
        canvas.set(cx, topY + 1, 2, "CRYING_OBSIDIAN");

        // Nether portal blocks
        for (int z = -1; z <= 1; z++) {
            for (int y = bottomY; y <= topY; y++) {
                canvas.set(cx, y, z, "NETHER_PORTAL");
            }
        }

        // Flanking pillars (z=-3 and z=3)
        canvas.fillBox(cx, 1, -3, cx, topY + 2, -3, c.concreteBlack());
        canvas.set(cx, 2, -3, "CRYING_OBSIDIAN");
        canvas.set(cx, 4, -3, "SOUL_LANTERN");
        canvas.set(cx, topY + 1, -3, c.primaryRed());
        canvas.set(cx, topY + 2, -3, "SEA_LANTERN");

        canvas.fillBox(cx, 1, 3, cx, topY + 2, 3, c.concreteBlack());
        canvas.set(cx, 2, 3, "CRYING_OBSIDIAN");
        canvas.set(cx, 4, 3, "SOUL_LANTERN");
        canvas.set(cx, topY + 1, 3, c.primaryRed());
        canvas.set(cx, topY + 2, 3, "SEA_LANTERN");

        // Gothic pediment above
        for (int z = -2; z <= 2; z++) canvas.set(cx, topY + 1, z, c.concreteBlack());
        for (int z = -1; z <= 1; z++) canvas.set(cx, topY + 2, z, c.primaryRed());
        canvas.set(cx, topY + 3, cz, c.white());
        canvas.set(cx, topY + 4, cz, "LIGHTNING_ROD");

        // Approach floor decorations
        canvas.set(cx - 1, 0, cz, "SEA_LANTERN");
        canvas.set(cx - 1, 0, cz - 1, "CRIMSON_SLAB");
        canvas.set(cx - 1, 0, cz + 1, "CRIMSON_SLAB");
        canvas.set(cx - 2, 0, cz - 1, c.primaryRed());
        canvas.set(cx - 2, 0, cz, c.white());
        canvas.set(cx - 2, 0, cz + 1, c.primaryRed());

        // Soul lantern chains
        canvas.set(cx - 1, topY, -1, "CHAIN");
        canvas.set(cx - 1, topY - 1, -1, "SOUL_LANTERN");
        canvas.set(cx - 1, topY, 1, "CHAIN");
        canvas.set(cx - 1, topY - 1, 1, "SOUL_LANTERN");

        // Destination sign
        String dest = label(cfg, "portal-east", label(cfg, "portal-right", "Minigames"));
        canvas.sign(cx - 3, 1, cz, 12, "NC State Portal", dest);
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

