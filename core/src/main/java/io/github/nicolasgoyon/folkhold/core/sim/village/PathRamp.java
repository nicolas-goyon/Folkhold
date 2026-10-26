package io.github.nicolasgoyon.folkhold.core.sim.village;

/**
 * The levels of a straight path on the terrain: from a gate of a village to its streets.
 *
 * <p>The level of a row is the Y of the top block of the path. The levels obey these rules:
 *
 * <ul>
 *   <li>The first row is at the level of the start: the flat terrain of the gate.</li>
 *   <li>2 rows side by side have a difference of {@link #MAX_STEP} block or less: a villager walks up and down the
 *       path.</li>
 *   <li>The last row is at the level of the terrain at its block: the path meets the street. When the difference
 *       between the start and this terrain is larger than the length of the path allows, the last row stays as near
 *       to the terrain as the steps allow.</li>
 *   <li>Between them, a row is as near to the terrain at its block as these rules allow.</li>
 * </ul>
 */
public final class PathRamp {
    /** The largest difference between the levels of 2 rows side by side. */
    public static final int MAX_STEP = 1;

    private PathRamp() {
    }

    /**
     * The levels of the rows of a path.
     *
     * @param start   the level of the first row
     * @param terrain the height of the terrain at each row: the Y of its top block
     * @return the level of each row
     */
    public static int[] levels(int start, int[] terrain) {
        int length = terrain.length;
        int[] levels = new int[length];
        if (length == 0) {
            return levels;
        }
        int last = length - 1;
        int end = terrain[last];
        boolean reachable = Math.abs(end - start) <= last * MAX_STEP;
        levels[0] = start;
        for (int row = 1; row < length; row++) {
            int low = levels[row - 1] - MAX_STEP;
            int high = levels[row - 1] + MAX_STEP;
            if (reachable) {
                // The rows that stay must reach the end.
                low = Math.max(low, end - (last - row) * MAX_STEP);
                high = Math.min(high, end + (last - row) * MAX_STEP);
            }
            levels[row] = Math.max(low, Math.min(high, terrain[row]));
        }
        return levels;
    }
}
