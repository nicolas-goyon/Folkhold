package io.github.nicolasgoyon.folkhold.core.sim.reputation;

import java.util.Locale;

/**
 * Tier of a reputation value (-1000 to +1000).
 *
 * <table>
 *   <caption>Tiers</caption>
 *   <tr><th>Tier</th><th>Values</th></tr>
 *   <tr><td>{@link #SWORN_ENEMY}</td><td>-1000 to -601</td></tr>
 *   <tr><td>{@link #HOSTILE}</td><td>-600 to -201</td></tr>
 *   <tr><td>{@link #NEUTRAL}</td><td>-200 to +200</td></tr>
 *   <tr><td>{@link #FRIEND}</td><td>+201 to +600</td></tr>
 *   <tr><td>{@link #ALLY}</td><td>+601 to +1000</td></tr>
 * </table>
 */
public enum ReputationTier {
    SWORN_ENEMY,
    HOSTILE,
    NEUTRAL,
    FRIEND,
    ALLY;

    /**
     * Gives the tier of a reputation value.
     *
     * @param value the reputation value
     * @return the tier
     */
    public static ReputationTier of(int value) {
        if (value <= -601) {
            return SWORN_ENEMY;
        }
        if (value <= -201) {
            return HOSTILE;
        }
        if (value <= 200) {
            return NEUTRAL;
        }
        if (value <= 600) {
            return FRIEND;
        }
        return ALLY;
    }

    /** True for {@link #HOSTILE} and {@link #SWORN_ENEMY}: units attack on sight. */
    public boolean isHostile() {
        return this == HOSTILE || this == SWORN_ENEMY;
    }

    /** True for {@link #FRIEND} and {@link #ALLY}. */
    public boolean isFriendly() {
        return this == FRIEND || this == ALLY;
    }

    /** True if this tier is the given tier or a better one. */
    public boolean isAtLeast(ReputationTier other) {
        return compareTo(other) >= 0;
    }

    /**
     * The glyph of the tier: crosses for danger, a dash for neutral, ticks and arrows for safety. A text shows the
     * glyph with the name of the tier, so the color never gives the tier alone.
     */
    public String glyph() {
        return switch (this) {
            case SWORN_ENEMY -> "✕✕";
            case HOSTILE -> "✕";
            case NEUTRAL -> "–";
            case FRIEND -> "✓";
            case ALLY -> "⇈";
        };
    }

    /** The RGB color of the tier for a text on a dark background: the HUD, the chat, a title, a dark panel. */
    public int color() {
        return switch (this) {
            case SWORN_ENEMY -> 0xFF7A66;
            case HOSTILE -> 0xE8846F;
            case NEUTRAL -> 0xA89F8C;
            case FRIEND -> 0x9AD07E;
            case ALLY -> 0x8FC2F0;
        };
    }

    /** Lower-case name, for translation keys and save data. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Gives the tier with a serialized name.
     *
     * @param name a name from {@link #serializedName()}
     * @return the tier, or {@link #NEUTRAL} for an unknown name
     */
    public static ReputationTier bySerializedName(String name) {
        for (ReputationTier tier : values()) {
            if (tier.serializedName().equals(name)) {
                return tier;
            }
        }
        return NEUTRAL;
    }
}
