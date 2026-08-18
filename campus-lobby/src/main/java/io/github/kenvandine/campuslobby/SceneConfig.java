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
            boolean libraryFacade,
            boolean enclosure
    ) {
        public Include(boolean belltower, boolean wolfStatue, boolean tunnel, boolean unionFacade, boolean libraryFacade) {
            this(belltower, wolfStatue, tunnel, unionFacade, libraryFacade, true);
        }
    }

    public record Colors(
            String primaryRed,
            String white,
            String black,
            String brick,
            String brickTrim,
            String glass,
            String roof,
            String concreteRed,
            String concreteWhite,
            String concreteBlack,
            String concreteGray,
            String concreteLightGray
    ) {
        public Colors(String primaryRed, String white, String black, String brick, String brickTrim, String glass, String roof) {
            this(primaryRed, white, black, brick, brickTrim, glass, roof,
                    "RED_CONCRETE", "WHITE_CONCRETE", "BLACK_CONCRETE", "GRAY_CONCRETE", "LIGHT_GRAY_CONCRETE");
        }

        // Backwards compatibility helper getters for clay method names if any legacy code refers to them
        public String clayRed() { return concreteRed; }
        public String clayWhite() { return concreteWhite; }
        public String clayBlack() { return concreteBlack; }
        public String clayGray() { return concreteGray; }
        public String clayNormal() { return concreteRed; }
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
                32,
                36,
                new Include(true, true, true, true, true, true),
                new Colors(
                        "RED_CONCRETE",
                        "WHITE_CONCRETE",
                        "BLACK_CONCRETE",
                        "RED_CONCRETE",
                        "BLACK_CONCRETE",
                        "WHITE_STAINED_GLASS_PANE",
                        "POLISHED_BLACKSTONE",
                        "RED_CONCRETE",
                        "WHITE_CONCRETE",
                        "BLACK_CONCRETE",
                        "GRAY_CONCRETE",
                        "LIGHT_GRAY_CONCRETE"
                ),
                Map.of(),
                new Clear(10, 14),
                8
        );
    }
}
