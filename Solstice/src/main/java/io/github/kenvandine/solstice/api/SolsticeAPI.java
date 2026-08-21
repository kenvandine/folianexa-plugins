package io.github.kenvandine.solstice.api;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;

/**
 * Public API surface for other plugins. Mirrors the shape RealisticSeasons publishes (own
 * package, own names — see PLAN.md §3.9) so integrators porting from that plugin have a familiar
 * shape to move to.
 *
 * <p><b>Threading contract:</b> Bukkit events must fire on the thread owning the relevant region,
 * and callers may invoke this API from arbitrary threads. Every getter here reads a lock-free
 * atomic snapshot and is safe to call from any thread. Every mutator internally hops to the
 * correct Folia scheduler (global region for date/season, entity scheduler for player-targeted
 * temperature effects) and returns immediately — the effect is not guaranteed applied by the time
 * the call returns.
 */
public interface SolsticeAPI {

    static SolsticeAPI getInstance() {
        return SolsticeAPIHolder.INSTANCE;
    }

    Season getSeason(World world);

    /** Sets the season by moving the world's date to that season's configured start date. */
    void setSeason(World world, Season season);

    SeasonDate getDate(World world);

    void setDate(World world, SeasonDate date);

    int getSeconds(World world);

    int getMinutes(World world);

    int getHours(World world);

    int getDayOfWeek(World world);

    String getCurrentMonthName(World world);

    /** Current apparent temperature for the player, in Celsius, including all active modifiers. */
    double getTemperature(Player player);

    /** Ambient air temperature at a location, in Celsius, ignoring player-specific modifiers. */
    double getAirTemperature(Location location);

    /** Applies a temperature delta for the given duration; expires on its own. */
    void applyTimedTemperatureEffect(Player player, double delta, int seconds);

    /** Applies a temperature delta with no expiry. Call {@link TemperatureEffectHandle#cancel()} to remove it. */
    TemperatureEffectHandle applyPermanentTemperatureEffect(Player player, double delta);

    /** Seasonal biome color lookup: fog/water/waterFog/sky/foliage/grass, as 0xRRGGBB ints. */
    SeasonalBiomeColors getSeasonalColors(World world, org.bukkit.block.Biome biome);

    interface TemperatureEffectHandle {
        void cancel();
    }

    record SeasonalBiomeColors(int fog, int water, int waterFog, int sky, int foliage, int grass) {
    }

    final class SolsticeAPIHolder {
        static SolsticeAPI INSTANCE;

        private SolsticeAPIHolder() {
        }
    }
}
