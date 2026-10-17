package io.github.nicolasgoyon.folkhold.core.client.ui;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;

/**
 * The look of the reputation tiers: a scale from danger (dark red) to safety (blue). The color never gives the tier
 * alone: a chip also has a glyph and the name of the tier.
 *
 * <p>This class has no dependency on the client: the server also uses it for the chat messages and the titles.
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

    /** The RGB color of the tier for a text on a dark background: the HUD, the chat, a title, a dark panel. */
    public static int color(ReputationTier tier) {
        return switch (tier) {
            case SWORN_ENEMY -> 0xFF7A66;
            case HOSTILE -> 0xE8846F;
            case NEUTRAL -> 0xA89F8C;
            case FRIEND -> 0x9AD07E;
            case ALLY -> 0x8FC2F0;
        };
    }

    /** The color of the tier for a text on wood or on the HUD, with the alpha. */
    public static int textOnWood(ReputationTier tier) {
        return 0xFF000000 | color(tier);
    }

    /**
     * The glyph of the tier: crosses for danger, a dash for neutral, ticks and arrows for safety. A text shows the
     * glyph with the name of the tier, so the color never gives the tier alone.
     */
    public static String glyph(ReputationTier tier) {
        return switch (tier) {
            case SWORN_ENEMY -> "✕✕";
            case HOSTILE -> "✕";
            case NEUTRAL -> "–";
            case FRIEND -> "✓";
            case ALLY -> "⇈";
        };
    }
}
