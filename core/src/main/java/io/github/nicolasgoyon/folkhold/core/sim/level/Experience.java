package io.github.nicolasgoyon.folkhold.core.sim.level;

/**
 * Changes the level and the experience of a settlement.
 *
 * <p>Rules:
 * <ul>
 *   <li>A gain adds experience. When the experience reaches the experience of the level, the settlement goes up
 *       one level and keeps the remaining experience. A large gain can give several levels.</li>
 *   <li>A loss removes experience. Below 0, the settlement goes down one level, with the experience of the lower
 *       level minus the remaining loss (minimum 0). One change removes at most one level.</li>
 *   <li>At the last level, the experience stays below the experience of the last level.</li>
 *   <li>At level 1, the experience does not go below 0.</li>
 * </ul>
 */
public final class Experience {

    /**
     * Level and experience.
     *
     * @param level      the level
     * @param experience the experience in this level
     */
    public record Progress(int level, int experience) {
    }

    private Experience() {
    }

    /**
     * Applies a change of experience.
     *
     * @param level      the current level
     * @param experience the current experience
     * @param delta      the change (positive for a gain, negative for a loss)
     * @param table      the level table
     * @return the new level and experience
     */
    public static Progress apply(int level, int experience, int delta, LevelTable table) {
        int currentLevel = table.clamp(level);
        long current = Math.max(0, experience) + (long) delta;
        if (current < 0) {
            if (currentLevel == 1) {
                return new Progress(1, 0);
            }
            int lower = currentLevel - 1;
            long remaining = table.traits(lower).experience() + current;
            return new Progress(lower, (int) Math.max(0, remaining));
        }
        while (currentLevel < table.maxLevel() && current >= table.traits(currentLevel).experience()) {
            current -= table.traits(currentLevel).experience();
            currentLevel++;
        }
        if (currentLevel == table.maxLevel()) {
            current = Math.min(current, table.traits(currentLevel).experience() - 1L);
        }
        return new Progress(currentLevel, (int) current);
    }

    /**
     * The experience gain of one cycle.
     *
     * @param prosperity the prosperity of the settlement, from 0 (no prosperity) to 1 (full prosperity)
     * @param maxGain    the gain for a full prosperity
     * @return the gain, from 0 to {@code maxGain}
     */
    public static int cycleGain(double prosperity, int maxGain) {
        double clamped = Math.max(0.0, Math.min(1.0, prosperity));
        return (int) Math.round(clamped * maxGain);
    }
}
