package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSite.Result;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VillageSiteTest {
    private static final int SEA = 63;
    private static final int LAND = 70;
    private static final int STEP = VillageSite.STEP;

    /** A flat terrain above the sea level accepts a village. */
    @Test
    void flatDryTerrainIsAccepted() {
        assertEquals(Result.ACCEPTED, VillageSite.check(LAND, LAND, SEA, (dx, dz) -> LAND));
    }

    /** The check reads the 8 points around the middle, and not the middle. */
    @Test
    void checkReadsThePointsAroundTheMiddle() {
        Set<List<Integer>> offsets = new HashSet<>();

        VillageSite.check(LAND, LAND, SEA, (dx, dz) -> {
            offsets.add(List.of(dx, dz));
            return LAND;
        });

        assertEquals(Set.of(List.of(-STEP, -STEP), List.of(0, -STEP), List.of(STEP, -STEP), List.of(-STEP, 0),
                List.of(STEP, 0), List.of(-STEP, STEP), List.of(0, STEP), List.of(STEP, STEP)), offsets);
    }

    /** Water at the middle of the plaza refuses a village, before the check reads the other points. */
    @Test
    void waterInTheMiddleIsRefused() {
        assertEquals(Result.WATER_IN_THE_MIDDLE, VillageSite.check(SEA, SEA - 4, SEA, (dx, dz) -> {
            throw new AssertionError("The check reads no other point");
        }));
    }

    /** A dry middle at the sea level is accepted: only the surface above the floor is water. */
    @Test
    void dryMiddleAtTheSeaLevelIsAccepted() {
        assertEquals(Result.ACCEPTED, VillageSite.check(SEA, SEA, SEA, (dx, dz) -> SEA + 1));
    }

    /** A lake on 4 points of 9 is accepted, a lake on 5 points is refused. A surface at the sea level is low. */
    @Test
    void largeLakeIsRefused() {
        // The lake covers the west column and the point to the north of the middle.
        Result fourPoints = VillageSite.check(LAND, LAND, SEA,
                (dx, dz) -> dx < 0 || (dx == 0 && dz < 0) ? SEA : LAND);
        // It also covers the point to the south of the middle.
        Result fivePoints = VillageSite.check(LAND, LAND, SEA, (dx, dz) -> dx <= 0 ? SEA : LAND);

        assertEquals(Result.ACCEPTED, fourPoints);
        assertEquals(Result.TOO_MUCH_WATER, fivePoints);
    }

    /**
     * A slope along the X axis: the 6 pairs on the X axis have the difference of the slope, and the 6 pairs on the Z
     * axis have no difference. The steepest accepted difference is the limit of the mean slope of the 12 pairs.
     */
    @Test
    void steepSlopeIsRefused() {
        int limit = VillageSite.MAX_SLOPE_PERCENT * 12 * STEP / (100 * 6);
        int middle = SEA + 2 * limit;

        Result accepted = VillageSite.check(middle, middle, SEA, (dx, dz) -> middle + Integer.signum(dx) * limit);
        Result refused = VillageSite.check(middle, middle, SEA,
                (dx, dz) -> middle + Integer.signum(dx) * (limit + 1));

        assertEquals(Result.ACCEPTED, accepted);
        assertEquals(Result.TOO_STEEP, refused);
    }

    /** A high peak at a corner of the grid changes only 2 pairs of the 12: the village can start. */
    @Test
    void peakAtACornerIsAccepted() {
        assertEquals(Result.ACCEPTED,
                VillageSite.check(LAND, LAND, SEA, (dx, dz) -> dx > 0 && dz > 0 ? LAND + 60 : LAND));
    }
}
