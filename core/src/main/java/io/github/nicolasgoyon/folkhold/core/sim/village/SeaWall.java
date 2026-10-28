package io.github.nicolasgoyon.folkhold.core.sim.village;

/**
 * The palisade of a maritime village: the water is the wall. The palisade stands on the land side of the ring, and
 * goes down into the water for {@link #EXTENSION} columns at each end of the land side, so that a mob or a player does
 * not walk around it. A ring with no land has no palisade.
 */
public final class SeaWall {
    /** The palisade goes this number of columns into the water at each end of the land side. */
    public static final int EXTENSION = 4;

    private SeaWall() {
    }

    /**
     * The columns of the ring that keep the palisade.
     *
     * @param water true for a column of the ring on water, in the order of the ring (the ring is closed: the last
     *              column is next to the first one)
     * @return true for a column that keeps the palisade: a column on land, or a column on water at {@link #EXTENSION}
     *         columns or less from a column on land along the ring
     */
    public static boolean[] keep(boolean[] water) {
        int size = water.length;
        boolean[] keep = new boolean[size];
        for (int i = 0; i < size; i++) {
            if (!water[i]) {
                for (int d = -EXTENSION; d <= EXTENSION; d++) {
                    keep[Math.floorMod(i + d, size)] = true;
                }
            }
        }
        return keep;
    }
}
