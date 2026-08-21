package io.github.kenvandine.solstice.api.event;

import io.github.kenvandine.solstice.api.SeasonDate;
import org.bukkit.World;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

/**
 * Fired when a world's calendar date advances by one day. Not cancellable — the day always
 * advances once its length has elapsed; use this to react, not to veto.
 *
 * <p><b>Threading:</b> fired on the global region scheduler thread for {@code world} (PLAN.md §3.9).
 */
public class DayChangeEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final World world;
    private final SeasonDate from;
    private final SeasonDate to;

    public DayChangeEvent(World world, SeasonDate from, SeasonDate to) {
        super(false);
        this.world = world;
        this.from = from;
        this.to = to;
    }

    public World getWorld() {
        return world;
    }

    public SeasonDate getFrom() {
        return from;
    }

    public SeasonDate getTo() {
        return to;
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
