package io.github.kenvandine.solstice.world;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.platform.ClaimGuard;
import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkUnloadEvent;

import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Spring flower carpets and summer berry-bush patches (PLAN.md §3.1), generated sparsely on grass
 * blocks in loaded chunks and removed once their season ends. Same region-scheduled-per-chunk
 * approach as {@link SnowManager}.
 */
public final class FloraManager implements Listener {

    private static final Material[] SPRING_FLOWERS = {
            Material.DANDELION, Material.POPPY, Material.BLUE_ORCHID, Material.ALLIUM,
            Material.AZURE_BLUET, Material.RED_TULIP, Material.ORANGE_TULIP, Material.WHITE_TULIP,
            Material.PINK_TULIP, Material.OXEYE_DAISY, Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY
    };

    private record ChunkKey(UUID world, int x, int z) {
    }

    private record BlockPos(UUID world, int x, int y, int z) {
        Block block(Solstice plugin) {
            World w = plugin.getServer().getWorld(world);
            return w != null ? w.getBlockAt(x, y, z) : null;
        }
    }

    private static final long PERIOD_TICKS = 200L; // 10s
    private static final double SPAWN_CHANCE_PER_COLUMN = 0.02;

    private final Solstice plugin;
    private final ConcurrentHashMap<ChunkKey, ScheduledTask> chunkTasks = new ConcurrentHashMap<>();
    private final Set<BlockPos> springFlowers = ConcurrentHashMap.newKeySet();
    private final Set<BlockPos> summerBushes = ConcurrentHashMap.newKeySet();

    public FloraManager(Solstice plugin) {
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
        if (!plugin.config().main().floraEnabled() || !plugin.calendarEngine().isManaged(chunk.getWorld())) {
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

        if (state.season() == Season.SPRING) {
            generate(world, chunkX, chunkZ, guard, springFlowers, this::pickFlower);
            revert(world, chunkX, chunkZ, guard, summerBushes);
        } else if (state.season() == Season.SUMMER) {
            generate(world, chunkX, chunkZ, guard, summerBushes, block -> Material.SWEET_BERRY_BUSH);
            revert(world, chunkX, chunkZ, guard, springFlowers);
        } else {
            revert(world, chunkX, chunkZ, guard, springFlowers);
            revert(world, chunkX, chunkZ, guard, summerBushes);
        }
    }

    private Material pickFlower(Block ignored) {
        return SPRING_FLOWERS[ThreadLocalRandom.current().nextInt(SPRING_FLOWERS.length)];
    }

    private void generate(World world, int chunkX, int chunkZ, ClaimGuard guard, Set<BlockPos> tracked,
                           java.util.function.Function<Block, Material> materialPicker) {
        int baseX = chunkX << 4;
        int baseZ = chunkZ << 4;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = baseX + dx;
                int z = baseZ + dz;
                Block top = world.getHighestBlockAt(x, z);
                if (top.getType() != Material.GRASS_BLOCK) {
                    continue;
                }
                String category = plugin.config().biomes().categoryFor(top.getBiome());
                double density = plugin.config().main().floraDensityFor(category);
                if (density <= 0.0 || random.nextDouble() >= SPAWN_CHANCE_PER_COLUMN * density) {
                    continue;
                }
                Block above = top.getRelative(0, 1, 0);
                if (above.getType() != Material.AIR || !guard.canModify(above.getLocation())) {
                    continue;
                }
                above.setType(materialPicker.apply(top), false);
                tracked.add(new BlockPos(world.getUID(), x, above.getY(), z));
            }
        }
    }

    private void revert(World world, int chunkX, int chunkZ, ClaimGuard guard, Set<BlockPos> tracked) {
        var it = tracked.iterator();
        while (it.hasNext()) {
            BlockPos pos = it.next();
            if (!pos.world().equals(world.getUID()) || (pos.x() >> 4) != chunkX || (pos.z() >> 4) != chunkZ) {
                continue;
            }
            Block block = pos.block(plugin);
            if (block != null && guard.canModify(block.getLocation())) {
                block.setType(Material.AIR, false);
            }
            it.remove();
        }
    }

    /** Immediately clears every tracked flower/bush in a world, ignoring season ({@code /solstice restoreworld}). */
    public void forceRevertAll(World world) {
        ClaimGuard guard = plugin.claimGuard();
        for (Set<BlockPos> tracked : List.of(springFlowers, summerBushes)) {
            var it = tracked.iterator();
            while (it.hasNext()) {
                BlockPos pos = it.next();
                if (!pos.world().equals(world.getUID())) continue;
                Block block = pos.block(plugin);
                if (block != null && guard.canModify(block.getLocation())) {
                    block.setType(Material.AIR, false);
                }
                it.remove();
            }
        }
    }
}
