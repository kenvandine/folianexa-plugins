package io.github.phqen1x.worldeditcraft.dsl;

import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import io.github.phqen1x.worldeditcraft.voxel.VoxelPalette;

import java.util.List;
import java.util.Map;

/**
 * A pure, deterministic function from a validated {@link BuildScript} (or
 * more precisely, {@link BuildScriptValidator}'s clamped size and
 * executable op list) to a {@link VoxelGrid} plus the {@link Marker}s its
 * {@code marker} ops recorded. No scheduler, no I/O, no Bukkit — this is
 * a function call, matching the design doc's Folia-safety split ("Steps
 * 1–8 never touch a game thread. Step 9 never touches anything else.").
 *
 * <p>Every stochastic op derives its randomness from {@code script.seed ^
 * opIndex ^ op.seed_offset} (see {@link ExecutionContext#randomFor}), so
 * the same script always produces the same grid — same JVM, different
 * JVM, next year.
 */
public final class BuildScriptInterpreter {

    public record Result(VoxelGrid grid, List<Marker> markers) {
    }

    private BuildScriptInterpreter() {
    }

    public static Result interpret(BuildScriptValidator.ValidationResult validated, Map<String, String> palette, long seed) {
        int[] size = validated.effectiveSize();
        VoxelGrid grid = new VoxelGrid(size[0], size[1], size[2], new VoxelPalette());
        ExecutionContext ctx = new ExecutionContext(grid, palette, seed);

        for (BuildOp op : validated.executableOps()) {
            int repeatCount = op.repeatCount();
            int[] step = op.repeatStep();
            for (int i = 0; i < repeatCount; i++) {
                BuildOp instance = i == 0 ? op : op.translated(step[0] * i, step[1] * i, step[2] * i);
                OpRegistry.get(instance.op()).executor().execute(instance, ctx);
            }
        }

        return new Result(grid, ctx.markers());
    }
}
