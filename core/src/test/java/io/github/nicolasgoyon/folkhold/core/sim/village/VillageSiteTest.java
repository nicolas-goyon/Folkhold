package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSite.Result;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

class VillageSiteTest {
    private static final int SIZE = 9;

    private static int[] flat(int height) {
        int[] grid = new int[SIZE * SIZE];
        Arrays.fill(grid, height);
        return grid;
    }

    /** A flat terrain with no water accepts a village. */
    @Test
    void flatDryTerrainIsAccepted() {
        assertEquals(Result.ACCEPTED, VillageSite.check(SIZE, flat(64), flat(64)));
    }

    /** Water in the middle of the grid, at the place of the plaza, refuses a village. */
    @Test
    void waterInTheMiddleIsRefused() {
        int[] surface = flat(64);
        int[] floor = flat(64);
        floor[SIZE * SIZE / 2 + 1] = 60;

        assertEquals(Result.WATER_IN_THE_MIDDLE, VillageSite.check(SIZE, surface, floor));
    }

    /** A small pond near the edge is accepted, a large lake is refused. */
    @Test
    void largeLakeIsRefused() {
        int[] surface = flat(64);
        int[] pond = flat(64);
        pond[0] = 62;
        pond[1] = 62;
        int[] lake = flat(64);
        for (int i = 0; i < 2 * SIZE; i++) {
            lake[i] = 58;
        }

        assertEquals(Result.ACCEPTED, VillageSite.check(SIZE, surface, pond));
        assertEquals(Result.TOO_MUCH_WATER, VillageSite.check(SIZE, surface, lake));
    }

    /** A relief of more than the maximum refuses a village, a relief of the maximum is accepted. */
    @Test
    void steepTerrainIsRefused() {
        int[] gentle = flat(64);
        gentle[0] = 64 + VillageSite.MAX_RELIEF;
        int[] steep = flat(64);
        steep[0] = 65 + VillageSite.MAX_RELIEF;

        assertEquals(Result.ACCEPTED, VillageSite.check(SIZE, gentle, gentle));
        assertEquals(Result.TOO_STEEP, VillageSite.check(SIZE, steep, steep));
    }

    /** A grid needs an odd size and a height for each point. */
    @Test
    void badGridsAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> VillageSite.check(4, new int[16], new int[16]));
        assertThrows(IllegalArgumentException.class, () -> VillageSite.check(SIZE, new int[3], flat(64)));
    }
}
