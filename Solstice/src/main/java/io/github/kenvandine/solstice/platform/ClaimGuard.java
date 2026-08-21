package io.github.kenvandine.solstice.platform;

import org.bukkit.Location;

/**
 * Abstraction over region-protection plugins (WorldGuard, GriefPrevention, Lands, Factions)
 * so seasonal physical changes (snow, ice, flora) don't vandalize player claims.
 */
public interface ClaimGuard {

    /** Whether Solstice is allowed to place or remove blocks at this location. */
    boolean canModify(Location location);

    ClaimGuard NOOP = location -> true;
}
