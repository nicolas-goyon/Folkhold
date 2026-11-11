package io.github.nicolasgoyon.folkhold.core.sim.village;

import java.util.ArrayList;
import java.util.List;

/**
 * The check of the terrain of a plot before the generation of the chunks. A building does not go on a plot with bad
 * terrain: the plan makes it a spare plot (see {@link VillagePlan}).
 *
 * <p>The check reads the columns of the terrain at the points of a grid of {@link #STEP} blocks: each X and Z that is a
 * multiple of {@link #STEP} in the box of the plot. A box with no point of the grid on an axis uses its middle on this
 * axis. A box of {@link #STEP} blocks or more in a plot has only points of the plot: a building in a good plot has
 * good terrain.
 *
 * <ul>
 *   <li>{@link Result#LAVA}: the top block of a point is lava.</li>
 *   <li>{@link Result#DEEP_WATER}: the water of a point is deeper than {@link VillageSite#MAX_SHALLOW_DEPTH} blocks.
 *       Over shallow water, the buildings stand on stilts.</li>
 *   <li>{@link Result#STEEP}: the surfaces of 2 points have a difference of more than {@link #MAX_DROP} blocks. The
 *       terrain adaptation and the foundations of the structure accept the other slopes.</li>
 * </ul>
 *
 * <p>The noise of the terrain does not show the ravines and the lava lakes: the carvers and the features make them
 * after the structures. A building over them stands on its foundation.
 */
public final class PlotTerrain {
    /** The distance between 2 points of the grid, in blocks. The noise of the vanilla terrain has cells of 4 blocks. */
    public static final int STEP = 4;
    /** The largest difference of the surfaces of 2 points of a plot, in blocks. */
    public static final int MAX_DROP = 8;

    /** The result of a check. */
    public enum Result {
        /** A building can go on the plot. */
        GOOD,
        /** Lava at the surface. */
        LAVA,
        /** Deep water. */
        DEEP_WATER,
        /** A steep slope. */
        STEEP
    }

    /**
     * A column of the terrain.
     *
     * @param surface the Y of the first free block above the terrain, water and lava included
     * @param floor   the Y of the first free block above the top solid block
     * @param lava    true if the top block is lava
     */
    public record Column(int surface, int floor, boolean lava) {
        /** The depth of the fluid on the column, in blocks. */
        public int depth() {
            return Math.max(0, surface - floor);
        }
    }

    /** The column of the terrain at a position. */
    @FunctionalInterface
    public interface Terrain {
        /**
         * The column at a position.
         *
         * @param x the block X
         * @param z the block Z
         * @return the column
         */
        Column at(int x, int z);
    }

    private PlotTerrain() {
    }

    /**
     * Checks the terrain of a box.
     *
     * @param minX    the smallest X of the box
     * @param minZ    the smallest Z of the box
     * @param maxX    the largest X of the box
     * @param maxZ    the largest Z of the box
     * @param terrain the columns of the terrain
     * @return the result: the first problem in the order lava, deep water, steep slope
     */
    public static Result check(int minX, int minZ, int maxX, int maxZ, Terrain terrain) {
        List<Column> columns = new ArrayList<>();
        for (int x : axis(minX, maxX)) {
            for (int z : axis(minZ, maxZ)) {
                columns.add(terrain.at(x, z));
            }
        }
        return check(columns);
    }

    /**
     * Checks the columns of the points of a plot.
     *
     * @param columns the columns
     * @return the result: the first problem in the order lava, deep water, steep slope
     */
    public static Result check(List<Column> columns) {
        if (columns.stream().anyMatch(Column::lava)) {
            return Result.LAVA;
        }
        if (columns.stream().anyMatch(column -> column.depth() > VillageSite.MAX_SHALLOW_DEPTH)) {
            return Result.DEEP_WATER;
        }
        int lowest = columns.stream().mapToInt(Column::surface).min().orElse(0);
        int highest = columns.stream().mapToInt(Column::surface).max().orElse(0);
        return highest - lowest > MAX_DROP ? Result.STEEP : Result.GOOD;
    }

    /** The coordinates of the points of the grid from a minimum to a maximum, or the middle if the grid has none. */
    static List<Integer> axis(int min, int max) {
        List<Integer> points = new ArrayList<>();
        for (int value = Math.ceilDiv(min, STEP) * STEP; value <= max; value += STEP) {
            points.add(value);
        }
        if (points.isEmpty()) {
            points.add(Math.floorDiv(min + max, 2));
        }
        return points;
    }
}
