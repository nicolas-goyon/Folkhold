package io.github.nicolasgoyon.folkhold.core.sim.level;

/**
 * Name for a range of levels (for example Hamlet for the levels 1 to 4).
 *
 * @param id       the id of the tier, used in the translation keys
 * @param minLevel the first level of the tier
 * @param maxLevel the last level of the tier
 */
public record Tier(String id, int minLevel, int maxLevel) {

    public Tier {
        if (id == null || id.isBlank() || minLevel < 1 || maxLevel < minLevel) {
            throw new IllegalArgumentException("Invalid tier: " + id + " " + minLevel + "-" + maxLevel);
        }
    }

    public boolean contains(int level) {
        return level >= minLevel && level <= maxLevel;
    }
}
