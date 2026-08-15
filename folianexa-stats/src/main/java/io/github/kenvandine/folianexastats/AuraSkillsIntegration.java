package io.github.kenvandine.folianexastats;

import dev.aurelium.auraskills.api.AuraSkillsApi;
import dev.aurelium.auraskills.api.user.SkillsUser;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.OptionalInt;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads a player's AuraSkills power level, if AuraSkills is installed —
 * softdepend (plugin.yml), never a hard requirement. Built against
 * AuraSkills API Bukkit's real, verified {@code SkillsUser#getPowerLevel()}
 * (confirmed against the published javadocs at
 * https://aurelium.dev/javadocs/auraskills-api-bukkit/ — "Gets the power
 * level of the player").
 *
 * <p>Must only be called from the target player's own region thread (per
 * this cluster's Folia-safety rules — see
 * docs/plugin-dev/02-plugin-architecture.md in the FoliaNexa repo): this
 * calls into another plugin's live user-data API, which this codebase has
 * no guarantee is safe to touch from an arbitrary thread. See
 * {@code FoliaNexaStatsPlugin#runReportCycle} for how callers schedule
 * that correctly.
 */
final class AuraSkillsIntegration {

    private final Logger logger;

    AuraSkillsIntegration(Logger logger) {
        this.logger = logger;
    }

    boolean isPresent() {
        return Bukkit.getPluginManager().isPluginEnabled("AuraSkills");
    }

    OptionalInt getPowerLevel(Player player) {
        if (!isPresent()) return OptionalInt.empty();
        try {
            AuraSkillsApi api = AuraSkillsApi.get();
            SkillsUser user = api.getUser(player.getUniqueId());
            return OptionalInt.of(user.getPowerLevel());
        } catch (RuntimeException e) {
            // AuraSkills is present but something about its API surface
            // didn't match what this integration expects (version drift,
            // the player not fully loaded into AuraSkills yet, etc.) —
            // degrade to "no reading this cycle" rather than failing the
            // whole report for every other stat/player.
            logger.log(Level.FINE, "AuraSkills power level lookup failed for " + player.getUniqueId(), e);
            return OptionalInt.empty();
        }
    }
}
