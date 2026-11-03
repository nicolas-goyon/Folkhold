package io.github.nicolasgoyon.folkhold.events.earthquake;

/** The formulas of the earthquakes. They use no game class, so the JUnit tests check them. */
public final class EarthquakeMath {
    /** The number of ticks in one game day. */
    public static final long TICKS_PER_DAY = 24_000L;

    private EarthquakeMath() {
    }

    /**
     * The intensity of an earthquake at a distance from its epicenter: 1 at the epicenter, 0 at the edge of its area
     * and farther. The intensity goes down slowly near the epicenter and fast near the edge.
     *
     * @param distanceSquared the square of the horizontal distance to the epicenter, in blocks
     * @param radius          the radius of the area, in blocks
     * @return the intensity, from 0 to 1
     */
    public static double intensity(double distanceSquared, int radius) {
        if (radius <= 0) {
            return 0.0;
        }
        return Math.max(0.0, 1.0 - distanceSquared / ((double) radius * radius));
    }

    /**
     * The chance that a block takes damage.
     *
     * @param classChance the chance of the class of the block, at the epicenter, for a strength of 1
     * @param intensity   the intensity at the block (see {@link #intensity(double, int)})
     * @param strength    the strength of the earthquake
     * @return the chance, from 0 to 1
     */
    public static double chance(double classChance, double intensity, double strength) {
        return Math.clamp(classChance * intensity * strength, 0.0, 1.0);
    }

    /**
     * The time to the next natural earthquake: a uniform random time between two numbers of game days.
     *
     * @param random  a random number from 0 (included) to 1 (excluded)
     * @param minDays the shortest time, in game days
     * @param maxDays the longest time, in game days. A value below {@code minDays} counts as {@code minDays}.
     * @return the time, in ticks of game time
     */
    public static long nextDelayTicks(double random, int minDays, int maxDays) {
        int max = Math.max(minDays, maxDays);
        return Math.round((minDays + random * (max - minDays)) * TICKS_PER_DAY);
    }

    /**
     * The depth of a crack in the surface at a point. The crack is deepest at its center and 1 block deep at its
     * ends.
     *
     * @param offset   the distance from the center of the crack, along the crack, in blocks
     * @param half     the half length of the crack, in blocks
     * @param maxDepth the depth at the center, in blocks
     * @return the depth, from 1 to {@code maxDepth}, or 0 outside the crack
     */
    public static int crackDepth(double offset, double half, int maxDepth) {
        if (half <= 0.0 || Math.abs(offset) > half) {
            return 0;
        }
        return Math.max(1, (int) Math.round(maxDepth * (1.0 - Math.abs(offset) / half)));
    }
}
