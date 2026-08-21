package io.github.kenvandine.solstice.temperature;

import io.github.kenvandine.solstice.Solstice;
import io.github.kenvandine.solstice.api.SolsticeAPI;
import io.github.kenvandine.solstice.api.WorldSeasonState;
import io.github.kenvandine.solstice.config.Messages;
import io.github.kenvandine.solstice.config.TemperatureConfig;
import io.github.kenvandine.solstice.storage.PlayerPrefs;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRegainHealthEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.potion.PotionType;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Owns the per-player temperature recalculation loop. Each online player gets their own repeating
 * task on {@code entity.getScheduler()} so the computation follows them across regions (PLAN.md
 * §2) rather than living on any single region thread.
 */
public final class TemperatureManager implements Listener {

    /** Resend cadence for the action bar so it never fades between recalculation cycles. */
    private static final long ACTION_BAR_REFRESH_TICKS = 20;

    private final Solstice plugin;
    private final ModifierSet modifiers = new ModifierSet();
    private final Map<UUID, PlayerTemperature> latest = new ConcurrentHashMap<>();
    private final Map<UUID, Double> waterLinger = new ConcurrentHashMap<>();
    private final Map<UUID, EffectRuntimeState> effectState = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBars = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bossBarPadding = new ConcurrentHashMap<>();
    private final Map<UUID, Scoreboard> sidebars = new ConcurrentHashMap<>();
    private volatile boolean sideBarUnsupported = false;
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

    /** Removes every boss bar this manager created so none are left stuck on-screen after a disable/reload. */
    public void stop() {
        for (Map.Entry<UUID, BossBar> entry : bossBars.entrySet()) {
            entry.getValue().removeAll();
        }
        bossBars.clear();
        for (Map.Entry<UUID, BossBar> entry : bossBarPadding.entrySet()) {
            entry.getValue().removeAll();
        }
        bossBarPadding.clear();
        for (UUID id : sidebars.keySet()) {
            Player player = plugin.getServer().getPlayer(id);
            if (player != null) {
                player.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
            }
        }
        sidebars.clear();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        startTracking(event.getPlayer());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        onQuitCleanup(event.getPlayer().getUniqueId());
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
        plugin.schedulers().entityRepeating(player, () -> refreshActionBar(player),
                () -> onQuitCleanup(player.getUniqueId()), ACTION_BAR_REFRESH_TICKS, ACTION_BAR_REFRESH_TICKS);
    }

    private void onQuitCleanup(UUID id) {
        latest.remove(id);
        waterLinger.remove(id);
        effectState.remove(id);
        modifiers.forget(id);
        removeBossBar(bossBars, id);
        removeBossBar(bossBarPadding, id);
        sidebars.remove(id);
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
        updateBossBar(player, cfg, messages, apparentTemperature, state, world);
        updateSidebar(player, cfg, messages, apparentTemperature, state, world);
    }

    private void refreshActionBar(Player player) {
        TemperatureConfig cfg = plugin.config().temperature();
        if (!cfg.enabled || !runtimeEnabled || !cfg.actionBar) {
            return;
        }
        PlayerTemperature temp = latest.get(player.getUniqueId());
        if (temp == null) {
            return;
        }
        displayActionBar(player, cfg, plugin.config().messages(), temp.apparentTemperatureC());
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
        String text = temperatureText(cfg, messages, prefs, apparentTemperature);
        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(text));
    }

    private void updateBossBar(Player player, TemperatureConfig cfg, Messages messages, double apparentTemperature,
                                WorldSeasonState state, World world) {
        UUID id = player.getUniqueId();
        PlayerPrefs prefs = plugin.playerDataStore().get(id);
        if (!cfg.bossBar || !prefs.temperatureDisplay()) {
            removeBossBar(bossBars, id);
            removeBossBar(bossBarPadding, id);
            return;
        }
        String text = bossBarText(cfg, messages, prefs, apparentTemperature, state, world);
        BarColor color = severityColor(cfg, apparentTemperature);
        BossBar bar = bossBars.computeIfAbsent(id, k -> {
            BossBar created = Bukkit.createBossBar(text, color, BarStyle.SOLID);
            created.addPlayer(player);
            return created;
        });
        bar.setTitle(text);
        bar.setColor(color);

        if (cfg.bossBarPadding) {
            // A second, blank, zero-progress bar directly below ours — Minecraft has no true
            // invisible/spacer boss bar, so this is the closest thing to breathing room between
            // ours and whatever boss bar another plugin renders next.
            bossBarPadding.computeIfAbsent(id, k -> {
                BossBar created = Bukkit.createBossBar(" ", BarColor.WHITE, BarStyle.SOLID);
                created.setProgress(0.0);
                created.addPlayer(player);
                return created;
            });
        } else {
            removeBossBar(bossBarPadding, id);
        }
    }

    private void removeBossBar(Map<UUID, BossBar> bars, UUID id) {
        BossBar existing = bars.remove(id);
        if (existing != null) {
            existing.removeAll();
        }
    }

    private void updateSidebar(Player player, TemperatureConfig cfg, Messages messages, double apparentTemperature,
                                WorldSeasonState state, World world) {
        if (sideBarUnsupported) {
            return;
        }
        UUID id = player.getUniqueId();
        PlayerPrefs prefs = plugin.playerDataStore().get(id);
        if (!cfg.sideBar || !prefs.temperatureDisplay()) {
            Scoreboard board = sidebars.remove(id);
            if (board != null) {
                player.setScoreboard(plugin.getServer().getScoreboardManager().getMainScoreboard());
            }
            return;
        }

        Scoreboard board;
        try {
            board = sidebars.computeIfAbsent(id, k -> {
                Scoreboard created = plugin.getServer().getScoreboardManager().getNewScoreboard();
                player.setScoreboard(created);
                return created;
            });
        } catch (UnsupportedOperationException e) {
            sideBarUnsupported = true;
            plugin.getLogger().warning("This server's Bukkit implementation doesn't support per-player "
                    + "scoreboards (ScoreboardManager#getNewScoreboard threw UnsupportedOperationException) "
                    + "— display.side-bar can't work here. Disabling it for this session; use action-bar or "
                    + "boss-bar instead.");
            return;
        }

        Objective objective = board.getObjective("solstice");
        if (objective == null) {
            objective = board.registerNewObjective("solstice", Criteria.DUMMY, messages.raw("temperature.side-bar-title"));
            objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        // Entries must be unique text per board; wiping and re-adding each cycle is simplest since
        // this only runs every recalculate-interval-seconds, not every tick like the action bar.
        for (String entry : new HashSet<>(board.getEntries())) {
            board.resetScores(entry);
        }

        var api = SolsticeAPI.getInstance();
        String season = "§f" + capitalize(state.season().name());
        String time = "§f" + String.format("%02d:%02d", api.getHours(world), api.getMinutes(world));
        String temperature = temperatureText(cfg, messages, prefs, apparentTemperature);

        objective.getScore(season).setScore(3);
        objective.getScore(time).setScore(2);
        objective.getScore(temperature).setScore(1);
    }

    private String temperatureText(TemperatureConfig cfg, Messages messages, PlayerPrefs prefs, double apparentTemperature) {
        boolean fahrenheit = prefs.fahrenheit();
        double displayed = fahrenheit ? (apparentTemperature * 9.0 / 5.0) + 32 : apparentTemperature;
        String unit = fahrenheit ? "°F" : "°C";
        String icon = severityIcon(cfg, apparentTemperature);
        return messages.rawFormat("temperature.action-bar", Map.of(
                "icon", icon,
                "temperature", Math.round(displayed) + unit
        ));
    }

    private String bossBarText(TemperatureConfig cfg, Messages messages, PlayerPrefs prefs, double apparentTemperature,
                                WorldSeasonState state, World world) {
        boolean fahrenheit = prefs.fahrenheit();
        double displayed = fahrenheit ? (apparentTemperature * 9.0 / 5.0) + 32 : apparentTemperature;
        String unit = fahrenheit ? "°F" : "°C";
        String icon = severityIcon(cfg, apparentTemperature);
        var api = SolsticeAPI.getInstance();
        String season = capitalize(state.season().name());
        String time = String.format("%02d:%02d", api.getHours(world), api.getMinutes(world));
        return messages.rawFormat("temperature.boss-bar", Map.of(
                "icon", icon,
                "temperature", Math.round(displayed) + unit,
                "season", season,
                "time", time
        ));
    }

    private String capitalize(String s) {
        return s.charAt(0) + s.substring(1).toLowerCase();
    }

    private String severityIcon(TemperatureConfig cfg, double apparent) {
        if (apparent <= cfg.freezingDamageBelowC) return "§b❄";
        if (apparent <= cfg.slownessBelowC) return "§9❄";
        if (apparent >= cfg.burningAboveC) return "§4🔥";
        if (apparent >= cfg.sweatingAboveC) return "§c♨";
        return "§f";
    }

    private BarColor severityColor(TemperatureConfig cfg, double apparent) {
        if (apparent <= cfg.freezingDamageBelowC) return BarColor.BLUE;
        if (apparent <= cfg.slownessBelowC) return BarColor.BLUE;
        if (apparent >= cfg.burningAboveC) return BarColor.RED;
        if (apparent >= cfg.sweatingAboveC) return BarColor.YELLOW;
        return BarColor.WHITE;
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
