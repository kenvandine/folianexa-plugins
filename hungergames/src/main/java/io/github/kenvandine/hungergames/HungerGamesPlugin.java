package io.github.kenvandine.hungergames;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * A configurable battle-royale minigame: tributes queue into an arena,
 * fight down to one survivor once a border-shrinking match starts, with
 * optional randomized "twists" (Quarter-Quell-style rule changes) each
 * match. Keep this class thin — wiring only. The actual game logic lives
 * in {@link ArenaGame} (the state machine, plain Java, unit-tested) and
 * {@link GameManager} (the only class that touches game state — see its
 * javadoc for the Folia-safety rules it follows).
 */
public final class HungerGamesPlugin extends JavaPlugin {

    private GameManager gameManager;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        gameManager = new GameManager(this);
        getServer().getPluginManager().registerEvents(new HungerGamesListener(this), this);
        getCommand("hungergames").setExecutor(new HungerGamesCommand(this));
        gameManager.startTicking();
    }

    @Override
    public void onDisable() {
        if (gameManager != null) {
            gameManager.stopTicking();
        }
        // Paper cancels tasks scheduled through its own schedulers
        // (Bukkit.getRegionScheduler()/getGlobalRegionScheduler()/
        // getAsyncScheduler()) automatically on plugin disable — nothing
        // else to clean up here.
    }

    GameManager getGameManager() {
        return gameManager;
    }

    void reloadHungerGamesConfig() {
        reloadConfig();
        gameManager.reload();
    }
}
