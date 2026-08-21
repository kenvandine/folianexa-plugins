package io.github.kenvandine.solstice.platform;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * The only place in this plugin allowed to touch a Folia scheduler API directly.
 * Every other class routes scheduling through here. {@link org.bukkit.scheduler.BukkitScheduler}
 * (Bukkit.getScheduler()) must never be called anywhere in this codebase — it throws under Folia.
 */
public final class Schedulers {

    private final Plugin plugin;

    public Schedulers(Plugin plugin) {
        this.plugin = plugin;
    }

    /** World time, weather, calendar advancement, and other global-region-owned state. */
    public void global(Consumer<Object> task) {
        plugin.getServer().getGlobalRegionScheduler().run(plugin, scheduledTask -> task.accept(scheduledTask));
    }

    public void globalRepeating(Consumer<Object> task, long initialDelayTicks, long periodTicks) {
        plugin.getServer().getGlobalRegionScheduler()
                .runAtFixedRate(plugin, scheduledTask -> task.accept(scheduledTask),
                        Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
    }

    /** Anything tied to a Location or chunk: block edits, snow, flora, particles. */
    public void region(Location location, Runnable task) {
        plugin.getServer().getRegionScheduler().run(plugin, location, scheduledTask -> task.run());
    }

    public ScheduledTask regionRepeating(Location location, Runnable task, long initialDelayTicks, long periodTicks) {
        return plugin.getServer().getRegionScheduler()
                .runAtFixedRate(plugin, location, scheduledTask -> task.run(),
                        Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
    }

    /** Anything tied to a player or mob; follows the entity across regions. */
    public void entity(Entity entity, Runnable task, Runnable retired) {
        entity.getScheduler().run(plugin, scheduledTask -> task.run(), retired);
    }

    public void entityRepeating(Entity entity, Runnable task, Runnable retired, long initialDelayTicks, long periodTicks) {
        entity.getScheduler().runAtFixedRate(plugin, scheduledTask -> task.run(), retired,
                Math.max(1, initialDelayTicks), Math.max(1, periodTicks));
    }

    /** Disk I/O, config parsing, persistence — never touches game state directly. */
    public void async(Runnable task) {
        plugin.getServer().getAsyncScheduler().runNow(plugin, scheduledTask -> task.run());
    }

    public void asyncDelayed(Runnable task, long delay, TimeUnit unit) {
        plugin.getServer().getAsyncScheduler().runDelayed(plugin, scheduledTask -> task.run(), delay, unit);
    }

    public boolean ownedByCurrentRegion(Location location) {
        return plugin.getServer().isOwnedByCurrentRegion(location);
    }

    public boolean ownedByCurrentRegion(Entity entity) {
        return plugin.getServer().isOwnedByCurrentRegion(entity);
    }

    public boolean isGlobalTickThread() {
        return plugin.getServer().isGlobalTickThread();
    }
}
