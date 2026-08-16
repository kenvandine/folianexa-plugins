package io.github.kenvandine.hungergames;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ArenaGameTest {

    private static ArenaConfig arena(int spawnPointCount) {
        List<SpawnPoint> spawns = new java.util.ArrayList<>();
        for (int i = 0; i < spawnPointCount; i++) {
            spawns.add(new SpawnPoint(i, 65, 0, 0, 0));
        }
        return new ArenaConfig("test", "Test Arena", "hg_test",
                new Point(-50, 0, -50), new Point(50, 255, 50),
                new Point(0, 65, 0), 50, spawns, List.of(), List.of());
    }

    @Test
    void addingTributesRespectsCapacity() {
        ArenaGame game = new ArenaGame(arena(2));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();

        assertTrue(game.addTribute(a, 2));
        assertTrue(game.addTribute(b, 2));
        assertFalse(game.addTribute(c, 2), "capacity of 2 should reject a third tribute");
        assertEquals(2, game.tributes().size());
    }

    @Test
    void cannotJoinTwiceOrJoinAfterGameStarts() {
        ArenaGame game = new ArenaGame(arena(4));
        UUID a = UUID.randomUUID();
        assertTrue(game.addTribute(a, 4));
        assertFalse(game.addTribute(a, 4), "already-queued tribute shouldn't be added twice");

        game.startCountdown(Instant.now(), 30);
        UUID b = UUID.randomUUID();
        assertTrue(game.addTribute(b, 4), "still joinable during countdown");

        game.beginGracePeriod(Instant.now(), null, HungerGamesConfig.defaults().game());
        UUID c = UUID.randomUUID();
        assertFalse(game.addTribute(c, 4), "not joinable once a match has started");
    }

    @Test
    void countdownCancelsBackToWaiting() {
        ArenaGame game = new ArenaGame(arena(4));
        game.startCountdown(Instant.now(), 30);
        assertEquals(GameState.COUNTDOWN, game.state());

        game.cancelCountdown();
        assertEquals(GameState.WAITING, game.state());
    }

    @Test
    void countdownElapsesOnlyAfterItsDuration() {
        ArenaGame game = new ArenaGame(arena(4));
        Instant start = Instant.now();
        game.startCountdown(start, 30);

        assertFalse(game.countdownElapsed(start.plusSeconds(29)));
        assertTrue(game.countdownElapsed(start.plusSeconds(30)));
        assertTrue(game.countdownElapsed(start.plusSeconds(31)));
    }

    @Test
    void lastTributeStandingWinsTheMatch() {
        ArenaGame game = new ArenaGame(arena(4));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        UUID c = UUID.randomUUID();
        game.addTribute(a, 4);
        game.addTribute(b, 4);
        game.addTribute(c, 4);
        game.beginGracePeriod(Instant.now(), null, HungerGamesConfig.defaults().game());
        game.beginActive(Instant.now());

        assertFalse(game.eliminate(a));
        assertEquals(GameState.ACTIVE, game.state());

        assertTrue(game.eliminate(b), "eliminating the second-to-last tribute should end the match");
        assertEquals(GameState.ENDING, game.state());
        assertEquals(c, game.winner().orElseThrow());
    }

    @Test
    void eliminatingEveryoneAtOnceEndsWithNoWinner() {
        ArenaGame game = new ArenaGame(arena(4));
        UUID a = UUID.randomUUID();
        game.addTribute(a, 4);
        game.beginGracePeriod(Instant.now(), null, HungerGamesConfig.defaults().game());
        game.beginActive(Instant.now());

        assertTrue(game.eliminate(a));
        assertEquals(GameState.ENDING, game.state());
        assertTrue(game.winner().isEmpty());
    }

    @Test
    void eliminationOutsideActiveOrDeathmatchIsIgnored() {
        ArenaGame game = new ArenaGame(arena(4));
        UUID a = UUID.randomUUID();
        game.addTribute(a, 4);

        assertFalse(game.eliminate(a), "shouldn't be able to eliminate someone before the match is even active");
        assertEquals(GameState.WAITING, game.state());
    }

    @Test
    void resetClearsEverythingBackToWaiting() {
        ArenaGame game = new ArenaGame(arena(4));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        game.addTribute(a, 4);
        game.addTribute(b, 4);
        game.beginGracePeriod(Instant.now(), null, HungerGamesConfig.defaults().game());
        game.beginActive(Instant.now());
        game.eliminate(a);

        game.reset();

        assertEquals(GameState.WAITING, game.state());
        assertTrue(game.tributes().isEmpty());
        assertTrue(game.alive().isEmpty());
        assertTrue(game.kills().isEmpty());
        assertTrue(game.winner().isEmpty());
    }

    @Test
    void gameLengthOnlyElapsesWithMultipleSurvivorsStillAlive() {
        ArenaGame game = new ArenaGame(arena(4));
        UUID a = UUID.randomUUID();
        UUID b = UUID.randomUUID();
        game.addTribute(a, 4);
        game.addTribute(b, 4);
        Instant start = Instant.now();
        game.beginGracePeriod(start, null, HungerGamesConfig.defaults().game());
        game.beginActive(start);

        int gameLength = HungerGamesConfig.defaults().game().gameLengthSeconds();
        assertTrue(game.gameLengthElapsed(start.plusSeconds(gameLength + 1)));

        game.eliminate(a); // ends the match (1 tribute left) before the timer would matter
        assertFalse(game.gameLengthElapsed(start.plusSeconds(gameLength + 1)));
    }
}
