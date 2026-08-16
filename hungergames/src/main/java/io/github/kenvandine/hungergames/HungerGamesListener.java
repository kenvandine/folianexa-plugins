package io.github.kenvandine.hungergames;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.util.Optional;
import java.util.UUID;

/** Thin Bukkit adapter — all real logic lives in {@link ArenaGame}/{@link GameManager}. */
final class HungerGamesListener implements Listener {

    private final HungerGamesPlugin plugin;

    HungerGamesListener(HungerGamesPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        plugin.getGameManager().leave(event.getPlayer());
    }

    /**
     * A pre-fight safety net: {@code EffectiveRules.gracePeriodSeconds()}
     * already makes tributes invulnerable while queued/spawning (see
     * GameManager#prepareAndTeleport), but this also blocks incidental PvP
     * against a queued/waiting tribute from someone standing in the same
     * lobby world who isn't a tribute at all.
     */
    @EventHandler
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) {
            return;
        }
        Optional<ArenaGame> maybeGame = plugin.getGameManager().findGameForTribute(victim.getUniqueId());
        if (maybeGame.isEmpty()) {
            return;
        }
        GameState state = maybeGame.get().state();
        if (state == GameState.WAITING || state == GameState.COUNTDOWN || state == GameState.GRACE_PERIOD) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        UUID victimId = victim.getUniqueId();
        Optional<ArenaGame> maybeGame = plugin.getGameManager().findGameForTribute(victimId);
        if (maybeGame.isEmpty() || !maybeGame.get().isAlive(victimId)) {
            return;
        }
        Player killer = victim.getKiller();
        victim.setGameMode(GameMode.SPECTATOR);
        plugin.getGameManager().recordElimination(maybeGame.get(), victimId, killer != null ? killer.getUniqueId() : null);
    }

    /** Keeps an eliminated tribute spectating high above the arena instead of respawning at their bed/world spawn. */
    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        Optional<ArenaGame> maybeGame = plugin.getGameManager().findGameForTribute(id);
        if (maybeGame.isEmpty() || maybeGame.get().state() == GameState.WAITING) {
            return;
        }
        ArenaGame game = maybeGame.get();
        World world = Bukkit.getWorld(game.arena().world());
        if (world == null) {
            return;
        }
        Point center = game.arena().borderCenter();
        event.setRespawnLocation(new Location(world, center.x(), center.y() + 40, center.z()));
    }
}
