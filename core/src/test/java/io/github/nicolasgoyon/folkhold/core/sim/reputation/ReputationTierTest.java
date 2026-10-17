package io.github.nicolasgoyon.folkhold.core.sim.reputation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.Set;
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
    void tiersAreOrderedFromTheWorstToTheBest() {
        assertTrue(ReputationTier.NEUTRAL.isAtLeast(ReputationTier.NEUTRAL));
        assertTrue(ReputationTier.ALLY.isAtLeast(ReputationTier.FRIEND));
        assertFalse(ReputationTier.HOSTILE.isAtLeast(ReputationTier.NEUTRAL));
    }

    @Test
    void serializedNameGivesTheTierBack() {
        for (ReputationTier tier : ReputationTier.values()) {
            assertEquals(tier, ReputationTier.bySerializedName(tier.serializedName()));
        }
        assertEquals(ReputationTier.NEUTRAL, ReputationTier.bySerializedName("unknown"),
                "An unknown name must give the neutral tier");
    }

    @Test
    void eachTierHasItsOwnGlyphAndColor() {
        Set<String> glyphs = new HashSet<>();
        Set<Integer> colors = new HashSet<>();
        for (ReputationTier tier : ReputationTier.values()) {
            glyphs.add(tier.glyph());
            colors.add(tier.color());
        }
        assertEquals(ReputationTier.values().length, glyphs.size(), "Two tiers have the same glyph");
        assertEquals(ReputationTier.values().length, colors.size(), "Two tiers have the same color");
    }
}
