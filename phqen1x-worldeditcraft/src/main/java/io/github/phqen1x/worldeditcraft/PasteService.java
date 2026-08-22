package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.dsl.Marker;
import io.github.phqen1x.worldeditcraft.library.MarkerRecord;
import io.github.phqen1x.worldeditcraft.library.SchematicLibrary;
import io.github.phqen1x.worldeditcraft.paste.PastePlan;
import io.github.phqen1x.worldeditcraft.paste.PasteEngine;
import io.github.phqen1x.worldeditcraft.paste.PasteJob;
import io.github.phqen1x.worldeditcraft.paste.Placement;
import io.github.phqen1x.worldeditcraft.paste.UndoJournal;
import io.github.phqen1x.worldeditcraft.schem.SchematicReader;
import io.github.phqen1x.worldeditcraft.voxel.BlockStateRef;
import io.github.phqen1x.worldeditcraft.voxel.Bounds;
import io.github.phqen1x.worldeditcraft.voxel.Transform;
import io.github.phqen1x.worldeditcraft.voxel.VoxelGrid;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * Orchestrates {@code /wec paste}/{@code undo}/{@code cancel}: loads a
 * schematic from the {@link SchematicLibrary}, builds a {@link
 * PastePlan}, hands it to {@link PasteEngine}, and tracks the bookkeeping
 * {@code PasteEngine} itself doesn't own — which job belongs to which
 * player (for cancel) and each world's recent undo journals (for undo).
 * The load/parse/plan steps here are plain I/O and pure computation; the
 * actual placement inside {@link PasteEngine#execute} is the only part
 * of this whole path that touches the world, and does so through the
 * region scheduler exclusively — see that class's docs for the
 * unverified-against-a-live-server caveat this inherits.
 */
final class PasteService {

    private final WorldEditCraftPlugin plugin;
    private final Path undoDirectory;
    private final Map<UUID, PasteJob> activeJobsByPlayer = new ConcurrentHashMap<>();
    private final Map<String, Deque<Path>> undoHistoryByWorld = new ConcurrentHashMap<>();

    /**
     * Created once, in {@code onEnable}, and never rebuilt on {@code
     * /wec reload}/{@code set} — unlike the config-derived services,
     * this one owns in-memory state (in-flight jobs, undo history) that
     * a reload must not silently discard. It reads {@link
     * WorldEditCraftPlugin#library()} and {@link WorldEditCraftPlugin#config()}
     * fresh on every call instead of capturing either at construction
     * time, so it still sees the current config/library after a reload.
     */
    PasteService(WorldEditCraftPlugin plugin, Path undoDirectory) {
        this.plugin = plugin;
        this.undoDirectory = undoDirectory;
    }

    record PasteRequest(String schematicName, Player player, int rotationDegrees, Transform.Flip flip, boolean skipAir) {
    }

    /** Called from the async scheduler; the callback fires later, once placement actually finishes on the region scheduler. */
    void paste(PasteRequest request, Consumer<String> messageCallback) {
        WorldEditCraftConfig.Paste pasteConfig = plugin.config().paste();
        if (activeJobsByPlayer.size() >= Math.max(1, pasteConfig.maxConcurrentJobs())) {
            messageCallback.accept("Too many pastes already in progress (max " + pasteConfig.maxConcurrentJobs() + "). Try again shortly.");
            return;
        }

        Optional<byte[]> bytes = plugin.library().load(request.schematicName());
        if (bytes.isEmpty()) {
            messageCallback.accept("No such schematic: '" + request.schematicName() + "'.");
            return;
        }

        SchematicReader.Result schematic;
        try {
            schematic = SchematicReader.read(bytes.get());
        } catch (RuntimeException e) {
            messageCallback.accept("Could not read '" + request.schematicName() + "': " + e.getMessage());
            return;
        }

        List<MarkerRecord> markerRecords = plugin.library().loadMarkers(request.schematicName());
        List<Marker> markers = markerRecords.stream()
                .map(m -> new Marker(m.id(), new int[]{m.x(), m.y(), m.z()}, m.meta()))
                .toList();

        Transform transform = new Transform(request.rotationDegrees(), request.flip());
        Location origin = request.player().getLocation();
        int originX = origin.getBlockX();
        int originY = origin.getBlockY();
        int originZ = origin.getBlockZ();
        World world = request.player().getWorld();

        VoxelGrid grid = schematic.grid();
        PastePlan.Result plan = PastePlan.build(grid, markers, transform, originX, originY, originZ,
                pasteConfig.blocksPerTick(), request.skipAir());

        if (plan.totalPlacements() == 0) {
            messageCallback.accept("'" + request.schematicName() + "' has nothing to place (empty after --no-air).");
            return;
        }

        Bounds clearBounds = null;
        if (pasteConfig.clearVolumeFirst()) {
            int outputWidth = transform.outputWidth(grid.width(), grid.length());
            int outputLength = transform.outputLength(grid.width(), grid.length());
            clearBounds = Bounds.of(originX, originY, originZ,
                    originX + outputWidth - 1, originY + grid.height() - 1, originZ + outputLength - 1);
        }

        PasteJob job = new PasteJob(plan.totalPlacements());
        UUID playerId = request.player().getUniqueId();
        activeJobsByPlayer.put(playerId, job);

        String markerSummary = plan.markers().isEmpty() ? "" : " " + plan.markers().size() + " marker(s) resolved.";
        String startMessage = "Pasting '" + request.schematicName() + "' — " + plan.totalPlacements() + " block(s) across "
                + plan.chunks().size() + " chunk(s)." + markerSummary;
        messageCallback.accept(startMessage);
        plugin.getLogger().info(() -> startMessage + " (requested by " + request.player().getName() + ")");

        PasteEngine.execute(plugin, world, plan, job, clearBounds, pasteConfig.unknownBlock(), (finishedJob, undoEntries, unknownCount) -> {
            activeJobsByPlayer.remove(playerId, finishedJob);
            plugin.runAsync(() -> {
                if (!finishedJob.isCancelled() && !undoEntries.isEmpty()) {
                    recordUndoJournal(world.getName(), finishedJob, undoEntries, pasteConfig.undoHistory());
                }
                String outcome = finishedJob.isCancelled()
                        ? "Paste of '" + request.schematicName() + "' cancelled after " + finishedJob.progress().placed() + " block(s)."
                        : "Finished pasting '" + request.schematicName() + "' — " + finishedJob.progress().placed() + " block(s) placed.";
                if (unknownCount > 0) {
                    outcome += " " + unknownCount + " block(s) used an unrecognized state and were substituted per paste.unknown-block.";
                }
                plugin.getLogger().info(outcome);
                messageCallback.accept(outcome);
            });
        });
    }

    /** {@code false} if the player has no in-flight paste to cancel. */
    boolean cancel(Player player) {
        PasteJob job = activeJobsByPlayer.get(player.getUniqueId());
        if (job == null) {
            return false;
        }
        job.cancel();
        return true;
    }

    /** {@code false} if this world has no undo journal left to replay. */
    boolean undo(Player player, Consumer<String> messageCallback) {
        Deque<Path> history = undoHistoryByWorld.get(player.getWorld().getName());
        if (history == null || history.isEmpty()) {
            return false;
        }
        Path journalFile = history.pop();

        plugin.runAsync(() -> {
            List<UndoJournal.Entry> entries;
            try {
                entries = UndoJournal.read(journalFile);
            } catch (RuntimeException e) {
                messageCallback.accept("Could not read the undo journal: " + e.getMessage());
                return;
            }
            List<UndoJournal.Entry> replayOrder = UndoJournal.replayOrder(entries);
            PastePlan.Result undoPlan = planFromUndoEntries(replayOrder, plugin.config().paste().blocksPerTick());
            PasteJob undoJob = new PasteJob(undoPlan.totalPlacements());
            PasteEngine.execute(plugin, player.getWorld(), undoPlan, undoJob, null, "abort",
                    (finishedJob, ignoredUndoEntries, ignoredUnknownCount) ->
                            messageCallback.accept("Undid the last paste — " + finishedJob.progress().placed() + " block(s) restored."));
        });
        return true;
    }

    private void recordUndoJournal(String worldName, PasteJob job, List<UndoJournal.Entry> entries, int maxHistory) {
        Path journalFile = undoDirectory.resolve(worldName).resolve(job.id() + ".json");
        UndoJournal.write(journalFile, entries);

        Deque<Path> history = undoHistoryByWorld.computeIfAbsent(worldName, w -> new ArrayDeque<>());
        synchronized (history) {
            history.push(journalFile);
            while (history.size() > Math.max(1, maxHistory)) {
                history.removeLast();
            }
        }
    }

    /** Turns a reversed undo-entry list back into a one-pass {@link PastePlan.Result} — undo never needs a two-pass split, it just restores exactly what was there. */
    private static PastePlan.Result planFromUndoEntries(List<UndoJournal.Entry> entries, int blocksPerTick) {
        Map<Long, List<Placement>> byChunk = new LinkedHashMap<>();
        for (UndoJournal.Entry entry : entries) {
            BlockStateRef state = BlockStateRef.parse(entry.block());
            Placement placement = new Placement(entry.x(), entry.y(), entry.z(), state, null);
            long chunkKey = PastePlan.chunkKey(entry.x() >> 4, entry.z() >> 4);
            byChunk.computeIfAbsent(chunkKey, k -> new ArrayList<>()).add(placement);
        }

        List<PastePlan.ChunkPlan> chunkPlans = new ArrayList<>();
        for (Map.Entry<Long, List<Placement>> entry : byChunk.entrySet()) {
            int chunkX = (int) (entry.getKey() >> 32);
            int chunkZ = (int) (long) entry.getKey();
            List<PastePlan.Batch> batches = new ArrayList<>();
            List<Placement> placements = entry.getValue();
            int size = Math.max(1, blocksPerTick);
            for (int i = 0; i < placements.size(); i += size) {
                batches.add(new PastePlan.Batch(List.copyOf(placements.subList(i, Math.min(i + size, placements.size())))));
            }
            chunkPlans.add(new PastePlan.ChunkPlan(chunkX, chunkZ, batches, List.of()));
        }
        return new PastePlan.Result(chunkPlans, entries.size(), List.of());
    }
}
