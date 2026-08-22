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
    private PasteService pasteService;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        reloadWorldEditCraftConfig();
        // Created once, not in reloadWorldEditCraftConfig() — PasteService
        // owns in-memory state (in-flight jobs, undo history) a reload
        // must not discard; see its own class docs.
        this.pasteService = new PasteService(this, getDataFolder().toPath().resolve("undo"));
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

    PasteService pasteService() {
        return pasteService;
    }

    /**
     * Writes one {@code config.yml} path and persists it to disk — the
     * mechanism behind {@code /wec set}. Reloads every config-derived
     * service afterward, the same as {@link #reloadWorldEditCraftConfig()}
     * on its own, so the change takes effect immediately without a
     * separate {@code /wec reload}.
     *
     * <p>Known limitation: Bukkit's {@link org.bukkit.configuration.file.YamlConfiguration}
     * rewrites the whole file on save and does not preserve comments, so
     * every {@code # ...} explanation in the shipped {@code config.yml}
     * is lost from the on-disk file the first time this is called — the
     * values survive, the documentation next to them doesn't. See the
     * plugin README for the tradeoff this accepts.
     */
    void setConfigPath(String path, String value) {
        getConfig().set(path, value);
        saveConfig();
        reloadWorldEditCraftConfig();
    }

    /** Runs {@code task} on Paper's async scheduler — never a game-tick thread (see docs/phqen1x-rpg-suite/07-folia-safety.md). */
    void runAsync(Runnable task) {
        Bukkit.getAsyncScheduler().runNow(this, scheduledTask -> task.run());
    }
}
