package io.github.nicolasgoyon.folkhold.core.sim.faction;

import java.util.Set;

/**
 * Fixed data of a faction, made once when the world starts.
 *
 * @param id         the faction id, unique in the world, for example {@code faction_0}
 * @param cultureId  the id of the culture of the faction
 * @param name       the name of the faction (a proper noun, the same in all languages)
 * @param color      the color of the faction (RGB)
 * @param attitude   the base attitude to the players
 * @param traits     the traits of the faction
 * @param centerX    X coordinate (blocks) of the center of the territory
 * @param centerZ    Z coordinate (blocks) of the center of the territory
 */
public record FactionSpec(String id, String cultureId, String name, int color, Attitude attitude,
        Set<FactionTrait> traits, int centerX, int centerZ) {
    public FactionSpec {
        traits = Set.copyOf(traits);
    }
}
