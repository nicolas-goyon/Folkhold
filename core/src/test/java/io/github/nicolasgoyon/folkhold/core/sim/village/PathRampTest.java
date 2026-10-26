package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PathRampTest {
    /** On a flat terrain at the level of the start, the path stays flat. */
    @Test
    void flatTerrainGivesAFlatPath() {
        assertArrayEquals(new int[] {64, 64, 64, 64}, PathRamp.levels(64, new int[] {64, 64, 64, 64}));
    }

    /** The path follows a terrain with steps of 1 block. */
    @Test
    void pathFollowsAGentleTerrain() {
        assertArrayEquals(new int[] {64, 63, 62, 63, 64}, PathRamp.levels(64, new int[] {64, 63, 62, 63, 64}));
    }

    /**
     * A step of 6 blocks in the terrain: the path goes up by 1 block on each row, early enough to meet the terrain at
     * its last row.
     */
    @Test
    void highStepGivesARamp() {
        int[] levels = PathRamp.levels(64, new int[] {64, 64, 64, 70, 70, 70, 70, 70});

        assertArrayEquals(new int[] {64, 64, 65, 66, 67, 68, 69, 70}, levels);
        assertSteps(levels);
    }

    /** The first row is at the level of the start, also when the terrain under it is lower. */
    @Test
    void firstRowIsAtTheStart() {
        int[] levels = PathRamp.levels(66, new int[] {62, 62, 62, 62, 62});

        assertArrayEquals(new int[] {66, 65, 64, 63, 62}, levels);
    }

    /** With a terrain too far for the length of the path, the path goes as far as the steps allow. */
    @Test
    void farTerrainGivesTheSteepestPath() {
        int[] levels = PathRamp.levels(64, new int[] {64, 70, 75, 80});

        assertArrayEquals(new int[] {64, 65, 66, 67}, levels);
    }

    /** A path with no rows has no levels. */
    @Test
    void emptyPathHasNoLevels() {
        assertArrayEquals(new int[0], PathRamp.levels(64, new int[0]));
    }

    private static void assertSteps(int[] levels) {
        for (int row = 1; row < levels.length; row++) {
            assertTrue(Math.abs(levels[row] - levels[row - 1]) <= PathRamp.MAX_STEP,
                    "Rows " + (row - 1) + " and " + row + " have a step of more than 1 block");
        }
    }
}
