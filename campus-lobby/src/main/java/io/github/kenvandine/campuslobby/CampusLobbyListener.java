package io.github.kenvandine.campuslobby;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Keeps the lobby world exactly as CampusLobby built it: a themed welcome
 * message on join, block-edit protection (only campuslobby.admin holders
 * may break/place blocks or use buckets there), no mob spawning, and no
 * player inventory (cleared on entry, and items can't be dropped or
 * picked back up while there) since the lobby is meant to be a stable
 * hub, not somewhere to build, fight, or carry loot around.
 */
public final class CampusLobbyListener implements Listener {
    private final CampusLobbyPlugin plugin;

    public CampusLobbyListener(CampusLobbyPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (isLobbyWorld(player.getWorld())) {
            player.getInventory().clear();
        }
        Bukkit.getRegionScheduler().runDelayed(plugin, player.getLocation(), task -> {
            if (player.isOnline()) {
                player.sendMessage("Welcome to NC State's Wolfpack lobby — Go Pack!");
            }
        }, 20L);
    }

    @EventHandler
    public void onChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (isLobbyWorld(player.getWorld())) {
            player.getInventory().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDropItem(PlayerDropItemEvent event) {
        if (isLobbyWorld(event.getPlayer().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickupItem(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player && isLobbyWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (isLobbyWorld(event.getEntity().getWorld())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isProtected(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isProtected(event.getBlock().getWorld(), event.getPlayer())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (isLobbyWorld(event.getLocation().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (isLobbyWorld(event.getBlock().getWorld())) {
            event.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        if (isLobbyWorld(event.getBlock().getWorld())) {
            event.setCancelled(true);
        }
    }

    /** True if a non-admin player is trying to edit a block in the built lobby world. */
    private boolean isProtected(World world, Player player) {
        return isLobbyWorld(world) && !player.hasPermission("campuslobby.admin");
    }

    private boolean isLobbyWorld(World world) {
        String lobbyWorldName = plugin.getLobbyWorldName();
        return lobbyWorldName != null && world != null && lobbyWorldName.equals(world.getName());
    }
}
