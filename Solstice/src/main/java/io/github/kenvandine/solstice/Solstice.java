package io.github.kenvandine.solstice;

import io.github.kenvandine.solstice.api.SolsticeAPIImpl;
import io.github.kenvandine.solstice.calendar.CalendarEngine;
import io.github.kenvandine.solstice.command.SeasonCommand;
import io.github.kenvandine.solstice.command.SolsticeCommand;
import io.github.kenvandine.solstice.command.ToggleCommands;
import io.github.kenvandine.solstice.config.ConfigManager;
import io.github.kenvandine.solstice.events.EventManager;
import io.github.kenvandine.solstice.integration.PlaceholderHook;
import io.github.kenvandine.solstice.platform.ClaimGuard;
import io.github.kenvandine.solstice.platform.Schedulers;
import io.github.kenvandine.solstice.storage.PlayerDataStore;
import io.github.kenvandine.solstice.temperature.TemperatureManager;
import io.github.kenvandine.solstice.visual.BiomeColorManager;
import io.github.kenvandine.solstice.visual.ParticleManager;
import io.github.kenvandine.solstice.world.CropListener;
import io.github.kenvandine.solstice.world.FloraManager;
import io.github.kenvandine.solstice.world.MobSpawnListener;
import io.github.kenvandine.solstice.world.SnowManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Solstice extends JavaPlugin {

    private Schedulers schedulers;
    private ConfigManager configManager;
    private CalendarEngine calendarEngine;
    private PlayerDataStore playerDataStore;
    private TemperatureManager temperatureManager;
    private ClaimGuard claimGuard = ClaimGuard.NOOP;
    private SnowManager snowManager;
    private FloraManager floraManager;
    private ParticleManager particleManager;
    private BiomeColorManager biomeColorManager;
    private EventManager eventManager;

    @Override
    public void onEnable() {
        this.schedulers = new Schedulers(this);
        this.configManager = new ConfigManager(this);
        configManager.loadAll();

        this.playerDataStore = new PlayerDataStore(this);
        playerDataStore.loadAll();

        this.calendarEngine = new CalendarEngine(this);
        calendarEngine.start();

        this.temperatureManager = new TemperatureManager(this);
        temperatureManager.start();

        this.snowManager = new SnowManager(this);
        snowManager.start();

        this.floraManager = new FloraManager(this);
        floraManager.start();

        new CropListener(this).start();
        new MobSpawnListener(this).start();

        this.particleManager = new ParticleManager(this);
        particleManager.start();

        this.biomeColorManager = new BiomeColorManager(this);
        biomeColorManager.start();

        this.eventManager = new EventManager(this);
        eventManager.start();

        new SolsticeAPIImpl(this).register();

        registerCommands();

        if (getServer().getPluginManager().isPluginEnabled("PlaceholderAPI")) {
            new PlaceholderHook(this).register();
        }

        getLogger().info("Solstice enabled on " + getServer().getName() + " " + getServer().getVersion());
    }

    private void registerCommands() {
        getCommand("solstice").setExecutor(new SolsticeCommand(this));
        getCommand("season").setExecutor(new SeasonCommand(this));
        ToggleCommands toggles = new ToggleCommands(this);
        for (String name : new String[]{"toggleseasoncolors", "toggletemperature", "toggleseasonparticles",
                "togglefahrenheit", "currentbiome"}) {
            getCommand(name).setExecutor(toggles);
        }
    }

    @Override
    public void onDisable() {
        if (snowManager != null) {
            snowManager.stop();
        }
        if (floraManager != null) {
            floraManager.stop();
        }
        if (biomeColorManager != null) {
            biomeColorManager.stop();
        }
        if (calendarEngine != null) {
            calendarEngine.stop();
        }
        if (playerDataStore != null) {
            playerDataStore.saveAll();
        }
        getLogger().info("Solstice disabled.");
    }

    public Schedulers schedulers() {
        return schedulers;
    }

    public ConfigManager config() {
        return configManager;
    }

    public CalendarEngine calendarEngine() {
        return calendarEngine;
    }

    public PlayerDataStore playerDataStore() {
        return playerDataStore;
    }

    public TemperatureManager temperatureManager() {
        return temperatureManager;
    }

    public ClaimGuard claimGuard() {
        return claimGuard;
    }

    public EventManager eventManager() {
        return eventManager;
    }

    public SnowManager snowManager() {
        return snowManager;
    }

    public FloraManager floraManager() {
        return floraManager;
    }
}
