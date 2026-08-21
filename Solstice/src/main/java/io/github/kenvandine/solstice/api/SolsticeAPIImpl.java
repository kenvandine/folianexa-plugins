package io.github.kenvandine.solstice.api;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.config.BiomesConfig;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;

public final class SolsticeAPIImpl implements SolsticeAPI {

    private final Solstice plugin;

    public SolsticeAPIImpl(Solstice plugin) {
        this.plugin = plugin;
    }

    public void register() {
        SolsticeAPIHolder.INSTANCE = this;
    }

    private WorldSeasonState requireState(World world) {
        WorldSeasonState state = plugin.calendarEngine().stateOf(world);
        if (state == null) {
            throw new IllegalArgumentException("World '" + world.getName() + "' is not managed by Solstice.");
        }
        return state;
    }

    @Override
    public Season getSeason(World world) {
        return requireState(world).season();
    }

    @Override
    public void setSeason(World world, Season season) {
        plugin.calendarEngine().setSeason(world, season);
    }

    @Override
    public SeasonDate getDate(World world) {
        return requireState(world).date();
    }

    @Override
    public void setDate(World world, SeasonDate date) {
        plugin.calendarEngine().setDate(world, date);
    }

    @Override
    public int getSeconds(World world) {
        return relativeSecondsOfDay(world) % 60;
    }

    @Override
    public int getMinutes(World world) {
        return (relativeSecondsOfDay(world) / 60) % 60;
    }

    @Override
    public int getHours(World world) {
        return (relativeSecondsOfDay(world) / 3600) % 24;
    }

    private int relativeSecondsOfDay(World world) {
        long relativeTick = Math.floorMod(world.getTime(), 24000L);
        return (int) ((relativeTick / 24000.0) * 86400.0);
    }

    @Override
    public int getDayOfWeek(World world) {
        return requireState(world).date().weekdayIndex();
    }

    @Override
    public String getCurrentMonthName(World world) {
        SeasonDate date = requireState(world).date();
        return plugin.config().calendar().monthDef(date.month()).name();
    }

    @Override
    public double getTemperature(Player player) {
        return plugin.temperatureManager().getTemperature(player);
    }

    @Override
    public double getAirTemperature(Location location) {
        return plugin.temperatureManager().getAirTemperature(location);
    }

    @Override
    public void applyTimedTemperatureEffect(Player player, double delta, int seconds) {
        plugin.temperatureManager().applyTimedTemperatureEffect(player, delta, seconds);
    }

    @Override
    public TemperatureEffectHandle applyPermanentTemperatureEffect(Player player, double delta) {
        return plugin.temperatureManager().applyPermanentTemperatureEffect(player, delta);
    }

    @Override
    public SeasonalBiomeColors getSeasonalColors(World world, Biome biome) {
        WorldSeasonState state = requireState(world);
        BiomesConfig.RgbColors colors = plugin.config().biomes().colorsFor(biome, state.season());
        return new SeasonalBiomeColors(colors.fog(), colors.water(), colors.waterFog(), colors.sky(), colors.foliage(), colors.grass());
    }
}
