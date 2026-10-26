package io.github.nicolasgoyon.folkhold.core.sim.village;

/**
 * The check of the terrain before a village starts. The structure reads the terrain on a square grid of points
 * around the start. A village does not start on water or on a steep terrain.
 *
 * <ul>
 *   <li>A point is on water when the surface (the top block, water included) is higher than the floor (the top solid
 *       block).</li>
 *   <li>The points in the middle of the grid are the place of the plaza: they must have no water.</li>
 *   <li>At most {@link #MAX_WATER_PERCENT} percent of the points are on water.</li>
 *   <li>The difference between the highest and the lowest floor is at most {@link #MAX_RELIEF} blocks.</li>
 * </ul>
 */
public final class VillageSite {
    /** The largest difference between the highest and the lowest floor of the grid, in blocks. */
    public static final int MAX_RELIEF = 28;
    /** The largest part of the points on water, in percent. */
    public static final int MAX_WATER_PERCENT = 15;
    /** The points at this distance from the middle of the grid or nearer, in points, are the place of the plaza. */
    public static final int MIDDLE_RADIUS = 1;

    /** The result of a check. */
    public enum Result {
        /** The village can start. */
        ACCEPTED,
        /** A point of the middle is on water. */
        WATER_IN_THE_MIDDLE,
        /** Too many points are on water. */
        TOO_MUCH_WATER,
        /** The relief is too high. */
        TOO_STEEP
    }

    private VillageSite() {
    }

    /**
     * Checks the terrain of a grid.
     *
     * @param size    the number of points on each side of the grid, an odd number
     * @param surface the Y of the top block at each point, water included, row after row
     * @param floor   the Y of the top solid block at each point, row after row
     * @return the result of the check
     */
    public static Result check(int size, int[] surface, int[] floor) {
        if (size < 1 || size % 2 == 0 || surface.length != size * size || floor.length != size * size) {
            throw new IllegalArgumentException("A grid of " + size + " points on each side needs " + size * size
                    + " heights: " + surface.length + " and " + floor.length);
        }
        int middle = size / 2;
        int water = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int row = 0; row < size; row++) {
            for (int column = 0; column < size; column++) {
                int i = row * size + column;
                boolean wet = surface[i] > floor[i];
                boolean inMiddle = Math.abs(row - middle) <= MIDDLE_RADIUS && Math.abs(column - middle) <= MIDDLE_RADIUS;
                if (wet && inMiddle) {
                    return Result.WATER_IN_THE_MIDDLE;
                }
                if (wet) {
                    water++;
                }
                lowest = Math.min(lowest, floor[i]);
                highest = Math.max(highest, floor[i]);
            }
        }
        if (100 * water > MAX_WATER_PERCENT * size * size) {
            return Result.TOO_MUCH_WATER;
        }
        return highest - lowest > MAX_RELIEF ? Result.TOO_STEEP : Result.ACCEPTED;
    }
}
