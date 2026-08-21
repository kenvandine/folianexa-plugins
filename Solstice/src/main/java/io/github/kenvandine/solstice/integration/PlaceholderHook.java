package io.github.kenvandine.solstice.integration;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.config.EventsConfig;
import io.github.kenvandine.solstice.season.SeasonManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

/**
 * PlaceholderAPI expansion under the {@code solstice} prefix (PLAN.md §3.8 — deliberately not
 * {@code rs}, to avoid any confusion with the original plugin this was built to replace).
 * World-scoped placeholders take the form {@code %solstice_<name>_<world>%}; without a trailing
 * world segment, they fall back to the requesting player's current world.
 */
public final class PlaceholderHook extends PlaceholderExpansion {

    private final Solstice plugin;

    public PlaceholderHook(Solstice plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "solstice";
    }

    @Override
    public @NotNull String getAuthor() {
        return "kenvandine";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onPlaceholderRequest(Player player, @NotNull String params) {
        String name = params;
        World world = player != null ? player.getWorld() : null;

        int lastUnderscore = params.lastIndexOf('_');
        if (lastUnderscore > 0) {
            String maybeWorld = params.substring(lastUnderscore + 1);
            World explicit = plugin.getServer().getWorld(maybeWorld);
            if (explicit != null && plugin.calendarEngine().isManaged(explicit)) {
                world = explicit;
                name = params.substring(0, lastUnderscore);
            }
        }

        if (world == null || !plugin.calendarEngine().isManaged(world)) {
            return "";
        }
        WorldSeasonState state = plugin.calendarEngine().stateOf(world);

        return switch (name.toLowerCase(Locale.ROOT)) {
            case "season" -> state.season().name();
            case "next_season" -> state.season().next().name();
            case "days_until_next_season" -> String.valueOf(derive(world).daysUntilNextSeason());
            case "day" -> String.valueOf(state.date().day());
            case "weekday" -> plugin.config().calendar().weekdays().get(state.date().weekdayIndex());
            case "month" -> String.valueOf(state.date().month());
            case "month_asname" -> plugin.config().calendar().monthDef(state.date().month()).name();
            case "year" -> String.valueOf(state.date().year());
            case "seasonlength" -> String.valueOf(derive(world).seasonLengthDays());
            case "time" -> formattedTime(world);
            case "active_events" -> String.join(", ", plugin.eventManager().activeEventNames(world));
            case "next_event" -> nextEvent(state).map(e -> e.name).orElse("");
            case "days_until_next_event" -> nextEvent(state).map(e -> String.valueOf(daysUntil(state, e))).orElse("");
            case "biome" -> player != null ? player.getLocation().getBlock().getBiome().getKey().getKey() : "";
            case "temperature" -> player != null ? formatTemp(playerTemp(player)) : "";
            case "temperature_int" -> player != null ? String.valueOf(Math.round(playerTemp(player))) : "";
            case "temperature_int_celcius" -> player != null ? String.valueOf(Math.round(playerTemp(player))) : "";
            case "temperature_int_fahr" -> player != null ? String.valueOf(Math.round(toFahrenheit(playerTemp(player)))) : "";
            case "temperaturecolor" -> player != null ? tempColor(playerTemp(player)) : "";
            case "air_temperature" -> formatTemp(plugin.temperatureManager().getAirTemperature(
                    player != null ? player.getLocation() : world.getSpawnLocation()));
            case "air_temperaturecolor" -> tempColor(plugin.temperatureManager().getAirTemperature(
                    player != null ? player.getLocation() : world.getSpawnLocation()));
            case "bottle_icon" -> player != null && playerTemp(player) >= plugin.config().temperature().foodWaterBottleAboveC
                    ? "❄" : "☀";
            default -> null;
        };
    }

    private SeasonManager.Derived derive(World world) {
        return SeasonManager.derive(plugin.config().main(), plugin.config().calendar(),
                plugin.calendarEngine().stateOf(world).date());
    }

    private String formattedTime(World world) {
        var api = io.github.kenvandine.solstice.api.SolsticeAPI.getInstance();
        return String.format("%02d:%02d", api.getHours(world), api.getMinutes(world));
    }

    private double playerTemp(Player player) {
        return plugin.temperatureManager().getTemperature(player);
    }

    private double toFahrenheit(double celsius) {
        return celsius * 9.0 / 5.0 + 32;
    }

    private String formatTemp(double celsius) {
        return Math.round(celsius) + "°C";
    }

    private String tempColor(double celsius) {
        var cfg = plugin.config().temperature();
        if (celsius <= cfg.freezingDamageBelowC) return "&b";
        if (celsius <= cfg.slownessBelowC) return "&9";
        if (celsius >= cfg.burningAboveC) return "&4";
        if (celsius >= cfg.sweatingAboveC) return "&c";
        return "&f";
    }

    private record NextEvent(String name, int month, int day) {
    }

    private java.util.Optional<NextEvent> nextEvent(WorldSeasonState state) {
        NextEvent best = null;
        int bestDelta = Integer.MAX_VALUE;
        for (EventsConfig.BuiltinEvent builtin : plugin.config().events().events()) {
            if (!builtin.enabled) continue;
            int delta = daysFromDoyToDoy(state.date().month(), state.date().day(), builtin.startMonth, builtin.startDay);
            if (delta < bestDelta) {
                bestDelta = delta;
                best = new NextEvent(builtin.name, builtin.startMonth, builtin.startDay);
            }
        }
        return java.util.Optional.ofNullable(best);
    }

    private int daysUntil(WorldSeasonState state, NextEvent event) {
        return daysFromDoyToDoy(state.date().month(), state.date().day(), event.month(), event.day());
    }

    private int daysFromDoyToDoy(int fromMonth, int fromDay, int toMonth, int toDay) {
        var calendar = plugin.config().calendar();
        long fromDoy = calendar.totalDaysFor(1, fromMonth, fromDay);
        long toDoy = calendar.totalDaysFor(1, toMonth, toDay);
        long delta = toDoy - fromDoy;
        if (delta < 0) {
            delta += calendar.daysPerYear();
        }
        return (int) delta;
    }
}
