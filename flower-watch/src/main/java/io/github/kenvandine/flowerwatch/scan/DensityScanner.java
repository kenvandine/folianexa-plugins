package io.github.kenvandine.flowerwatch.scan;

import io.github.kenvandine.flowerwatch.FlowerMaterials;
import io.github.kenvandine.flowerwatch.config.FlowerWatchConfig;
import io.github.kenvandine.flowerwatch.log.DiagnosticLogger;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.ChunkSnapshot;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;

import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Periodically counts flower blocks per loaded chunk and logs a WARNING
 * when a chunk's count jumps by more than the configured threshold since
 * its last scan — catches a runaway spawn burst even between individual
 * per-block events, e.g. if thousands of blocks change between two
 * scans, faster than anyone is watching the live event log.
 *
 * <p>Every per-chunk count runs through {@link Bukkit#getRegionScheduler()}
 * so it only ever touches blocks from the thread that actually owns that
 * chunk's region — this never assumes single-threaded world access, and
 * a chunk that unloads between being listed and its scan task actually
 * running is handled by re-checking {@link World#isChunkLoaded(int, int)}
 * rather than assumed to still be there.
 *
 * <p>This is genuinely O(chunk volume) work per loaded chunk per scan —
 * not free. {@code density-scan.worlds}/{@code min-y}/{@code max-y} in
 * config.yml exist to bound that cost; use them once you've narrowed
 * things down, rather than leaving a full-cluster, full-height scan
 * running indefinitely.
 */
public final class DensityScanner {

    private final Plugin plugin;
    private final FlowerWatchConfig config;
    private final DiagnosticLogger logger;
    private final DensityTracker tracker = new DensityTracker();
    private final AtomicReference<ScheduledTask> task = new AtomicReference<>();

    public DensityScanner(Plugin plugin, FlowerWatchConfig config, DiagnosticLogger logger) {
        this.plugin = plugin;
        this.config = config;
        this.logger = logger;
    }

    public void start() {
        if (!config.densityScanEnabled() || task.get() != null) {
            return;
        }
        long ticks = config.densityScanIntervalSeconds() * 20L;
        ScheduledTask scheduled = Bukkit.getGlobalRegionScheduler()
                .runAtFixedRate(plugin, t -> scanAllWorlds(), ticks, ticks);
        task.set(scheduled);
    }

    public void stop() {
        ScheduledTask scheduled = task.getAndSet(null);
        if (scheduled != null) {
            scheduled.cancel();
        }
    }

    private void scanAllWorlds() {
        Set<String> allowlist = config.densityScanWorlds();
        for (World world : Bukkit.getWorlds()) {
            if (!allowlist.isEmpty() && !allowlist.contains(world.getName())) {
                continue;
            }
            for (Chunk chunk : world.getLoadedChunks()) {
                int chunkX = chunk.getX();
                int chunkZ = chunk.getZ();
                Bukkit.getRegionScheduler().run(plugin, world, chunkX, chunkZ,
                        t -> scanChunk(world, chunkX, chunkZ));
            }
        }
    }

    private void scanChunk(World world, int chunkX, int chunkZ) {
        if (!world.isChunkLoaded(chunkX, chunkZ)) {
            return; // unloaded between being listed and this region task actually running
        }
        ChunkSnapshot snapshot = world.getChunkAt(chunkX, chunkZ).getChunkSnapshot();
        int minY = Math.max(world.getMinHeight(), config.densityScanMinY());
        int maxY = Math.min(world.getMaxHeight(), config.densityScanMaxY());
        int count = 0;
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = minY; y < maxY; y++) {
                    if (FlowerMaterials.isFlower(snapshot.getBlockType(x, y, z))) {
                        count++;
                    }
                }
            }
        }
        ChunkKey key = new ChunkKey(world.getName(), chunkX, chunkZ);
        int finalCount = count;
        tracker.recordAndDelta(key, count).ifPresent(delta -> {
            if (delta >= config.densityScanAlertThreshold()) {
                logger.densityAlert(world.getName(), chunkX, chunkZ, finalCount, delta);
            }
        });
    }
}
