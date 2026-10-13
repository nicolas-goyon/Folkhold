package io.github.nicolasgoyon.folkhold.core.client.ui;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;

/**
 * The look of the reputation tiers: a scale from danger (dark red) to safety (blue). The color never gives the tier
 * alone: a chip also has a glyph and the name of the tier.
 */
public final class UiReputation {
    private UiReputation() {
    }

    /** The background of a chip of the tier. */
    public static int background(ReputationTier tier) {
        return switch (tier) {
            case SWORN_ENEMY -> 0xFF6E1F18;
            case HOSTILE -> 0xFFA4481F;
            case NEUTRAL -> 0xFF8A7F63;
            case FRIEND -> 0xFF4E7D34;
            case ALLY -> 0xFF2C5F8E;
        };
    }

    /** The text on a chip of the tier. */
    public static int text(ReputationTier tier) {
        return switch (tier) {
            case SWORN_ENEMY -> 0xFFFFE2DA;
            case HOSTILE -> 0xFFFFF0E2;
            case NEUTRAL -> 0xFF1F1810;
            case FRIEND -> 0xFFF1FFE6;
            case ALLY -> 0xFFE6F2FF;
        };
    }

    /** The color of the tier for a text on wood or on the HUD. */
    public static int textOnWood(ReputationTier tier) {
        return 0xFF000000 | tier.color();
    }

    /** The glyph of the tier: crosses for danger, a dash for neutral, ticks and arrows for safety. */
    public static String glyph(ReputationTier tier) {
        return tier.glyph();
    }
}
