package io.github.kenvandine.solstice.calendar;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.SeasonDate;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.api.event.DayChangeEvent;
import io.github.kenvandine.solstice.api.event.SeasonChangeEvent;
import io.github.kenvandine.solstice.config.CalendarConfig;
import io.github.kenvandine.solstice.config.MainConfig;
import io.github.kenvandine.solstice.config.MonthDef;
import io.github.kenvandine.solstice.season.SeasonManager;
import io.github.kenvandine.solstice.storage.WorldDataStore;
import org.bukkit.GameRule;
import org.bukkit.World;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Drives every managed world's calendar and clock on the global region scheduler — world time is
 * global-region-owned (PLAN.md §2). Vanilla's daylight cycle is disabled per managed world so
 * Solstice's own variable-length day/night model (PLAN.md §3.3) has sole control.
 */
public final class CalendarEngine implements Listener {

    private final Solstice plugin;
    private final WorldDataStore dataStore;
    private final Map<UUID, AtomicReference<WorldSeasonState>> states = new ConcurrentHashMap<>();

    public CalendarEngine(Solstice plugin) {
        this.plugin = plugin;
        this.dataStore = new WorldDataStore(plugin);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (World world : plugin.getServer().getWorlds()) {
            initWorld(world);
        }
        plugin.schedulers().globalRepeating(unused -> tickAll(), 1, 1);
    }

    public void stop() {
        for (Map.Entry<UUID, AtomicReference<WorldSeasonState>> entry : states.entrySet()) {
            World world = plugin.getServer().getWorld(entry.getKey());
            if (world != null) {
                persist(world, entry.getValue().get());
            }
        }
    }

    @EventHandler
    public void onWorldLoad(WorldLoadEvent event) {
        initWorld(event.getWorld());
    }

    @EventHandler
    public void onWorldUnload(WorldUnloadEvent event) {
        AtomicReference<WorldSeasonState> ref = states.remove(event.getWorld().getUID());
        if (ref != null) {
            persist(event.getWorld(), ref.get());
        }
    }

    private void initWorld(World world) {
        MainConfig main = plugin.config().main();
        if (!main.managesWorld(world.getName())) {
            return;
        }
        CalendarConfig calendar = plugin.config().calendar();

        WorldDataStore.Data defaults = new WorldDataStore.Data(calendar.startDate().totalDays(), 0L, false);
        WorldDataStore.Data data = dataStore.load(world.getName(), defaults);

        SeasonDate date = calendar.dateFromTotalDays(data.totalDays());
        SeasonManager.Derived derived = SeasonManager.derive(main, calendar, date);
        MonthDef monthDef = calendar.monthDef(date.month());

        WorldSeasonState state = new WorldSeasonState(
                date, derived.season(), derived.subSeason(), derived.progress(),
                monthDef.dayTicks(), monthDef.nightTicks(), data.ticksIntoDayNight(), data.timePaused()
        );
        states.put(world.getUID(), new AtomicReference<>(state));

        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, true);
        applyRelativeTime(world, state);
    }

    private void persist(World world, WorldSeasonState state) {
        dataStore.save(world.getName(), new WorldDataStore.Data(
                state.date().totalDays(), state.ticksIntoDayNight(), state.timePaused()));
    }

    public AtomicReference<WorldSeasonState> reference(World world) {
        return states.get(world.getUID());
    }

    /** Current season state snapshot. Lock-free, safe from any thread. Null if not managed. */
    public WorldSeasonState stateOf(World world) {
        AtomicReference<WorldSeasonState> ref = states.get(world.getUID());
        return ref != null ? ref.get() : null;
    }

    public boolean isManaged(World world) {
        return states.containsKey(world.getUID());
    }

    /**
     * Stops ticking this world for the rest of the session ({@code /solstice disable}). Add the
     * world to config.yml's {@code worlds.disabled-worlds} for this to persist across a restart.
     */
    public void disableWorld(World world) {
        AtomicReference<WorldSeasonState> ref = states.remove(world.getUID());
        if (ref != null) {
            persist(world, ref.get());
        }
    }

    private void tickAll() {
        MainConfig main = plugin.config().main();
        CalendarConfig calendar = plugin.config().calendar();
        for (World world : plugin.getServer().getWorlds()) {
            AtomicReference<WorldSeasonState> ref = states.get(world.getUID());
            if (ref == null) {
                continue;
            }
            tickWorld(world, ref, main, calendar);
        }
    }

    private void tickWorld(World world, AtomicReference<WorldSeasonState> ref, MainConfig main, CalendarConfig calendar) {
        WorldSeasonState state = ref.get();
        if (state.timePaused()) {
            return;
        }

        long newTicks = state.ticksIntoDayNight() + 1;
        WorldSeasonState newState;

        if (newTicks >= state.cycleLengthTicks()) {
            newTicks -= state.cycleLengthTicks();
            SeasonDate oldDate = state.date();
            SeasonDate newDate = calendar.nextDay(oldDate);

            DayChangeEvent dayEvent = new DayChangeEvent(world, oldDate, newDate);
            plugin.getServer().getPluginManager().callEvent(dayEvent);

            SeasonManager.Derived derived = SeasonManager.derive(main, calendar, newDate);
            Season adoptedSeason = state.season();
            if (derived.season() != state.season()) {
                SeasonChangeEvent seasonEvent = new SeasonChangeEvent(world, state.season(), derived.season());
                plugin.getServer().getPluginManager().callEvent(seasonEvent);
                if (!seasonEvent.isCancelled()) {
                    adoptedSeason = derived.season();
                }
            }

            MonthDef monthDef = calendar.monthDef(newDate.month());
            newState = new WorldSeasonState(newDate, adoptedSeason, derived.subSeason(), derived.progress(),
                    monthDef.dayTicks(), monthDef.nightTicks(), newTicks, state.timePaused());
        } else {
            newState = state.withTicksIntoDayNight(newTicks);
        }

        ref.set(newState);
        applyRelativeTime(world, newState);
    }

    private void applyRelativeTime(World world, WorldSeasonState state) {
        long dayLength = Math.max(1, state.dayLengthTicks());
        long nightLength = Math.max(1, state.nightLengthTicks());
        long ticks = state.ticksIntoDayNight();

        long relative;
        if (ticks < dayLength) {
            relative = (long) ((ticks / (double) dayLength) * 12000.0);
        } else {
            relative = 12000 + (long) (((ticks - dayLength) / (double) nightLength) * 12000.0);
        }
        world.setTime(relative);
    }

    // --- Admin mutators. All hop to the global region scheduler internally. ---

    public void setDate(World world, SeasonDate date) {
        plugin.schedulers().global(unused -> {
            AtomicReference<WorldSeasonState> ref = states.get(world.getUID());
            if (ref == null) {
                return;
            }
            MainConfig main = plugin.config().main();
            CalendarConfig calendar = plugin.config().calendar();
            SeasonManager.Derived derived = SeasonManager.derive(main, calendar, date);
            MonthDef monthDef = calendar.monthDef(date.month());
            WorldSeasonState state = ref.get();
            WorldSeasonState newState = new WorldSeasonState(date, derived.season(), derived.subSeason(),
                    derived.progress(), monthDef.dayTicks(), monthDef.nightTicks(), state.ticksIntoDayNight(), state.timePaused());
            ref.set(newState);
            applyRelativeTime(world, newState);
        });
    }

    public void setSeason(World world, Season season) {
        MainConfig.MonthDay md = plugin.config().main().seasonStarts().get(season);
        CalendarConfig calendar = plugin.config().calendar();
        AtomicReference<WorldSeasonState> ref = states.get(world.getUID());
        if (ref == null) {
            return;
        }
        int day = calendar.clampDay(md.month(), md.day());
        SeasonDate current = ref.get().date();
        long candidate = calendar.totalDaysFor(current.year(), md.month(), day);
        SeasonDate newDate = calendar.dateFromTotalDays(candidate);
        setDate(world, newDate);
    }

    public void nextSeason(World world) {
        WorldSeasonState state = stateOf(world);
        if (state != null) {
            setSeason(world, state.season().next());
        }
    }

    public void setPaused(World world, boolean paused) {
        plugin.schedulers().global(unused -> {
            AtomicReference<WorldSeasonState> ref = states.get(world.getUID());
            if (ref != null) {
                ref.set(ref.get().withPaused(paused));
            }
        });
    }
}
