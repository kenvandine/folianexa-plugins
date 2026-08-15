package io.github.kenvandine.folianexastats;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.RegisteredServiceProvider;

import java.util.OptionalDouble;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Reads a player's Vault economy balance — reported as
 * {@code axauctions_wealth}, but gated on AxAuctions specifically being
 * installed, not Vault alone (see the caller in
 * {@code FoliaNexaStatsPlugin}). AxAuctions is a closed-source premium
 * plugin (Artillex Studios) with no public source/API to compile
 * against or verify — like the rest of the Bukkit auction-house
 * ecosystem, it transacts through whichever Vault-registered economy
 * plugin is present rather than implementing its own, so that
 * registered economy's balance is what this actually reads. Built
 * against Vault's real, verified {@code Economy} interface
 * ({@code net.milkbowl.vault.economy.Economy#getBalance(OfflinePlayer)},
 * confirmed against
 * https://github.com/MilkBowl/VaultAPI/blob/master/src/main/java/net/milkbowl/vault/economy/Economy.java).
 *
 * <p>Same threading rule as {@link AuraSkillsIntegration}: only call from
 * the target player's own region thread.
 */
final class VaultEconomyIntegration {

    private final Logger logger;

    VaultEconomyIntegration(Logger logger) {
        this.logger = logger;
    }

    boolean isPresent() {
        return Bukkit.getServicesManager().getRegistration(Economy.class) != null;
    }

    OptionalDouble getBalance(OfflinePlayer player) {
        RegisteredServiceProvider<Economy> registration = Bukkit.getServicesManager().getRegistration(Economy.class);
        if (registration == null) return OptionalDouble.empty();
        try {
            return OptionalDouble.of(registration.getProvider().getBalance(player));
        } catch (RuntimeException e) {
            logger.log(Level.FINE, "Vault balance lookup failed for " + player.getUniqueId(), e);
            return OptionalDouble.empty();
        }
    }
}
