package io.github.nicolasgoyon.folkhold.core.sim.village;

import java.util.function.IntBinaryOperator;

/**
 * The check of the terrain before a village starts. A village does not start on water, in a large water or on the
 * steepest slopes. The streets of the village follow the terrain. The terrain adaptation and the foundations of the
 * structure accept the other slopes.
 *
 * <p>The check reads the surface of the terrain (the Y of the first free block, water included) on a square grid of
 * {@link #GRID} x {@link #GRID} points, {@link #STEP} blocks apart. The middle of the plaza is the middle of the grid.
 * The calculation of a surface takes time: the grid has few points.
 *
 * <ul>
 *   <li>The middle of the plaza is on water when its surface is higher than its floor (the top solid block).</li>
 *   <li>A point is low when its surface is at the sea level or lower: the sea, a lake or a river. At most
 *       {@link #MAX_LOW_PERCENT} percent of the points are low.</li>
 *   <li>The slope between 2 neighbor points is the difference of their surfaces, divided by {@link #STEP}. The mean
 *       slope of the pairs of neighbor points is at most {@link #MAX_SLOPE_PERCENT} percent.</li>
 * </ul>
 */
public final class VillageSite {
    /** The number of points on each side of the grid. */
    public static final int GRID = 3;
    /** The distance between 2 neighbor points of the grid, in blocks. */
    public static final int STEP = 32;
    /** The largest part of the low points, in percent. */
    public static final int MAX_LOW_PERCENT = 45;
    /** The largest mean slope between the neighbor points, in percent. */
    public static final int MAX_SLOPE_PERCENT = 40;

    /** The result of a check. */
    public enum Result {
        /** The village can start. */
        ACCEPTED,
        /** The middle of the plaza is on water. */
        WATER_IN_THE_MIDDLE,
        /** Too many points are low. */
        TOO_MUCH_WATER,
        /** The mean slope is too steep. */
        TOO_STEEP
    }

    private VillageSite() {
    }

    /**
     * Checks the terrain around the middle of a plaza. The check reads the surface of the other points of the grid
     * only when the middle is not on water.
     *
     * @param middleSurface the Y of the first free block above the middle, water included
     * @param middleFloor   the Y of the first free block above the top solid block of the middle
     * @param seaLevel      the sea level: a surface at this Y or lower is low
     * @param surface       the Y of the first free block above the terrain at an offset from the middle, water
     *                      included, from the offset on the X axis and the offset on the Z axis, in blocks
     * @return the result of the check
     */
    public static Result check(int middleSurface, int middleFloor, int seaLevel, IntBinaryOperator surface) {
        if (middleSurface > middleFloor) {
            return Result.WATER_IN_THE_MIDDLE;
        }
        int half = GRID / 2;
        int[] heights = new int[GRID * GRID];
        int low = 0;
        for (int row = 0; row < GRID; row++) {
            for (int column = 0; column < GRID; column++) {
                int dx = (column - half) * STEP;
                int dz = (row - half) * STEP;
                int height = dx == 0 && dz == 0 ? middleSurface : surface.applyAsInt(dx, dz);
                heights[row * GRID + column] = height;
                if (height <= seaLevel) {
                    low++;
                }
            }
        }
        if (100 * low > MAX_LOW_PERCENT * GRID * GRID) {
            return Result.TOO_MUCH_WATER;
        }
        int differences = 0;
        int pairs = 0;
        for (int row = 0; row < GRID; row++) {
            for (int column = 0; column < GRID; column++) {
                int height = heights[row * GRID + column];
                if (column + 1 < GRID) {
                    differences += Math.abs(height - heights[row * GRID + column + 1]);
                    pairs++;
                }
                if (row + 1 < GRID) {
                    differences += Math.abs(height - heights[(row + 1) * GRID + column]);
                    pairs++;
                }
            }
        }
        return 100 * differences > MAX_SLOPE_PERCENT * pairs * STEP ? Result.TOO_STEEP : Result.ACCEPTED;
    }
}
