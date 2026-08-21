package io.github.kenvandine.solstice.api.event;

import io.github.kenvandine.solstice.api.Season;
import org.bukkit.World;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a world's derived season changes. Cancellable — cancel to keep the previous season
 * active for one more tick (the date still advances; only the season swap is suppressed).
 *
 * <p><b>Threading:</b> fired on the global region scheduler thread for {@code world}, since season
 * state is global-region-owned (PLAN.md §2, §3.9). Listeners must not block; hop to
 * {@code Schedulers} for any follow-up region/entity work.
 */
public class SeasonChangeEvent extends Event implements Cancellable {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final Season oldSeason;
    private final Season newSeason;
    private boolean cancelled;

    public SeasonChangeEvent(World world, Season oldSeason, Season newSeason) {
        super(true);
        this.world = world;
        this.oldSeason = oldSeason;
        this.newSeason = newSeason;
    }

    public World getWorld() {
        return world;
    }

    public Season getOldSeason() {
        return oldSeason;
    }

    public Season getNewSeason() {
        return newSeason;
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
