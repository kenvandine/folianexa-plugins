package io.github.kenvandine.campuslobby;

/**
 * Where the most recently built lobby scene lives: the world it was
 * built in, and the build origin within that world. Persisted by
 * {@link LobbyStateStore} so build-world protection (see
 * CampusLobbyListener) and the spawn/border it implies survive a server
 * restart without requiring another {@code /campuslobby build}.
 */
public record LobbyState(String world, int x, int y, int z) {
}
