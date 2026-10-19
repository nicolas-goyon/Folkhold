package io.github.nicolasgoyon.folkhold.core.sim.level;

import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import java.util.Objects;

/**
 * Traits of a settlement that come from its level.
 *
 * @param experience    experience to go from this level to the next level (not used for the last level)
 * @param claimRadius   radius in chunks of the territory that the settlement claims
 * @param maxPopulation maximum population
 * @param jobSlots      number of jobs
 * @param stockCapacity maximum quantity of each item in stock
 * @param tradeOffers   number of trade offers of the merchant
 * @param cats          number of village cats
 * @param garrison      the units of the garrison and their equipment set
 */
public record LevelTraits(int experience, int claimRadius, int maxPopulation, int jobSlots, int stockCapacity,
        int tradeOffers, int cats, GarrisonTraits garrison) {

    public LevelTraits {
        if (experience < 1 || claimRadius < 0 || maxPopulation < 0 || jobSlots < 0 || stockCapacity < 0
                || tradeOffers < 0 || cats < 0) {
            throw new IllegalArgumentException("Invalid level traits: " + experience + ", " + claimRadius + ", "
                    + maxPopulation + ", " + jobSlots + ", " + stockCapacity + ", " + tradeOffers + ", " + cats);
        }
        Objects.requireNonNull(garrison, "garrison");
    }
}
