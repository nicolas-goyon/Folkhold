package io.github.nicolasgoyon.folkhold.core.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class UiReputationTest {

    @Test
    void eachTierHasItsOwnGlyphAndColor() {
        Set<String> glyphs = new HashSet<>();
        Set<Integer> colors = new HashSet<>();
        Set<Integer> backgrounds = new HashSet<>();
        for (ReputationTier tier : ReputationTier.values()) {
            glyphs.add(UiReputation.glyph(tier));
            colors.add(UiReputation.color(tier));
            backgrounds.add(UiReputation.background(tier));
        }
        assertEquals(ReputationTier.values().length, glyphs.size(), "Two tiers have the same glyph");
        assertEquals(ReputationTier.values().length, colors.size(), "Two tiers have the same color");
        assertEquals(ReputationTier.values().length, backgrounds.size(), "Two tiers have the same background");
    }

    @Test
    void textOnWoodIsOpaque() {
        for (ReputationTier tier : ReputationTier.values()) {
            assertEquals(0xFF, UiReputation.textOnWood(tier) >>> 24);
            assertEquals(UiReputation.color(tier), UiReputation.textOnWood(tier) & 0xFFFFFF);
        }
    }
}
