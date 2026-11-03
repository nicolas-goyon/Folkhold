package io.github.nicolasgoyon.folkhold.events.earthquake;

/**
 * The formulas of the earthquakes. They use no game class, so the JUnit tests check them. The formulas that all the
 * events share are in {@link io.github.nicolasgoyon.folkhold.events.common.EventMath}.
 */
public final class EarthquakeMath {
    private EarthquakeMath() {
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
