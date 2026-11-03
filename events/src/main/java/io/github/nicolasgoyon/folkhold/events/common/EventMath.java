package io.github.nicolasgoyon.folkhold.events.common;

/** The formulas that all the events share. They use no game class, so the JUnit tests check them. */
public final class EventMath {
    /** The number of ticks in one game day. */
    public static final long TICKS_PER_DAY = 24_000L;

    private EventMath() {
    }

    /**
     * The intensity of an event at a distance from its center: 1 at the center, 0 at the edge of its area and
     * farther. The intensity goes down slowly near the center and fast near the edge.
     *
     * @param distanceSquared the square of the horizontal distance to the center, in blocks
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
     * @param classChance the chance of the class of the block, at the center, for a strength of 1
     * @param intensity   the intensity at the block (see {@link #intensity(double, int)})
     * @param strength    the strength of the event
     * @return the chance, from 0 to 1
     */
    public static double chance(double classChance, double intensity, double strength) {
        return Math.clamp(classChance * intensity * strength, 0.0, 1.0);
    }

    /**
     * The time to the next natural event: a uniform random time between two numbers of game days.
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
     * A value between two limits.
     *
     * @param random a random number from 0 (included) to 1 (excluded)
     * @param min    the lowest value
     * @param max    the highest value. A value below {@code min} counts as {@code min}.
     * @return the value
     */
    public static double between(double random, double min, double max) {
        return min + random * (Math.max(min, max) - min);
    }
}
