package io.github.kenvandine.hungergames;

import java.util.List;
import java.util.Map;

/**
 * Parameters read from config.yml. Mirrors its top-level sections
 * one-for-one — {@link ConfigLoader} is responsible for reading the actual
 * config file and building one of these; this class itself (and everything
 * it references) has no org.bukkit.* dependency so the game logic in
 * {@link ArenaGame}/{@link RuleResolver}/{@link TwistSelector}/
 * {@link LootPicker} stays plain-Java-testable.
 */
record HungerGamesConfig(
        Game game,
        List<LootEntry> loot,
        int itemsPerChestMin,
        int itemsPerChestMax,
        Twists twists,
        List<ArenaConfig> arenas
) {

    /** The {@code game:} section — base rules every match starts from before any twist is layered on. */
    record Game(
            int minPlayers,
            int maxPlayers,
            int countdownSeconds,
            int gracePeriodSeconds,
            int gameLengthSeconds,
            int deathmatchCountdownSeconds,
            int endingSeconds,
            int finalBorderDiameter,
            int deathmatchBorderDiameter,
            double borderDamageAmount,
            double borderDamageBuffer,
            int borderWarningDistance,
            String lobbyWorld,
            SpawnPoint lobbySpawn
    ) {
    }

    /** The {@code twists:} section. */
    record Twists(boolean enabled, double chance, List<TwistConfig> pool) {
    }

    /** Matches the shipped config.yml's {@code game:} defaults; empty loot/twists/arenas. */
    static HungerGamesConfig defaults() {
        return new HungerGamesConfig(
                new Game(2, 24, 30, 30, 900, 15, 10, 20, 6, 1.0, 4.0, 5, "world",
                        new SpawnPoint(0, 100, 0, 0, 0)),
                List.of(),
                2,
                5,
                new Twists(true, 0.35, List.of()),
                List.of()
        );
    }
}
