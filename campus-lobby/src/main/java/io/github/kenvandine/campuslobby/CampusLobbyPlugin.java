package io.github.kenvandine.campuslobby;

import org.bukkit.plugin.java.JavaPlugin;

/**
 * Procedurally decorates a FoliaNexa lobby world into an NC State
 * Wolfpack-themed scene. Keep this class thin — wiring only. The actual
 * generation logic lives in CampusScene (plain Java, no org.bukkit.*
 * imports, unit-tested) and SceneBuilder (the only class that touches
 * game state).
 */
public final class CampusLobbyPlugin extends JavaPlugin {

    private volatile String lobbyWorldName;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        LobbyState state = new LobbyStateStore(this).load();
        if (state != null) {
            lobbyWorldName = state.world();
        }

        getServer().getPluginManager().registerEvents(new CampusLobbyListener(this), this);
        getCommand("campuslobby").setExecutor(new CampusLobbyCommand(this));
    }

    @Override
    public void onDisable() {
        // Paper cancels tasks scheduled through its own schedulers
        // (Bukkit.getRegionScheduler()/getGlobalRegionScheduler()/
        // getAsyncScheduler()) automatically on plugin disable — nothing
        // else to clean up here.
    }

    /**
     * The world the lobby scene was most recently built in, or {@code
     * null} if {@code /campuslobby build} hasn't run yet. CampusLobbyListener
     * restricts building/damage in this world to campuslobby.admin holders.
     */
    String getLobbyWorldName() {
        return lobbyWorldName;
    }

    void setLobbyWorldName(String lobbyWorldName) {
        this.lobbyWorldName = lobbyWorldName;
    }
}
