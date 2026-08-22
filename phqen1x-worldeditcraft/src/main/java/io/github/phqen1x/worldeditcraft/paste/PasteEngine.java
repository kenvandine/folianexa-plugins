package io.github.phqen1x.worldeditcraft.paste;

import io.github.phqen1x.worldeditcraft.voxel.Bounds;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.sign.Side;
import org.bukkit.plugin.Plugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Executes a {@link PastePlan.Result} against a real world, on Paper's
 * region scheduler. This is the only class in the plugin that places
 * blocks, and it is the least-verified code in this build — see the
 * plugin README's "What's real vs. unverified" for exactly what a live
 * Folia server needs to confirm before this is trusted.
 *
 * <p>Follows {@code campus-lobby}'s {@code SceneBuilder} precedent
 * (docs/phqen1x-rpg-suite/07-folia-safety.md's "Chunk-bucketed block
 * placement" and "FIFO ordering within a chunk"): placements are grouped
 * by the chunk they land in and each chunk's own batches are submitted
 * to {@link Bukkit#getRegionScheduler()} against that chunk's
 * coordinates, one batch per tick (batch <i>n+1</i> is submitted from
 * inside batch <i>n</i>'s own task, delayed one tick, so a region tick
 * is never held for more than {@code paste.blocks-per-tick} placements).
 *
 * <p><b>Pass ordering is a deliberate simplification of the design
 * doc.</b> The doc describes waiting for only the pass-1 batches that
 * could <em>neighbour</em> a given chunk before starting that chunk's
 * pass 2. This class instead waits for <em>every</em> chunk's pass 1 to
 * finish before starting pass 2 <em>anywhere</em> — a single global
 * fan-in counter, the same counter-based idiom
 * {@code FoliaNexaStatsPlugin#runReportCycle} uses, rather than a
 * per-chunk neighbour computation. It is strictly safer (pass 2 never
 * starts before any pass-1 placement it could conceivably depend on)
 * at the cost of being less parallel across a paste that spans many
 * chunks — a reasonable trade for a first implementation, revisit if a
 * live 96x48x96-class paste (the design doc's own M2 exit-criterion
 * size) turns out to visibly stall on this.
 */
public final class PasteEngine {

    public interface Callback {
        void onComplete(PasteJob job, List<UndoJournal.Entry> undoEntries, int unknownBlockSubstitutions);
    }

    private PasteEngine() {
    }

    /**
     * @param clearBounds world-space bounds to clear to air before placing, or {@code null} to skip clearing.
     *                    Only clears within chunks the plan actually places into — there is no separate
     *                    padding concept in this design (contrast {@code campus-lobby}'s {@code SceneConfig}).
     */
    public static void execute(Plugin plugin, World world, PastePlan.Result plan, PasteJob job,
                                Bounds clearBounds, String unknownBlockPolicy, Callback callback) {
        List<PastePlan.ChunkPlan> chunks = plan.chunks();
        if (chunks.isEmpty()) {
            callback.onComplete(job, List.of(), 0);
            return;
        }

        List<UndoJournal.Entry> undoEntries = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger unknownCount = new AtomicInteger();

        int pass1ChunkCount = (int) chunks.stream().filter(c -> !c.pass1Batches().isEmpty()).count();
        AtomicInteger pass1Remaining = new AtomicInteger(pass1ChunkCount);
        AtomicInteger chainsRemaining = new AtomicInteger(chunks.size() * 2); // one pass-1 "chain" and one pass-2 "chain" per chunk

        Runnable onAllChainsDone = () -> {
            if (chainsRemaining.decrementAndGet() == 0) {
                callback.onComplete(job, List.copyOf(undoEntries), unknownCount.get());
            }
        };

        Runnable startPass2 = () -> startPass2(plugin, world, chunks, job, undoEntries, unknownBlockPolicy, unknownCount, onAllChainsDone);

        for (PastePlan.ChunkPlan chunk : chunks) {
            if (clearBounds != null) {
                scheduleClear(plugin, world, chunk, clearBounds);
            }

            Runnable onPass1ChainDone = () -> {
                if (!chunk.pass1Batches().isEmpty() && pass1Remaining.decrementAndGet() == 0) {
                    startPass2.run();
                }
                onAllChainsDone.run();
            };

            if (chunk.pass1Batches().isEmpty()) {
                onPass1ChainDone.run();
            } else {
                runBatchChain(plugin, world, chunk.chunkX(), chunk.chunkZ(), chunk.pass1Batches(), 0,
                        job, undoEntries, unknownBlockPolicy, unknownCount, onPass1ChainDone);
            }
        }

        if (pass1ChunkCount == 0) {
            startPass2.run(); // nothing in pass 1 anywhere — pass 2 has nothing to wait for
        }
    }

    private static void startPass2(Plugin plugin, World world, List<PastePlan.ChunkPlan> chunks, PasteJob job,
                                    List<UndoJournal.Entry> undoEntries, String unknownBlockPolicy,
                                    AtomicInteger unknownCount, Runnable onAllChainsDone) {
        for (PastePlan.ChunkPlan chunk : chunks) {
            if (chunk.pass2Batches().isEmpty()) {
                onAllChainsDone.run();
            } else {
                runBatchChain(plugin, world, chunk.chunkX(), chunk.chunkZ(), chunk.pass2Batches(), 0,
                        job, undoEntries, unknownBlockPolicy, unknownCount, onAllChainsDone);
            }
        }
    }

    private static void runBatchChain(Plugin plugin, World world, int chunkX, int chunkZ, List<PastePlan.Batch> batches, int index,
                                       PasteJob job, List<UndoJournal.Entry> undoEntries, String unknownBlockPolicy,
                                       AtomicInteger unknownCount, Runnable onChainDone) {
        if (index >= batches.size() || job.isCancelled()) {
            onChainDone.run();
            return;
        }

        Runnable step = () -> {
            PastePlan.Batch batch = batches.get(index);
            for (Placement placement : batch.placements()) {
                // Undo reads happen on this region's own thread, right
                // before the overwrite — never from the async side (see
                // docs/phqen1x-rpg-suite/07-folia-safety.md).
                String previous = world.getBlockAt(placement.x(), placement.y(), placement.z()).getBlockData().getAsString();
                undoEntries.add(new UndoJournal.Entry(placement.x(), placement.y(), placement.z(), previous));
                placeOne(world, placement, unknownBlockPolicy, unknownCount);
            }
            job.addPlaced(batch.placements().size());
            runBatchChain(plugin, world, chunkX, chunkZ, batches, index + 1, job, undoEntries, unknownBlockPolicy, unknownCount, onChainDone);
        };

        if (index == 0) {
            Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ, scheduledTask -> step.run());
        } else {
            Bukkit.getRegionScheduler().runDelayed(plugin, world, chunkX, chunkZ, scheduledTask -> step.run(), 1L);
        }
    }

    private static void placeOne(World world, Placement placement, String unknownBlockPolicy, AtomicInteger unknownCount) {
        BlockData data;
        try {
            data = Bukkit.createBlockData(placement.state().toString());
        } catch (IllegalArgumentException e) {
            unknownCount.incrementAndGet();
            switch (unknownBlockPolicy.toLowerCase(Locale.ROOT)) {
                case "stone" -> data = Bukkit.createBlockData(Material.STONE);
                case "abort" -> {
                    return; // leave whatever was already there
                }
                default -> data = Bukkit.createBlockData(Material.AIR); // "air", and the fallback for an unrecognized policy string
            }
        }
        world.getBlockAt(placement.x(), placement.y(), placement.z()).setBlockData(data, false);
        applyBlockEntityBestEffort(world, placement);
    }

    /**
     * Best-effort only: sign text, since that's a common, simple, typed
     * Bukkit API ({@link Sign}). Everything else a {@code block_entity}
     * op can carry (chest contents, spawner type, banner patterns, ...)
     * is not applied — the base block from {@link #placeOne} is placed,
     * but its NBT payload beyond that is dropped. See the plugin
     * README's known limitations.
     */
    private static void applyBlockEntityBestEffort(World world, Placement placement) {
        VoxelGrid.BlockEntity blockEntity = placement.blockEntity();
        if (blockEntity == null) {
            return;
        }
        BlockState state = world.getBlockAt(placement.x(), placement.y(), placement.z()).getState();
        if (!(state instanceof Sign sign)) {
            return;
        }
        Object linesValue = blockEntity.data().get("lines");
        if (!(linesValue instanceof List<?> lines)) {
            return;
        }
        var front = sign.getSide(Side.FRONT);
        for (int i = 0; i < lines.size() && i < 4; i++) {
            front.line(i, Component.text(String.valueOf(lines.get(i))));
        }
        sign.update(true, false);
    }

    private static void scheduleClear(Plugin plugin, World world, PastePlan.ChunkPlan chunk, Bounds clearBounds) {
        int chunkMinX = chunk.chunkX() << 4;
        int chunkMinZ = chunk.chunkZ() << 4;
        int xMin = Math.max(chunkMinX, clearBounds.minX());
        int xMax = Math.min(chunkMinX + 15, clearBounds.maxX());
        int zMin = Math.max(chunkMinZ, clearBounds.minZ());
        int zMax = Math.min(chunkMinZ + 15, clearBounds.maxZ());
        if (xMin > xMax || zMin > zMax) {
            return; // this chunk doesn't actually overlap the clear volume
        }
        int yMin = Math.max(world.getMinHeight(), clearBounds.minY());
        int yMax = Math.min(world.getMaxHeight() - 1, clearBounds.maxY());

        // Submitted before this chunk's own pass-1 batch 0 (see the
        // execute() loop), so it runs first within this chunk's FIFO
        // region-task queue — the same property SceneBuilder's own
        // scheduleClear relies on.
        Bukkit.getRegionScheduler().run(plugin, world, chunk.chunkX(), chunk.chunkZ(), scheduledTask -> {
            for (int x = xMin; x <= xMax; x++) {
                for (int z = zMin; z <= zMax; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        world.getBlockAt(x, y, z).setType(Material.AIR, false);
                    }
                }
            }
        });
    }
}
