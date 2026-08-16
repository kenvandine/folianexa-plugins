package io.github.kenvandine.campuslobby;

import java.util.Map;

/**
 * Parameters controlling the generated campus scene. Mirrors config.yml's
 * {@code scene}/{@code signs} sections one-for-one — CampusLobbyPlugin is
 * responsible for reading the actual config file and building one of
 * these; this class itself has no org.bukkit.* dependency so CampusScene
 * stays plain-Java-testable.
 */
public record SceneConfig(
        int plazaRadius,
        int towerHeight,
        Include include,
        Colors colors,
        Map<String, String> signLabels,
        Clear clear,
        int borderMargin
) {

    public record Include(
            boolean belltower,
            boolean wolfStatue,
            boolean tunnel,
            boolean unionFacade,
            boolean libraryFacade
    ) {
    }

    public record Colors(
            String primaryRed,
            String white,
            String black,
            String brick,
            String brickTrim,
            String glass,
            String roof
    ) {
    }

    /**
     * Controls the pre-build area clear that removes leftover terrain
     * (rogue trees, hills, etc.) before the scene is placed. The clear
     * only ever goes from the plaza floor upward — it never digs below
     * the floor.
     */
    public record Clear(int padding, int heightAbove) {
    }

    /** Matches the shipped config.yml defaults exactly. */
    public static SceneConfig defaults() {
        return new SceneConfig(
                24,
                34,
                new Include(true, true, true, true, true),
                new Colors(
                        "RED_CONCRETE",
                        "WHITE_CONCRETE",
                        "BLACK_CONCRETE",
                        "BRICKS",
                        "CHISELED_BRICKS",
                        "WHITE_STAINED_GLASS_PANE",
                        "BLACKSTONE"
                ),
                Map.of(),
                new Clear(10, 16),
                8
        );
    }
}
