package io.github.kenvandine.flowerwatch.listener;

import io.github.kenvandine.flowerwatch.FixedWindowRateLimiter;
import io.github.kenvandine.flowerwatch.FlowerMaterials;
import io.github.kenvandine.flowerwatch.config.FlowerWatchConfig;
import io.github.kenvandine.flowerwatch.coreprotect.CoreProtectBridge;
import io.github.kenvandine.flowerwatch.log.DiagnosticLogger;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFertilizeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.world.StructureGrowEvent;

import java.util.List;

/**
 * Captures the *cause* of every flower-material block change, the moment
 * it happens — the piece CoreProtect alone can't give you (it records
 * the "what", not the "why"). Every handler is MONITOR-priority and
 * read-only: this plugin never cancels or otherwise changes any of these
 * events, it only observes and logs.
 */
public final class FlowerChangeListener implements Listener {

    private final FlowerWatchConfig config;
    private final DiagnosticLogger logger;
    private final CoreProtectBridge coreProtect;
    private final FixedWindowRateLimiter coreProtectRateLimiter;

    public FlowerChangeListener(FlowerWatchConfig config, DiagnosticLogger logger,
                                 CoreProtectBridge coreProtect, FixedWindowRateLimiter coreProtectRateLimiter) {
        this.config = config;
        this.logger = logger;
        this.coreProtect = coreProtect;
        this.coreProtectRateLimiter = coreProtectRateLimiter;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        if (!config.eventEnabled("block-grow")) return;
        recordTypeChange(event.getBlock(), event.getNewState().getType(), "BlockGrowEvent", null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        if (!config.eventEnabled("block-spread")) return;
        recordTypeChange(event.getBlock(), event.getNewState().getType(), "BlockSpreadEvent",
                null, "source=" + event.getSource().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        if (!config.eventEnabled("block-form")) return;
        recordTypeChange(event.getBlock(), event.getNewState().getType(), "BlockFormEvent", null, null);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockFertilize(BlockFertilizeEvent event) {
        if (!config.eventEnabled("block-fertilize")) return;
        List<BlockState> blocks = event.getBlocks();
        long flowerCount = blocks.stream().filter(b -> FlowerMaterials.isFlower(b.getType())).count();
        if (flowerCount == 0) return;

        Player player = event.getPlayer();
        String playerName = player == null ? null : player.getName();
        Block trigger = event.getBlock();

        // One line for the fertilize event itself (the whole batch), plus
        // one line per resulting flower so each shows up at its own
        // coordinates for density-scan/CoreProtect correlation.
        logAndCrossReference(trigger.getWorld().getName(), trigger.getX(), trigger.getY(), trigger.getZ(),
                trigger, trigger.getType(), "BlockFertilizeEvent", playerName,
                "fertilizedBlocks=" + blocks.size() + " flowersAmong=" + flowerCount);
        for (BlockState state : blocks) {
            if (FlowerMaterials.isFlower(state.getType())) {
                logAndCrossReference(state.getWorld().getName(), state.getX(), state.getY(), state.getZ(),
                        state.getBlock(), state.getType(), "BlockFertilizeEvent(batch)", playerName, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(StructureGrowEvent event) {
        if (!config.eventEnabled("structure-grow")) return;
        List<BlockState> blocks = event.getBlocks();
        long flowerCount = blocks.stream().filter(b -> FlowerMaterials.isFlower(b.getType())).count();
        if (flowerCount == 0) return;

        Player player = event.getPlayer();
        String playerName = player == null ? null : player.getName();
        Block trigger = event.getLocation().getBlock();

        logAndCrossReference(trigger.getWorld().getName(), trigger.getX(), trigger.getY(), trigger.getZ(),
                trigger, trigger.getType(), "StructureGrowEvent", playerName,
                "totalBlocks=" + blocks.size() + " flowersAmong=" + flowerCount
                        + " fromBonemeal=" + event.isFromBonemeal());
        for (BlockState state : blocks) {
            if (FlowerMaterials.isFlower(state.getType())) {
                logAndCrossReference(state.getWorld().getName(), state.getX(), state.getY(), state.getZ(),
                        state.getBlock(), state.getType(), "StructureGrowEvent(batch)", playerName, null);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (!config.eventEnabled("block-place")) return;
        Block placed = event.getBlockPlaced();
        if (!FlowerMaterials.isFlower(placed.getType())) return;
        Player player = event.getPlayer();
        logAndCrossReference(placed.getWorld().getName(), placed.getX(), placed.getY(), placed.getZ(),
                placed, placed.getType(), "BlockPlaceEvent", player.getName(),
                "item=" + event.getItemInHand().getType());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (!config.eventEnabled("block-break")) return;
        Block block = event.getBlock();
        if (!FlowerMaterials.isFlower(block.getType())) return;
        Player player = event.getPlayer();
        logAndCrossReference(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block, block.getType(), "BlockBreakEvent", player.getName(), null);
    }

    private void recordTypeChange(Block block, Material newType, String cause, String player, String extra) {
        if (!FlowerMaterials.isFlower(newType)) return;
        logAndCrossReference(block.getWorld().getName(), block.getX(), block.getY(), block.getZ(),
                block, newType, cause, player, extra);
    }

    private void logAndCrossReference(String world, int x, int y, int z, Block block, Material type,
                                       String cause, String player, String extra) {
        logger.event(world, x >> 4, z >> 4, x, y, z, type.name(), cause, player, extra);

        if (config.coreProtectEnabled() && coreProtect.isAvailable() && coreProtectRateLimiter.tryAcquire()) {
            List<String> rows = coreProtect.lookup(block, config.coreProtectLookbackSeconds());
            logger.coreProtect(world, x, y, z, rows);
        }
    }
}
