package io.github.nicolasgoyon.folkhold.core.sim.village;

/**
 * The heights of the columns of a palisade on the ring of a wall (see {@link WallLayout#ringSize()}).
 *
 * <p>The base of a column is the block under its logs: the top of its foundation. A column has {@link #LOGS} logs above
 * its base. The bases obey these rules:
 *
 * <ul>
 *   <li>A base is not lower than the terrain of its block: the terrain does not cover the palisade.</li>
 *   <li>A base is at most {@link #NEAR_DROP} blocks lower than the terrain near its block. A mob on this terrain
 *       cannot jump onto the logs.</li>
 *   <li>2 columns side by side have a difference of {@link #MAX_STEP} block or less: the top of the palisade has no
 *       high step. A column goes up for this rule only, never down: a foundation fills the space under it.</li>
 * </ul>
 */
public final class PalisadeHeights {
    /** The number of logs of a column, above its base. */
    public static final int LOGS = 4;
    /** The largest difference between a base and the terrain near its block, when the terrain is higher. */
    public static final int NEAR_DROP = 1;
    /** The largest difference between the bases of 2 columns side by side. */
    public static final int MAX_STEP = 1;

    private PalisadeHeights() {
    }

    /**
     * The bases of the columns of a ring.
     *
     * @param line the height of the terrain at each block of the ring: the Y of its top block
     * @param near the highest terrain near each block of the ring, its own block included
     * @return the base of each block of the ring
     */
    public static int[] bases(int[] line, int[] near) {
        if (line.length != near.length) {
            throw new IllegalArgumentException("The terrain lists need the same size: " + line.length + " and "
                    + near.length);
        }
        int[] bases = new int[line.length];
        for (int i = 0; i < line.length; i++) {
            bases[i] = Math.max(line[i], near[i] - NEAR_DROP);
        }
        return smooth(bases);
    }

    /**
     * The bases of a ring with no high step. Each base goes up until its difference with the bases side by side is
     * {@link #MAX_STEP} or less. No base goes down.
     *
     * @param ring the bases of the ring
     * @return the new bases, in a new list
     */
    public static int[] smooth(int[] ring) {
        int size = ring.length;
        int[] bases = ring.clone();
        // A rise goes forward and backward around the ring. It goes down by MAX_STEP at each block, so it stops
        // before one turn of the ring: 2 turns in each direction are sufficient.
        for (int turn = 0; turn < 2; turn++) {
            for (int i = 0; i < size; i++) {
                int previous = bases[Math.floorMod(i - 1, size)];
                bases[i] = Math.max(bases[i], previous - MAX_STEP);
            }
            for (int i = size - 1; i >= 0; i--) {
                int next = bases[Math.floorMod(i + 1, size)];
                bases[i] = Math.max(bases[i], next - MAX_STEP);
            }
        }
        return bases;
    }

    /**
     * The highest terrain near each block of a ring. It is the largest height of each list in a window of the ring
     * around the block. A list can be the terrain of the line, or the terrain at a distance from the line, inside or
     * outside.
     *
     * @param radius the number of blocks before and after the block in the window
     * @param lists  the lists of heights, with the size of the ring
     * @return the highest terrain for each block of the ring
     */
    public static int[] near(int radius, int[]... lists) {
        if (lists.length == 0) {
            throw new IllegalArgumentException("At least one list of heights is necessary");
        }
        int size = lists[0].length;
        int[] near = new int[size];
        for (int i = 0; i < size; i++) {
            int highest = Integer.MIN_VALUE;
            for (int[] list : lists) {
                if (list.length != size) {
                    throw new IllegalArgumentException("The lists of heights need the same size");
                }
                for (int d = -radius; d <= radius; d++) {
                    highest = Math.max(highest, list[Math.floorMod(i + d, size)]);
                }
            }
            near[i] = highest;
        }
        return near;
    }
}
