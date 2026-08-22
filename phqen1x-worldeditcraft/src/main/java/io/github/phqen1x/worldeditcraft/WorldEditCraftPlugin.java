package io.github.phqen1x.worldeditcraft;

import io.github.phqen1x.worldeditcraft.library.SchematicLibrary;
import io.github.phqen1x.worldeditcraft.llm.LemonadeClient;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * {@code onEnable} wiring only — config, library, the Lemonade client,
 * and command registration. Every plugin in this repo states this same
 * "keep this class thin" convention; the actual work lives in {@link
 * GenerationService}, {@link SchematicLibrary} and {@link LemonadeClient}.
 * {@code onDisable} stays near-empty: Paper cancels tasks scheduled
 * through its own schedulers automatically on plugin disable.
 */
public final class WorldEditCraftPlugin extends JavaPlugin {

    private volatile WorldEditCraftConfig config;
    private volatile LemonadeClient lemonadeClient;
    private volatile SchematicLibrary library;
    private volatile GenerationService generationService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadWorldEditCraftConfig();
        getCommand("wec").setExecutor(new WorldEditCraftCommand(this));
    }

    /** Reloads {@code config.yml} and rebuilds every config-derived service. Never mutates live world state. */
    void reloadWorldEditCraftConfig() {
        reloadConfig();
        this.config = ConfigLoader.load(getConfig());
        this.lemonadeClient = new LemonadeClient(config.lemonade(), getLogger());
        this.library = SchematicLibrary.open(getDataFolder().toPath().resolve(config.library().directory()));
        this.generationService = new GenerationService(lemonadeClient, config, library, getDataFolder().toPath().resolve("failed"));
    }

    WorldEditCraftConfig config() {
        return config;
    }

    LemonadeClient lemonadeClient() {
        return lemonadeClient;
    }

    SchematicLibrary library() {
        return library;
    }

    GenerationService generationService() {
        return generationService;
    }

    /** Runs {@code task} on Paper's async scheduler — never a game-tick thread (see docs/phqen1x-rpg-suite/07-folia-safety.md). */
    void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> task.run());
    }
}
