package io.github.kenvandine.solstice.world;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockGrowEvent;

/**
 * Seasonal crop growth rules (PLAN.md §3.1): winter crops need a roof to grow at all; summer
 * exposed crops grow twice as fast. Handled inline on {@link BlockGrowEvent} — that event already
 * fires on the region thread owning the block, so no extra scheduling is needed.
 */
public final class CropListener implements Listener {

    private final Solstice plugin;

    public CropListener(Solstice plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onCropGrow(BlockGrowEvent event) {
        if (!(event.getNewState().getBlockData() instanceof Ageable)) {
            return;
        }
        Block block = event.getBlock();
        WorldSeasonState state = plugin.calendarEngine().stateOf(block.getWorld());
        if (state == null) {
            return;
        }

        boolean roofed = block.getRelative(0, 1, 0).getType() != Material.AIR;

        if (state.season() == Season.WINTER && plugin.config().main().winterCropsRequireRoof() && !roofed) {
            event.setCancelled(true);
            return;
        }

        if (state.season() == Season.SUMMER && !roofed) {
            double multiplier = plugin.config().main().summerCropGrowthMultiplier();
            int bonusStages = (int) Math.round(multiplier) - 1;
            if (bonusStages > 0 && event.getNewState().getBlockData() instanceof Ageable ageable) {
                int boosted = Math.min(ageable.getMaximumAge(), ageable.getAge() + bonusStages);
                ageable.setAge(boosted);
                event.getNewState().setBlockData((BlockData) ageable);
            }
        }
    }
}
