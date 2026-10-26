package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntBinaryOperator;
import org.junit.jupiter.api.Test;

class HeightGridTest {
    private static final int CELL = 4;

    /** The corners that the interpolation reads, with a height of 64 for each corner. */
    private static List<List<Integer>> readCorners(int x, int z) {
        List<List<Integer>> corners = new ArrayList<>();
        HeightGrid.interpolate(x, z, CELL, (cx, cz) -> {
            corners.add(List.of(cx, cz));
            return 64;
        });
        return corners;
    }

    /** A corner block reads only its corner, a block on an edge reads 2 corners, a block in a cell reads 4. */
    @Test
    void cornersWithNoWeightAreNotRead() {
        assertEquals(List.of(List.of(8, 12)), readCorners(8, 12));
        assertEquals(List.of(List.of(8, 12), List.of(12, 12)), readCorners(9, 12));
        assertEquals(List.of(List.of(8, 12), List.of(8, 16)), readCorners(8, 15));
        assertEquals(4, readCorners(9, 15).size());
    }

    /** A negative coordinate uses the cell below it: the block -1 is in the cell from -4 to 0. */
    @Test
    void negativeCoordinatesUseTheCellBelow() {
        assertEquals(List.of(List.of(-4, 0), List.of(0, 0)), readCorners(-1, 0));
    }

    /** The interpolation of a plane gives the plane, also for negative coordinates. */
    @Test
    void planeIsExact() {
        IntBinaryOperator plane = (x, z) -> 64 + 2 * x - 3 * z;

        for (int[] block : new int[][] {{0, 0}, {5, 7}, {-5, -7}, {13, -1}, {-2, 6}}) {
            assertEquals(plane.applyAsInt(block[0], block[1]),
                    HeightGrid.interpolate(block[0], block[1], CELL, plane));
        }
    }

    /** The middle of a cell has the mean of its 4 corners. */
    @Test
    void middleOfACellIsTheMean() {
        IntBinaryOperator corners = (x, z) -> x == 0 ? (z == 0 ? 60 : 64) : (z == 0 ? 68 : 72);

        assertEquals(66, HeightGrid.interpolate(2, 2, CELL, corners));
    }

    /** A cell has a size of 1 block or more. */
    @Test
    void emptyCellIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> HeightGrid.interpolate(0, 0, 0, (x, z) -> 64));
    }
}
