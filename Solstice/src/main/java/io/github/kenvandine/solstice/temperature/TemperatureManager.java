package io.github.kenvandine.solstice.temperature;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.SolsticeAPI;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.config.Messages;
import io.github.kenvandine.solstice.config.TemperatureConfig;
import io.github.kenvandine.solstice.storage.PlayerPrefs;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the per-player temperature recalculation loop. Each online player gets their own repeating
 * task on {@code entity.getScheduler()} so the computation follows them across regions (PLAN.md
 * §2) rather than living on any single region thread.
 */
public final class TemperatureManager implements Listener {

    private final Solstice plugin;
    private final ModifierSet modifiers = new ModifierSet();
    private final Map<UUID, PlayerTemperature> latest = new ConcurrentHashMap<>();
    private final Map<UUID, Double> waterLinger = new ConcurrentHashMap<>();
    private final Map<UUID, EffectRuntimeState> effectState = new ConcurrentHashMap<>();
    private volatile boolean runtimeEnabled = true;

    public TemperatureManager(Solstice plugin) {
        this.plugin = plugin;
    }

    /** Runtime on/off switch for {@code /solstice temperature toggle}, independent of config.yml. */
    public boolean isRuntimeEnabled() {
        return runtimeEnabled;
    }

    public void setRuntimeEnabled(boolean enabled) {
        this.runtimeEnabled = enabled;
    }

    /** Removes every active timed/permanent modifier for a player ({@code /solstice temperature clear}). */
    public void clearEffects(UUID playerId) {
        modifiers.clear(playerId);
    }

    public void start() {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            startTracking(player);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startTracking(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID id = event.getPlayer().getUniqueId();
        latest.remove(id);
        waterLinger.remove(id);
        effectState.remove(id);
        modifiers.forget(id);
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onRegainHealth(EntityRegainHealthEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        TemperatureConfig cfg = plugin.config().temperature();
        PlayerTemperature temp = latest.get(player.getUniqueId());
        if (temp != null && temp.apparentTemperatureC() >= cfg.healingDisabledAboveC) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (event.getItem().getType() != Material.POTION) {
            return;
        }
        org.bukkit.inventory.meta.PotionMeta meta = (org.bukkit.inventory.meta.PotionMeta) event.getItem().getItemMeta();
        if (meta == null || meta.getBasePotionType() != PotionType.WATER) {
            return;
        }
        TemperatureConfig cfg = plugin.config().temperature();
        Player player = event.getPlayer();
        PlayerTemperature temp = latest.get(player.getUniqueId());
        if (temp != null && temp.apparentTemperatureC() >= cfg.foodWaterBottleAboveC) {
            modifiers.addTimed(player.getUniqueId(), cfg.foodWaterBottleModifier, cfg.foodWaterBottleDurationSeconds);
        }
    }

    private void startTracking(Player player) {
        TemperatureConfig cfg = plugin.config().temperature();
        int periodTicks = Math.max(1, cfg.recalculateIntervalSeconds * 20);
        plugin.schedulers().entityRepeating(player, () -> recalculate(player),
                () -> onQuitCleanup(player.getUniqueId()), periodTicks, periodTicks);
    }

    private void onQuitCleanup(UUID id) {
        latest.remove(id);
        waterLinger.remove(id);
        effectState.remove(id);
        modifiers.forget(id);
    }

    private void recalculate(Player player) {
        TemperatureConfig cfg = plugin.config().temperature();
        if (!cfg.enabled || !runtimeEnabled) {
            return;
        }
        Location loc = player.getLocation();
        var world = loc.getWorld();
        if (world == null || !plugin.calendarEngine().isManaged(world)) {
            return;
        }
        if (cfg.disabledDimensions.contains(world.getEnvironment())) {
            return;
        }

        WorldSeasonState state = plugin.calendarEngine().stateOf(world);
        if (state == null) {
            return;
        }

        double airTemperature = TemperatureModel.airTemperature(cfg, state.season(), state.seasonProgress(), loc);

        double waterModifier = updateWaterLinger(player, cfg, state);
        double armor = TemperatureModel.armorModifier(cfg, player.getInventory().getArmorContents());
        double nearby = TemperatureModel.nearbyBlocksModifier(cfg, loc);
        double sprint = player.isSprinting() ? cfg.sprintingMaxModifier : 0.0;
        double food = player.getFoodLevel() >= 20 && airTemperature < cfg.foodFullHungerBelowC ? cfg.foodFullHungerModifier : 0.0;
        double customItems = CustomTempItems.modifierFor(cfg, player);
        double activeEffects = modifiers.sumActive(player.getUniqueId());

        double apparentTemperature = airTemperature + waterModifier + armor + nearby + sprint + food + customItems + activeEffects;

        latest.put(player.getUniqueId(), new PlayerTemperature(airTemperature, apparentTemperature, System.currentTimeMillis()));

        Messages messages = plugin.config().messages();
        EffectRuntimeState runtimeState = effectState.computeIfAbsent(player.getUniqueId(), id -> new EffectRuntimeState());
        TemperatureEffects.apply(player, cfg, cfg.chatWarnings ? messages : null, airTemperature, apparentTemperature, runtimeState);

        if (cfg.actionBar) {
            displayActionBar(player, cfg, messages, apparentTemperature);
        }
    }

    private double updateWaterLinger(Player player, TemperatureConfig cfg, WorldSeasonState state) {
        boolean inWater = player.isInWater() || player.getFreezeTicks() > 0 || player.isInWaterOrBubbleColumn();
        double magnitude = switch (state.season()) {
            case SUMMER, WINTER -> cfg.waterSummerWinterModifier;
            case SPRING, AUTUMN -> cfg.waterSpringAutumnModifier;
        };
        double current = waterLinger.getOrDefault(player.getUniqueId(), 0.0);
        double updated;
        if (inWater) {
            updated = magnitude;
        } else if (current < 0) {
            updated = Math.min(0.0, current + cfg.waterDecayPer2Seconds);
        } else {
            updated = 0.0;
        }
        waterLinger.put(player.getUniqueId(), updated);
        return updated;
    }

    private void displayActionBar(Player player, TemperatureConfig cfg, Messages messages, double apparentTemperature) {
        PlayerPrefs prefs = plugin.playerDataStore().get(player.getUniqueId());
        if (!prefs.temperatureDisplay()) {
            return;
        }
        boolean fahrenheit = prefs.fahrenheit();
        double displayed = fahrenheit ? (apparentTemperature * 9.0 / 5.0) + 32 : apparentTemperature;
        String unit = fahrenheit ? "°F" : "°C";
        String icon = severityIcon(cfg, apparentTemperature);
        String text = messages.rawFormat("temperature.action-bar", Map.of(
                "icon", icon,
                "temperature", Math.round(displayed) + unit
        ));
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private String severityIcon(TemperatureConfig cfg, double apparent) {
        if (apparent <= cfg.freezingDamageBelowC) return "§b❄";
        if (apparent <= cfg.slownessBelowC) return "§9❄";
        if (apparent >= cfg.burningAboveC) return "§4🔥";
        if (apparent >= cfg.sweatingAboveC) return "§c♨";
        return "§f";
    }

    // --- SolsticeAPI surface ---

    public double getTemperature(Player player) {
        PlayerTemperature temp = latest.get(player.getUniqueId());
        return temp != null ? temp.apparentTemperatureC() : 0.0;
    }

    public double getAirTemperature(Location location) {
        var world = location.getWorld();
        if (world == null || !plugin.calendarEngine().isManaged(world)) {
            return 0.0;
        }
        WorldSeasonState state = plugin.calendarEngine().stateOf(world);
        return TemperatureModel.airTemperature(plugin.config().temperature(), state.season(), state.seasonProgress(), location);
    }

    public void applyTimedTemperatureEffect(Player player, double delta, int seconds) {
        modifiers.addTimed(player.getUniqueId(), delta, seconds);
    }

    public SolsticeAPI.TemperatureEffectHandle applyPermanentTemperatureEffect(Player player, double delta) {
        UUID id = modifiers.addPermanent(player.getUniqueId(), delta);
        UUID playerId = player.getUniqueId();
        return () -> modifiers.cancel(playerId, id);
    }
}
