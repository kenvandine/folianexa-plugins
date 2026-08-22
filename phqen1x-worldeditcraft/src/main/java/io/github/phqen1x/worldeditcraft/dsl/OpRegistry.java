package io.github.phqen1x.worldeditcraft.dsl;

import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * The single source of truth for the build-script op vocabulary — name,
 * required fields (for {@link BuildScriptValidator}), a short prompt
 * description (for a future {@code PromptBuilder}, generated from this
 * registry rather than hand-written per the design doc), and the
 * executor that rasterizes it into a {@link VoxelGrid}.
 *
 * <p>This first implementation covers sixteen ops spanning every
 * category in docs/phqen1x-rpg-suite/03-buildscript-dsl.md — solids
 * ({@code fill}/{@code box}/{@code hollow_box}/{@code sphere}/{@code
 * cylinder}/{@code line}), the two trivial architecture ops ({@code
 * floor}/{@code ceiling}/{@code column}), texture/decay ({@code
 * replace}/{@code noise_replace}/{@code scatter}/{@code carve}), and
 * detail/integration ({@code place_block}/{@code marker}/{@code
 * block_entity}). The remaining normative ops (arches, roofs, stairs,
 * ramps, window grids, doors, gradients, mirror/array/rotate_group,
 * ellipsoid/cone/pyramid/prism) are deliberately not yet implemented —
 * the design doc itself expects this vocabulary "to gain three or four
 * ops and lose one or two after the first week of real use", and these
 * sixteen are enough to validate the pipeline end to end (NBT, palette,
 * determinism, repeat/chance/skip_air/replace_only) before expanding it.
 */
public final class OpRegistry {

    public interface OpExecutor {
        void execute(BuildOp op, ExecutionContext ctx);
    }

    public record OpSpec(String name, List<String> requiredFields, String promptDescription, OpExecutor executor) {
    }

    private static final Map<String, OpSpec> OPS = buildRegistry();

    private OpRegistry() {
    }

    public static boolean isKnown(String name) {
        return OPS.containsKey(name);
    }

    public static OpSpec get(String name) {
        return OPS.get(name);
    }

    public static List<String> knownNames() {
        return List.copyOf(OPS.keySet());
    }

    public static List<OpSpec> all() {
        return List.copyOf(OPS.values());
    }

    private static Map<String, OpSpec> buildRegistry() {
        Map<String, OpSpec> ops = new LinkedHashMap<>();

        ops.put("fill", new OpSpec("fill", List.of("block"),
                "Fill the entire schematic with `block`. Usually the first op.",
                OpRegistry::executeFill));

        ops.put("box", new OpSpec("box", List.of("from", "to", "block"),
                "Solid axis-aligned box, `from`/`to` inclusive.",
                OpRegistry::executeBox));

        ops.put("hollow_box", new OpSpec("hollow_box", List.of("from", "to", "block"),
                "Shell only, `thickness` (default 1).",
                OpRegistry::executeHollowBox));

        ops.put("sphere", new OpSpec("sphere", List.of("at", "radius", "block"),
                "Filled or shelled (`hollow: true`) sphere centered at `at`.",
                OpRegistry::executeSphere));

        ops.put("cylinder", new OpSpec("cylinder", List.of("at", "radius", "height", "block"),
                "Filled or shelled cylinder along `axis` (x/y/z, default y).",
                OpRegistry::executeCylinder));

        ops.put("line", new OpSpec("line", List.of("from", "to", "block"),
                "3-D line from `from` to `to`, `thickness` (default 1).",
                OpRegistry::executeLine));

        ops.put("floor", new OpSpec("floor", List.of("region", "block"),
                "Fill the region's lowest layer.",
                (op, ctx) -> executeLayer(op, ctx, true)));

        ops.put("ceiling", new OpSpec("ceiling", List.of("region", "block"),
                "Fill the region's highest layer.",
                (op, ctx) -> executeLayer(op, ctx, false)));

        ops.put("column", new OpSpec("column", List.of("at", "height", "block"),
                "Vertical column, `radius` (default 0, a single line).",
                OpRegistry::executeColumn));

        ops.put("replace", new OpSpec("replace", List.of("region", "find", "block"),
                "Straight substitution of `find` with `block` within `region`.",
                OpRegistry::executeReplace));

        ops.put("noise_replace", new OpSpec("noise_replace", List.of("region", "find", "block", "chance"),
                "Substitute a `chance` fraction of matching blocks — mossy/weathered variants.",
                OpRegistry::executeReplace));

        ops.put("scatter", new OpSpec("scatter", List.of("region", "block", "density"),
                "Sparse random placement at `density` probability per voxel.",
                OpRegistry::executeScatter));

        ops.put("carve", new OpSpec("carve", List.of("region", "chance"),
                "Remove blocks (set to air) at `chance` probability per voxel.",
                OpRegistry::executeCarve));

        ops.put("place_block", new OpSpec("place_block", List.of("at", "block"),
                "One block, for deliberate detail.",
                OpRegistry::executePlaceBlock));

        ops.put("marker", new OpSpec("marker", List.of("at", "id"),
                "Not a block — records a named anchor point for callers.",
                OpRegistry::executeMarker));

        ops.put("block_entity", new OpSpec("block_entity", List.of("at", "id"),
                "A chest, sign, spawner, banner or similar, with its NBT payload in `data`.",
                OpRegistry::executeBlockEntity));

        return Map.copyOf(ops);
    }

    // --- Solids ---

    private static void executeFill(BuildOp op, ExecutionContext ctx) {
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        VoxelGrid grid = ctx.grid();
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());
        for (int y = 0; y < grid.height(); y++) {
            for (int z = 0; z < grid.length(); z++) {
                for (int x = 0; x < grid.width(); x++) {
                    ctx.tryWrite(op, x, y, z, block, rng);
                }
            }
        }
    }

    private static void executeBox(BuildOp op, ExecutionContext ctx) {
        int[] from = op.fieldIntTriple("from");
        int[] to = op.fieldIntTriple("to");
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());
        forEachInBox(from, to, (x, y, z) -> ctx.tryWrite(op, x, y, z, block, rng));
    }

    private static void executeHollowBox(BuildOp op, ExecutionContext ctx) {
        int[] from = op.fieldIntTriple("from");
        int[] to = op.fieldIntTriple("to");
        int thickness = Math.max(1, op.fieldInt("thickness", 1));
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());

        int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
        int minY = Math.min(from[1], to[1]), maxY = Math.max(from[1], to[1]);
        int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);

        forEachInBox(from, to, (x, y, z) -> {
            boolean onShell = x < minX + thickness || x > maxX - thickness
                    || y < minY + thickness || y > maxY - thickness
                    || z < minZ + thickness || z > maxZ - thickness;
            if (onShell) {
                ctx.tryWrite(op, x, y, z, block, rng);
            }
        });
    }

    private static void executeSphere(BuildOp op, ExecutionContext ctx) {
        int[] at = op.fieldIntTriple("at");
        double radius = op.fieldDouble("radius", 0);
        boolean hollow = op.fieldBoolean("hollow", false);
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());

        int r = (int) Math.ceil(radius);
        double innerRadius = radius - 1.0;
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -r; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                    boolean inside = dist <= radius && (!hollow || dist >= innerRadius);
                    if (inside) {
                        ctx.tryWrite(op, at[0] + dx, at[1] + dy, at[2] + dz, block, rng);
                    }
                }
            }
        }
    }

    private static void executeCylinder(BuildOp op, ExecutionContext ctx) {
        int[] at = op.fieldIntTriple("at");
        double radius = op.fieldDouble("radius", 0);
        int height = op.fieldInt("height", 1);
        String axis = op.fieldString("axis", "y");
        boolean hollow = op.fieldBoolean("hollow", false);
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());

        int r = (int) Math.ceil(radius);
        double innerRadius = radius - 1.0;
        for (int along = 0; along < height; along++) {
            for (int a = -r; a <= r; a++) {
                for (int b = -r; b <= r; b++) {
                    double dist = Math.sqrt(a * a + b * b);
                    boolean inside = dist <= radius && (!hollow || dist >= innerRadius);
                    if (!inside) {
                        continue;
                    }
                    int[] pos = alongAxis(at, axis, along, a, b);
                    ctx.tryWrite(op, pos[0], pos[1], pos[2], block, rng);
                }
            }
        }
    }

    private static int[] alongAxis(int[] at, String axis, int along, int a, int b) {
        return switch (axis) {
            case "x" -> new int[]{at[0] + along, at[1] + a, at[2] + b};
            case "z" -> new int[]{at[0] + a, at[1] + b, at[2] + along};
            default -> new int[]{at[0] + a, at[1] + along, at[2] + b}; // "y"
        };
    }

    private static void executeLine(BuildOp op, ExecutionContext ctx) {
        int[] from = op.fieldIntTriple("from");
        int[] to = op.fieldIntTriple("to");
        int thickness = Math.max(1, op.fieldInt("thickness", 1));
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());
        int pad = (thickness - 1) / 2;

        for (int[] point : bresenham3D(from, to)) {
            for (int dx = -pad; dx <= pad; dx++) {
                for (int dy = -pad; dy <= pad; dy++) {
                    for (int dz = -pad; dz <= pad; dz++) {
                        ctx.tryWrite(op, point[0] + dx, point[1] + dy, point[2] + dz, block, rng);
                    }
                }
            }
        }
    }

    // --- Architecture (trivial subset) ---

    private static void executeLayer(BuildOp op, ExecutionContext ctx, boolean lowest) {
        int[][] region = op.fieldRegion("region");
        int[] from = region[0];
        int[] to = region[1];
        int y = lowest ? Math.min(from[1], to[1]) : Math.max(from[1], to[1]);
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());

        int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
        int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                ctx.tryWrite(op, x, y, z, block, rng);
            }
        }
    }

    private static void executeColumn(BuildOp op, ExecutionContext ctx) {
        int[] at = op.fieldIntTriple("at");
        int height = op.fieldInt("height", 1);
        double radius = op.fieldDouble("radius", 0);
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());

        if (radius <= 0) {
            for (int dy = 0; dy < height; dy++) {
                ctx.tryWrite(op, at[0], at[1] + dy, at[2], block, rng);
            }
            return;
        }
        int r = (int) Math.ceil(radius);
        for (int dy = 0; dy < height; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.sqrt(dx * dx + dz * dz) <= radius) {
                        ctx.tryWrite(op, at[0] + dx, at[1] + dy, at[2] + dz, block, rng);
                    }
                }
            }
        }
    }

    // --- Texture and decay ---

    private static void executeReplace(BuildOp op, ExecutionContext ctx) {
        int[][] region = op.fieldRegion("region");
        BlockStateRef find = ctx.resolveBlock(op.fieldString("find"));
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());
        VoxelGrid grid = ctx.grid();

        forEachInBox(region[0], region[1], (x, y, z) -> {
            if (grid.inBounds(x, y, z) && grid.get(x, y, z).equals(find)) {
                ctx.tryWrite(op, x, y, z, block, rng);
            }
        });
    }

    private static void executeScatter(BuildOp op, ExecutionContext ctx) {
        int[][] region = op.fieldRegion("region");
        double density = op.fieldDouble("density", 0);
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());

        forEachInBox(region[0], region[1], (x, y, z) -> {
            if (rng.nextDouble() < density) {
                ctx.tryWrite(op, x, y, z, block, rng);
            }
        });
    }

    private static void executeCarve(BuildOp op, ExecutionContext ctx) {
        int[][] region = op.fieldRegion("region");
        BlockStateRef air = ctx.resolveBlock("minecraft:air");
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());
        forEachInBox(region[0], region[1], (x, y, z) -> ctx.tryWrite(op, x, y, z, air, rng));
    }

    // --- Detail and integration ---

    private static void executePlaceBlock(BuildOp op, ExecutionContext ctx) {
        int[] at = op.fieldIntTriple("at");
        BlockStateRef block = ctx.resolveBlock(op.fieldString("block"));
        Random rng = ctx.randomFor(op.sourceIndex(), op.seedOffset());
        ctx.tryWrite(op, at[0], at[1], at[2], block, rng);
    }

    private static void executeMarker(BuildOp op, ExecutionContext ctx) {
        int[] at = op.fieldIntTriple("at");
        String id = op.fieldString("id");
        Object meta = op.fields().get("meta");
        @SuppressWarnings("unchecked")
        Map<String, Object> metaMap = meta instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        ctx.addMarker(id, at, metaMap);
    }

    private static void executeBlockEntity(BuildOp op, ExecutionContext ctx) {
        int[] at = op.fieldIntTriple("at");
        String id = op.fieldString("id");
        Object data = op.fields().get("data");
        @SuppressWarnings("unchecked")
        Map<String, Object> dataMap = data instanceof Map<?, ?> map ? (Map<String, Object>) map : Map.of();
        if (ctx.grid().inBounds(at[0], at[1], at[2])) {
            ctx.grid().setBlockEntity(at[0], at[1], at[2], id, dataMap);
        }
    }

    // --- Shared geometry helpers ---

    private interface VoxelAction {
        void accept(int x, int y, int z);
    }

    private static void forEachInBox(int[] from, int[] to, VoxelAction action) {
        int minX = Math.min(from[0], to[0]), maxX = Math.max(from[0], to[0]);
        int minY = Math.min(from[1], to[1]), maxY = Math.max(from[1], to[1]);
        int minZ = Math.min(from[2], to[2]), maxZ = Math.max(from[2], to[2]);
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                for (int z = minZ; z <= maxZ; z++) {
                    action.accept(x, y, z);
                }
            }
        }
    }

    /** Standard 3-D Bresenham, driven by whichever axis has the greatest delta. */
    private static List<int[]> bresenham3D(int[] from, int[] to) {
        List<int[]> points = new java.util.ArrayList<>();
        int x0 = from[0], y0 = from[1], z0 = from[2];
        int x1 = to[0], y1 = to[1], z1 = to[2];

        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int dz = Math.abs(z1 - z0), sz = z0 < z1 ? 1 : -1;
        int dm = Math.max(dx, Math.max(dy, dz));
        int x = x0, y = y0, z = z0;
        int errX = dm / 2, errY = dm / 2, errZ = dm / 2;

        for (int i = 0; i <= dm; i++) {
            points.add(new int[]{x, y, z});
            errX -= dx;
            errY -= dy;
            errZ -= dz;
            if (errX < 0) {
                errX += dm;
                x += sx;
            }
            if (errY < 0) {
                errY += dm;
                y += sy;
            }
            if (errZ < 0) {
                errZ += dm;
                z += sz;
            }
        }
        return points;
    }
}
