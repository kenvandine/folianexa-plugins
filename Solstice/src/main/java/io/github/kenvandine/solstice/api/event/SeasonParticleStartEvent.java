package io.github.kenvandine.solstice.api.event;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired just before a seasonal ambient particle effect is shown to a player (fireflies, shooting
 * stars, falling leaves, night sparks, cold breath, sweating). Cancellable to suppress.
 *
 * <p><b>Threading:</b> fired on the region thread owning {@code location}, or the entity's
 * scheduler thread if triggered from an entity-tied effect (PLAN.md §3.9).
 */
public class SeasonParticleStartEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final Location location;
    private final String particleKind;
    private boolean cancelled;

    public SeasonParticleStartEvent(Player player, Location location, String particleKind) {
        super(false);
        this.player = player;
        this.location = location;
        this.particleKind = particleKind;
    }

    public Player getPlayer() {
        return player;
    }

    public Location getLocation() {
        return location;
    }

    /** One of: firefly, shooting_star, falling_leaf, autumn_leaf, night_spark, cold_breath, sweat. */
    public String getParticleKind() {
        return particleKind;
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void setCancelled(boolean cancel) {
        this.cancelled = cancel;
    }

    @Override
    @NotNull
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }
}
