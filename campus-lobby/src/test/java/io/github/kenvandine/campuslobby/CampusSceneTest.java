package io.github.kenvandine.campuslobby;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CampusSceneTest {

    @Test
    void defaultSceneIncludesEveryLandmarksSign() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());

        Set<String> signText = new HashSet<>();
        for (SignPlacement sign : scene.signs()) {
            signText.addAll(sign.lines());
        }

        assertTrue(signText.contains("Memorial Belltower"));
        assertTrue(signText.contains("Howl at the Wolfpack statue"));
        assertTrue(signText.contains("Free Expression Tunnel"));
        assertTrue(signText.contains("Talley Student Union"));
        assertTrue(signText.contains("D. H. Hill Jr. Library"));
        assertTrue(signText.contains("Resource Realm"));
        assertTrue(signText.contains("Survival World"));
        assertTrue(signText.contains("Minigames Hub"));
        assertEquals(8, scene.signs().size());
    }

    @Test
    void customSignLabelsOverrideDefaults() {
        SceneConfig defaults = SceneConfig.defaults();
        SceneConfig cfg = new SceneConfig(
                defaults.plazaRadius(),
                defaults.towerHeight(),
                defaults.include(),
                defaults.colors(),
                Map.of("belltower", "Custom Tower Name"),
                defaults.clear(),
                defaults.borderMargin()
        );

        CampusScene.Scene scene = CampusScene.generate(cfg);

        boolean foundCustom = scene.signs().stream()
                .anyMatch(sign -> sign.lines().contains("Custom Tower Name"));
        assertTrue(foundCustom);
    }

    @Test
    void disablingLandmarksOmitsTheirSigns() {
        SceneConfig defaults = SceneConfig.defaults();
        SceneConfig cfg = new SceneConfig(
                defaults.plazaRadius(),
                defaults.towerHeight(),
                new SceneConfig.Include(true, false, false, false, false, false, false),
                defaults.colors(),
                Map.of(),
                defaults.clear(),
                defaults.borderMargin()
        );

        CampusScene.Scene scene = CampusScene.generate(cfg);

        assertEquals(1, scene.signs().size());
        assertTrue(scene.signs().get(0).lines().contains("Memorial Belltower"));
    }

    @Test
    void everyPlacementUsesAValidBukkitMaterialName() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());

        // Bukkit Material enum names are upper snake case; this doesn't
        // require the real org.bukkit.Material enum (kept out of this
        // module's test scope) but does catch typos like lowercase or
        // stray whitespace before they reach the server.
        for (BlockPlacement block : scene.blocks()) {
            assertTrue(block.material().matches("[A-Z0-9_]+"),
                    "not a plausible Material name: " + block.material());
        }
    }

    @Test
    void belltowerIsTallerThanConfiguredHeight() {
        SceneConfig cfg = SceneConfig.defaults();
        CampusScene.Scene scene = CampusScene.generate(cfg);

        int maxY = scene.blocks().stream().mapToInt(BlockPlacement::dy).max().orElseThrow();
        assertTrue(maxY > cfg.towerHeight(), "roof/spire should sit above the configured shaft height");
    }

    @Test
    void wolfpackColorsAreTheDominantPalette() {
        SceneConfig cfg = SceneConfig.defaults();
        CampusScene.Scene scene = CampusScene.generate(cfg);

        Set<String> wolfpackColors = new HashSet<>();
        wolfpackColors.add(cfg.colors().primaryRed());
        wolfpackColors.add(cfg.colors().white());
        wolfpackColors.add(cfg.colors().black());
        wolfpackColors.add(cfg.colors().brick());
        wolfpackColors.add(cfg.colors().brickTrim());
        wolfpackColors.add(cfg.colors().glass());
        wolfpackColors.add(cfg.colors().roof());
        wolfpackColors.add(cfg.colors().concreteRed());
        wolfpackColors.add(cfg.colors().concreteWhite());
        wolfpackColors.add(cfg.colors().concreteBlack());
        wolfpackColors.add(cfg.colors().concreteGray());
        wolfpackColors.add(cfg.colors().concreteLightGray());
        wolfpackColors.addAll(List.of(
                "RED_BANNER", "WHITE_BANNER", "BLACK_BANNER", "POLISHED_BLACKSTONE", "POLISHED_BLACKSTONE_WALL",
                "POLISHED_BLACKSTONE_SLAB", "SMOOTH_STONE", "SMOOTH_STONE_SLAB", "LANTERN", "SOUL_LANTERN",
                "SEA_LANTERN", "RED_STAINED_GLASS", "RED_STAINED_GLASS_PANE", "BLACK_STAINED_GLASS_PANE",
                "WHITE_STAINED_GLASS_PANE", "IRON_BARS", "MOSS_BLOCK", "AZALEA_LEAVES", "RED_TULIP",
                "WHITE_TULIP", "CHAIN", "BELL", "LIGHTNING_ROD", "BOOKSHELF", "AIR",
                "OBSIDIAN", "CRYING_OBSIDIAN", "NETHER_PORTAL"
        ));

        long total = scene.blocks().size();
        long wolfpack = scene.blocks().stream().filter(b -> wolfpackColors.contains(b.material())).count();

        assertTrue(total > 0);
        assertTrue(wolfpack * 100.0 / total > 90.0,
                "expected the Wolfpack concrete palette to dominate, got " + wolfpack + "/" + total);
    }

    @Test
    void emptySceneWhenEveryLandmarkIsDisabledStillHasThePlaza() {
        SceneConfig defaults = SceneConfig.defaults();
        SceneConfig cfg = new SceneConfig(
                defaults.plazaRadius(),
                defaults.towerHeight(),
                new SceneConfig.Include(false, false, false, false, false, false, false),
                defaults.colors(),
                Map.of(),
                defaults.clear(),
                defaults.borderMargin()
        );

        CampusScene.Scene scene = CampusScene.generate(cfg);

        assertFalse(scene.blocks().isEmpty());
        assertEquals(0, scene.signs().size());
    }

    @Test
    void boundsEncloseEveryBlockAndSign() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());
        CampusScene.Bounds bounds = scene.bounds();

        for (BlockPlacement block : scene.blocks()) {
            assertTrue(block.dx() >= bounds.minX() && block.dx() <= bounds.maxX());
            assertTrue(block.dy() >= bounds.minY() && block.dy() <= bounds.maxY());
            assertTrue(block.dz() >= bounds.minZ() && block.dz() <= bounds.maxZ());
        }
        for (SignPlacement sign : scene.signs()) {
            assertTrue(sign.dx() >= bounds.minX() && sign.dx() <= bounds.maxX());
            assertTrue(sign.dy() >= bounds.minY() && sign.dy() <= bounds.maxY());
            assertTrue(sign.dz() >= bounds.minZ() && sign.dz() <= bounds.maxZ());
        }
        // The plaza floor sits at relative y=0; nothing should be generated below it.
        assertEquals(0, bounds.minY());
    }

    @Test
    void smallerPlazaRadiusStillGeneratesWithoutError() {
        SceneConfig cfg = new SceneConfig(
                12,
                16,
                SceneConfig.defaults().include(),
                SceneConfig.defaults().colors(),
                Map.of(),
                SceneConfig.defaults().clear(),
                SceneConfig.defaults().borderMargin()
        );

        CampusScene.Scene scene = CampusScene.generate(cfg);

        assertFalse(scene.blocks().isEmpty());
        List<BlockPlacement> outOfBounds = scene.blocks().stream()
                .filter(b -> Math.abs(b.dx()) > 50 || Math.abs(b.dz()) > 50)
                .toList();
        assertTrue(outOfBounds.isEmpty(), "no landmark should sprawl unreasonably far past the plaza: " + outOfBounds.size());
    }

    @Test
    void concreteBlocksAreDominantInTheScene() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());

        Set<String> concreteMaterials = Set.of(
                "RED_CONCRETE", "WHITE_CONCRETE", "BLACK_CONCRETE",
                "GRAY_CONCRETE", "LIGHT_GRAY_CONCRETE"
        );

        long concreteBlockCount = scene.blocks().stream()
                .filter(b -> concreteMaterials.contains(b.material()))
                .count();

        assertTrue(concreteBlockCount > 2000, "expected abundant concrete blocks throughout the scene, got: " + concreteBlockCount);

        // Verify no terracotta blocks are present
        boolean hasTerracotta = scene.blocks().stream().anyMatch(b -> b.material().contains("TERRACOTTA"));
        assertFalse(hasTerracotta, "expected no terracotta blocks in the scene");
    }

    @Test
    void plazaSpansEnlargedRadius() {
        SceneConfig cfg = SceneConfig.defaults();
        CampusScene.Scene scene = CampusScene.generate(cfg);

        CampusScene.Bounds bounds = scene.bounds();
        assertTrue(bounds.minX() <= -cfg.plazaRadius());
        assertTrue(bounds.maxX() >= cfg.plazaRadius());
        assertTrue(bounds.minZ() <= -cfg.plazaRadius());
        assertTrue(bounds.maxZ() >= cfg.plazaRadius());
    }

    @Test
    void belltowerIncludesBellAndMemorialBeacon() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());

        boolean hasBell = scene.blocks().stream().anyMatch(b -> "BELL".equals(b.material()));
        boolean hasSeaLantern = scene.blocks().stream().anyMatch(b -> "SEA_LANTERN".equals(b.material()));
        boolean hasLantern = scene.blocks().stream().anyMatch(b -> "LANTERN".equals(b.material()));

        assertTrue(hasBell, "Belltower belfry should include a bell");
        assertTrue(hasSeaLantern, "Belltower / scene should include victory lighting beacons");
        assertTrue(hasLantern, "Belltower / plaza should include lanterns");
    }

    @Test
    void tripleNetherPortalsArePlacedWithObsidianAndPortalBlocks() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());

        long netherPortalBlocks = scene.blocks().stream()
                .filter(b -> "NETHER_PORTAL".equals(b.material()))
                .count();
        long obsidianBlocks = scene.blocks().stream()
                .filter(b -> "OBSIDIAN".equals(b.material()) || "CRYING_OBSIDIAN".equals(b.material()))
                .count();
        long soulLanterns = scene.blocks().stream()
                .filter(b -> "SOUL_LANTERN".equals(b.material()))
                .count();

        assertTrue(netherPortalBlocks >= 20, "Should generate 3 nether portal openings: " + netherPortalBlocks);
        assertTrue(obsidianBlocks >= 30, "Should feature gothic obsidian portal frames: " + obsidianBlocks);
        assertTrue(soulLanterns >= 3, "Should feature 3 soul lanterns for the 3 portals: " + soulLanterns);
    }

    @Test
    void enclosureEnclosesEntirePlazaAndContainsBelltower() {
        SceneConfig cfg = SceneConfig.defaults();
        CampusScene.Scene scene = CampusScene.generate(cfg);

        // Ceiling should sit above belltower spire
        int maxY = scene.blocks().stream().mapToInt(BlockPlacement::dy).max().orElseThrow();
        assertTrue(maxY >= cfg.towerHeight() + 4, "Enclosure ceiling should sit tall above the belltower");

        // Four perimeter walls should exist along the boundaries
        int r = cfg.plazaRadius();
        boolean hasNorthWall = scene.blocks().stream().anyMatch(b -> b.dz() == -r && b.dy() > 15);
        boolean hasSouthWall = scene.blocks().stream().anyMatch(b -> b.dz() == r && b.dy() > 15);
        boolean hasWestWall = scene.blocks().stream().anyMatch(b -> b.dx() == -r && b.dy() > 15);
        boolean hasEastWall = scene.blocks().stream().anyMatch(b -> b.dx() == r && b.dy() > 15);

        assertTrue(hasNorthWall && hasSouthWall && hasWestWall && hasEastWall,
                "All 4 perimeter walls should be present in the enclosure");
    }

    @Test
    void enclosureWallsAndCeilingAreSolidWithNoGlass() {
        SceneConfig cfg = SceneConfig.defaults();
        CampusScene.Scene scene = CampusScene.generate(cfg);

        int r = cfg.plazaRadius();

        // Check outer perimeter walls for glass
        boolean outerWallHasGlass = scene.blocks().stream()
                .filter(b -> (Math.abs(b.dx()) == r || Math.abs(b.dz()) == r) && b.dy() >= 1)
                .anyMatch(b -> b.material().contains("GLASS"));
        assertFalse(outerWallHasGlass, "Outer perimeter walls must be solid with no glass to prevent looking outside");

        // Check ceiling for glass
        boolean ceilingHasGlass = scene.blocks().stream()
                .filter(b -> b.dy() >= 25)
                .anyMatch(b -> b.material().contains("GLASS"));
        assertFalse(ceilingHasGlass, "Ceiling must be closed with solid blocks with no glass");
    }

    @Test
    void wolfpackDecorationsIncludeBannersAndBelltowerSpire() {
        CampusScene.Scene scene = CampusScene.generate(SceneConfig.defaults());

        long bannerCount = scene.blocks().stream()
                .filter(b -> b.material().contains("BANNER"))
                .count();
        assertTrue(bannerCount >= 4, "Scene should contain Wolfpack banners: " + bannerCount);

        long lightningRodCount = scene.blocks().stream()
                .filter(b -> "LIGHTNING_ROD".equals(b.material()))
                .count();
        assertTrue(lightningRodCount >= 4, "Belltower and portals should feature lightning rod finials");
    }
}
