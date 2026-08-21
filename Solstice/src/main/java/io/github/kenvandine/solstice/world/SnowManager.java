package io.github.kenvandine.solstice.world;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.config.MainConfig;
import io.github.kenvandine.solstice.platform.ClaimGuard;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.Snow;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Winter water freeze/thaw and snow accumulation. One region-scheduled repeating task per loaded
 * chunk (PLAN.md §2 — block edits must run on the region owning the chunk), budgeted by
 * {@code world-effects.snow-ice.blocks-per-tick-per-region} so a big managed world doesn't spike a
 * region tick.
 */
public final class SnowManager implements Listener {

    private record ChunkKey(UUID world, int x, int z) {
    }

    private record BlockPos(UUID world, int x, int y, int z) {
        Block block(Solstice plugin) {
            World w = plugin.getServer().getWorld(world);
            return w != null ? w.getBlockAt(x, y, z) : null;
        }
    }

    private static final long PERIOD_TICKS = 100L; // 5s

    private final Solstice plugin;
    private final ConcurrentHashMap<ChunkKey, ScheduledTask> chunkTasks = new ConcurrentHashMap<>();
    private final Set<BlockPos> frozenByUs = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> snowedByUs = ConcurrentHashMap.newKeySet();

    public SnowManager(Solstice plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                scheduleChunk(chunk);
            }
        }
    }

    public void stop() {
        for (ScheduledTask task : chunkTasks.values()) {
            task.cancel();
        }
        chunkTasks.clear();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        scheduleChunk(event.getChunk());
    }

    @EventHandler
    public void onChunkUnload(ChunkUnloadEvent event) {
        ChunkKey key = new ChunkKey(event.getWorld().getUID(), event.getChunk().getX(), event.getChunk().getZ());
        ScheduledTask task = chunkTasks.remove(key);
        if (task != null) {
            task.cancel();
        }
    }

    private void scheduleChunk(Chunk chunk) {
        if (!plugin.config().main().snowIceEnabled()) {
            return;
        }
        if (!plugin.calendarEngine().isManaged(chunk.getWorld())) {
            return;
        }
        ChunkKey key = new ChunkKey(chunk.getWorld().getUID(), chunk.getX(), chunk.getZ());
        if (chunkTasks.containsKey(key)) {
            return;
        }
        Location anchor = chunk.getBlock(8, Math.max(chunk.getWorld().getMinHeight(), 64), 8).getLocation();
        ScheduledTask task = plugin.schedulers().regionRepeating(anchor,
                () -> processChunk(chunk.getWorld(), chunk.getX(), chunk.getZ()), PERIOD_TICKS, PERIOD_TICKS);
        chunkTasks.put(key, task);
    }

    private void processChunk(World world, int chunkX, int chunkZ) {
        WorldSeasonState state = plugin.calendarEngine().stateOf(world);
        if (state == null) {
            return;
        }
        ClaimGuard guard = plugin.claimGuard();
        int budget = plugin.config().main().snowIceBlocksPerTickPerRegion();

        if (state.season() == Season.WINTER) {
            budget = freezeAndSnow(world, chunkX, chunkZ, budget, guard, world.hasStorm());
        } else {
            revertChunk(world, chunkX, chunkZ, budget, guard);
        }
    }

    private int freezeAndSnow(World world, int chunkX, int chunkZ, int budget, ClaimGuard guard, boolean snowing) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        for (int dx = 0; dx < 16 && budget > 0; dx++) {
            for (int dz = 0; dz < 16 && budget > 0; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                Block top = world.getHighestBlockAt(x, z);

                if (top.getType() == Material.WATER && isSourceBlock(top) && isEdgeOrNearIce(top)) {
                    Location loc = top.getLocation();
                    if (guard.canModify(loc)) {
                        BlockPos pos = new BlockPos(world.getUID(), x, top.getY(), z);
                        top.setType(Material.ICE, false);
                        frozenByUs.add(pos);
                        budget--;
                    }
                } else if (snowing && budget > 0 && top.getType().isSolid()) {
                    Block above = top.getRelative(0, 1, 0);
                    if (above.getType() == Material.AIR) {
                        Location loc = above.getLocation();
                        if (guard.canModify(loc)) {
                            above.setType(Material.SNOW, false);
                            snowedByUs.add(new BlockPos(world.getUID(), x, above.getY(), z));
                            budget--;
                        }
                    }
                }
            }
        }
        return budget;
    }

    private boolean isSourceBlock(Block block) {
        return block.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0;
    }

    private boolean isEdgeOrNearIce(Block water) {
        for (int[] d : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            Material neighbor = water.getRelative(d[0], 0, d[1]).getType();
            if (neighbor == Material.ICE || (neighbor != Material.WATER && neighbor.isSolid())) {
                return true;
            }
        }
        return false;
    }

    private void revertChunk(World world, int chunkX, int chunkZ, int budget, ClaimGuard guard) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        var frozenIt = frozenByUs.iterator();
        while (frozenIt.hasNext() && budget > 0) {
            BlockPos pos = frozenIt.next();
            if (!pos.world().equals(world.getUID()) || (pos.x() >> 4) != chunkX || (pos.z() >> 4) != chunkZ) {
                continue;
            }
            Block block = pos.block(plugin);
            if (block == null || block.getType() != Material.ICE) {
                frozenIt.remove(); // no longer ours to track (changed by something else)
            } else if (guard.canModify(block.getLocation())) {
                block.setType(Material.WATER, false);
                budget--;
                frozenIt.remove();
            }
        }
        var snowIt = snowedByUs.iterator();
        while (snowIt.hasNext() && budget > 0) {
            BlockPos pos = snowIt.next();
            if (!pos.world().equals(world.getUID()) || (pos.x() >> 4) != chunkX || (pos.z() >> 4) != chunkZ) {
                continue;
            }
            Block block = pos.block(plugin);
            if (block == null || block.getType() != Material.SNOW) {
                snowIt.remove();
            } else if (guard.canModify(block.getLocation())) {
                block.setType(Material.AIR, false);
                budget--;
                snowIt.remove();
            }
        }
    }

    /** Immediately reverts every tracked frozen/snowed block in a world, ignoring season and budget ({@code /solstice restoreworld}). */
    public void forceRevertAll(World world) {
        ClaimGuard guard = plugin.claimGuard();
        var frozenIt = frozenByUs.iterator();
        while (frozenIt.hasNext()) {
            BlockPos pos = frozenIt.next();
            if (!pos.world().equals(world.getUID())) continue;
            Block block = pos.block(plugin);
            if (block != null && block.getType() == Material.ICE && guard.canModify(block.getLocation())) {
                block.setType(Material.WATER, false);
            }
            frozenIt.remove();
        }
        var snowIt = snowedByUs.iterator();
        while (snowIt.hasNext()) {
            BlockPos pos = snowIt.next();
            if (!pos.world().equals(world.getUID())) continue;
            Block block = pos.block(plugin);
            if (block != null && block.getType() == Material.SNOW && guard.canModify(block.getLocation())) {
                block.setType(Material.AIR, false);
            }
            snowIt.remove();
        }
    }
}
