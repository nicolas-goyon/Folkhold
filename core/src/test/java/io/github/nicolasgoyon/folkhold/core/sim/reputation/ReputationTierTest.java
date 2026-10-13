package io.github.nicolasgoyon.folkhold.core.sim.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ReputationTierTest {

    @Test
    void valuesGiveTheirTier() {
        assertEquals(ReputationTier.SWORN_ENEMY, ReputationTier.of(-601));
        assertEquals(ReputationTier.HOSTILE, ReputationTier.of(-201));
        assertEquals(ReputationTier.NEUTRAL, ReputationTier.of(200));
        assertEquals(ReputationTier.FRIEND, ReputationTier.of(201));
        assertEquals(ReputationTier.ALLY, ReputationTier.of(601));
    }

    @Test
    void serializedNameGivesTheTierBack() {
        for (ReputationTier tier : ReputationTier.values()) {
            assertEquals(tier, ReputationTier.bySerializedName(tier.serializedName()));
        }
        assertEquals(ReputationTier.NEUTRAL, ReputationTier.bySerializedName("unknown"),
                "An unknown name must give the neutral tier");
    }
}
