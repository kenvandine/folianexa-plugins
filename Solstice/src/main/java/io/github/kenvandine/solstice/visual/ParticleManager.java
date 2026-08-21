package io.github.kenvandine.solstice.visual;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.Season;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.api.event.SeasonParticleStartEvent;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Ambient seasonal decoration particles (PLAN.md §3.5): fireflies, shooting stars, falling
 * leaves, and winter night-sparks. Cold breath and sweating are player-condition particles and
 * live with the temperature effects that trigger them ({@code TemperatureEffects}), not here.
 *
 * <p>Each online player gets a lightweight entity-scheduled check (period below); a separate
 * global-scheduled sweep detects each managed world's day-to-night transition to roll winter's
 * "20% chance per night" spark decision once per night, world-wide, rather than per player.
 */
public final class ParticleManager implements Listener {

    private static final Set<Material> FOREST_LEAVES = Set.of(Material.OAK_LEAVES, Material.BIRCH_LEAVES);
    private static final double FIREFLY_CHANCE_PER_TICK = 5.0 / 180.0; // ~once per 3 minutes, checked every 5s
    private static final double NIGHT_SPARK_CHANCE = 0.20;
    private static final long PLAYER_CHECK_PERIOD_TICKS = 100L; // 5s
    private static final long WORLD_SWEEP_PERIOD_TICKS = 20L; // 1s

    private final Solstice plugin;
    private final Map<UUID, Boolean> lastIsDaytime = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> nightSparkActiveTonight = new ConcurrentHashMap<>();

    public ParticleManager(Solstice plugin) {
        this.plugin = plugin;
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            trackPlayer(player);
        }
        plugin.schedulers().globalRepeating(unused -> sweepWorlds(), WORLD_SWEEP_PERIOD_TICKS, WORLD_SWEEP_PERIOD_TICKS);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        trackPlayer(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        // Per-player state is stack-local to the entityRepeating closure; nothing to clean up here.
    }

    private void trackPlayer(Player player) {
        plugin.schedulers().entityRepeating(player, () -> checkPlayer(player), () -> {
        }, PLAYER_CHECK_PERIOD_TICKS, PLAYER_CHECK_PERIOD_TICKS);
    }

    private void sweepWorlds() {
        if (!plugin.config().main().particlesEnabled()) {
            return;
        }
        for (World world : plugin.getServer().getWorlds()) {
            if (!plugin.calendarEngine().isManaged(world)) {
                continue;
            }
            WorldSeasonState state = plugin.calendarEngine().stateOf(world);
            boolean isDaytime = state.isDaytime();
            Boolean previous = lastIsDaytime.put(world.getUID(), isDaytime);
            if (previous != null && previous && !isDaytime) {
                nightSparkActiveTonight.put(world.getUID(), ThreadLocalRandom.current().nextDouble() < NIGHT_SPARK_CHANCE);
            }
        }
    }

    private void checkPlayer(Player player) {
        if (!plugin.config().main().particlesEnabled()) {
            return;
        }
        World world = player.getWorld();
        WorldSeasonState state = plugin.calendarEngine().stateOf(world);
        if (state == null) {
            return;
        }
        if (!plugin.playerDataStore().get(player.getUniqueId()).seasonParticles()) {
            return;
        }

        boolean isNight = !state.isDaytime();
        Season season = state.season();
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (isNight && (season == Season.SPRING || season == Season.SUMMER)
                && random.nextDouble() < FIREFLY_CHANCE_PER_TICK && isNearForest(player.getLocation())) {
            spawnCluster(player, "firefly", Particle.FIREFLY, player.getLocation().add(0, 1, 0), 8, 2.5);
        }

        if (isNight && season == Season.SUMMER && player.getClientViewDistance() >= 10
                && isNearMidnight(state) && random.nextDouble() < 0.05) {
            spawnShootingStar(player);
        }

        if (!isNight && season == Season.SUMMER && isUnderTree(player.getLocation()) && random.nextDouble() < 0.3) {
            spawnCluster(player, "falling_leaf", Particle.PALE_OAK_LEAVES, player.getLocation().add(0, 3, 0), 4, 1.5);
        }

        if (season == Season.AUTUMN && isUnderTree(player.getLocation()) && random.nextDouble() < 0.4) {
            spawnCluster(player, "autumn_leaf", Particle.TINTED_LEAVES, player.getLocation().add(0, 3, 0), 5, 1.5);
        }

        if (season == Season.WINTER && isNight && nightSparkActiveTonight.getOrDefault(world.getUID(), false)
                && random.nextDouble() < 0.3) {
            spawnCluster(player, "night_spark", Particle.ELECTRIC_SPARK, player.getEyeLocation().add(0, 6, 0), 3, 4.0);
        }
    }

    private boolean isNearMidnight(WorldSeasonState state) {
        long midpoint = state.dayLengthTicks() + state.nightLengthTicks() / 2;
        long window = Math.max(1, state.nightLengthTicks() / 4);
        return Math.abs(state.ticksIntoDayNight() - midpoint) <= window;
    }

    private void spawnShootingStar(Player player) {
        Location start = player.getEyeLocation().add(
                ThreadLocalRandom.current().nextDouble(-20, 20), 25, ThreadLocalRandom.current().nextDouble(-20, 20));
        spawnCluster(player, "shooting_star", Particle.END_ROD, start, 1, 0.0);
    }

    private void spawnCluster(Player player, String kind, Particle particle, Location at, int count, double spread) {
        SeasonParticleStartEvent event = new SeasonParticleStartEvent(player, at, kind);
        plugin.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        player.spawnParticle(particle, at, count, spread * 0.3, spread * 0.3, spread * 0.3, 0.0);
    }

    private boolean isNearForest(Location center) {
        return scanForLeaves(center, 5, 3);
    }

    private boolean isUnderTree(Location center) {
        return scanForLeaves(center, 2, 8);
    }

    private boolean scanForLeaves(Location center, int radius, int height) {
        World world = center.getWorld();
        int cx = center.getBlockX();
        int cy = center.getBlockY();
        int cz = center.getBlockZ();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy <= height; dy++) {
                    if (FOREST_LEAVES.contains(world.getBlockAt(cx + dx, cy + dy, cz + dz).getType())) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
