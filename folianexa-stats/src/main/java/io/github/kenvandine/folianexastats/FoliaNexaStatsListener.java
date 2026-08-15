package io.github.kenvandine.folianexastats;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.time.Instant;

/** Thin Bukkit adapter — all real logic lives in {@link StatsTracker}. */
final class FoliaNexaStatsListener implements Listener {

    private final FoliaNexaStatsPlugin plugin;

    FoliaNexaStatsListener(FoliaNexaStatsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        plugin.trackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getTracker().onQuit(event.getPlayer().getUniqueId().toString(), Instant.now());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        plugin.getTracker().recordDeath(event.getEntity().getUniqueId().toString());
        Player killer = event.getEntity().getKiller();
        if (killer != null) {
            plugin.getTracker().recordKill(killer.getUniqueId().toString());
        }
    }

    @EventHandler
    public void onBlockBreak(BlockBreakEvent event) {
        plugin.getTracker().recordBlockMined(event.getPlayer().getUniqueId().toString());
    }
}
