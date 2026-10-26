package io.github.nicolasgoyon.folkhold.core.sim.village;

import java.util.function.IntBinaryOperator;

/**
 * The height of the terrain at a block, from the heights at the corners of a square grid. A corner of the grid has X
 * and Z coordinates that are multiples of the size of the cell. The height of a block is the bilinear interpolation
 * of the 4 corners of its cell, rounded to the nearest block.
 */
public final class HeightGrid {
    private HeightGrid() {
    }

    /**
     * Interpolates the height of a block. The method does not read a corner with a weight of 0: the height of a
     * corner block is the height of this corner, and a block on an edge of a cell reads 2 corners.
     *
     * @param x      the X of the block
     * @param z      the Z of the block
     * @param cell   the size of a cell of the grid, in blocks (1 or more)
     * @param corner the height at a corner, from its X and its Z
     * @return the height of the block
     */
    public static int interpolate(int x, int z, int cell, IntBinaryOperator corner) {
        if (cell < 1) {
            throw new IllegalArgumentException("A cell has a size of 1 block or more: " + cell);
        }
        int x0 = Math.floorDiv(x, cell) * cell;
        int z0 = Math.floorDiv(z, cell) * cell;
        int dx = x - x0;
        int dz = z - z0;
        double sum = (double) (cell - dx) * (cell - dz) * corner.applyAsInt(x0, z0);
        if (dx > 0) {
            sum += (double) dx * (cell - dz) * corner.applyAsInt(x0 + cell, z0);
        }
        if (dz > 0) {
            sum += (double) (cell - dx) * dz * corner.applyAsInt(x0, z0 + cell);
        }
        if (dx > 0 && dz > 0) {
            sum += (double) dx * dz * corner.applyAsInt(x0 + cell, z0 + cell);
        }
        return (int) Math.round(sum / ((double) cell * cell));
    }
}
