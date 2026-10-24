package io.github.nicolasgoyon.folkhold.core.sim.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import java.util.List;
import org.junit.jupiter.api.Test;

class RouteGatesTest {
    /** The gate to the east of a village at (0, 0): the middle at X 20, the outside 8 blocks further. */
    private static final Settlement.Gate EAST = new Settlement.Gate(20, 0, 28, 0);
    /** The gate to the north of a village at (0, 0). */
    private static final Settlement.Gate NORTH = new Settlement.Gate(0, -20, 0, -28);

    /** A route from (0, 0) to the south-east, in steps of 4 blocks, with a road segment at the end. */
    private static Route route() {
        List<Route.Point> points = List.of(new Route.Point(0, 0), new Route.Point(12, 12), new Route.Point(24, 24),
                new Route.Point(48, 48), new Route.Point(100, 100));
        return new Route(points, List.of(Route.Surface.LAND, Route.Surface.LAND, Route.Surface.FORD,
                Route.Surface.ROAD));
    }

    @Test
    void routeGoesThroughTheNearestGate() {
        Route gated = RouteGates.atStart(route(), List.of(NORTH, EAST));
        List<Route.Point> points = gated.points();
        assertEquals(new Route.Point(0, 0), points.get(0), "The route starts at the heart");
        assertEquals(RouteGates.inside(EAST), points.get(1), "Then the point inside the gate");
        assertEquals(new Route.Point(20, 0), points.get(2), "Then the gate to the east");
        assertEquals(new Route.Point(28, 0), points.get(3), "Then the outside of the gate");
        assertEquals(new Route.Point(100, 100), points.get(points.size() - 1), "The end does not change");
        assertEquals(points.size() - 1, gated.surfaces().size());
        assertEquals(Route.Surface.ROAD, gated.surfaces().get(gated.surfaces().size() - 1),
                "The segments outside the village keep their surface");
    }

    @Test
    void pointsInsideTheVillageAreRemoved() {
        Route gated = RouteGates.atStart(route(), List.of(EAST));
        for (Route.Point point : gated.points().subList(4, gated.points().size())) {
            assertTrue(Math.hypot(point.x(), point.z()) >= 28, "No point inside the village after the gate: " + point);
        }
    }

    @Test
    void bothEndsGoThroughTheirGates() {
        Settlement.Gate west = new Settlement.Gate(80, 100, 72, 100);
        Route gated = RouteGates.through(route(), List.of(EAST), List.of(west));
        List<Route.Point> points = gated.points();
        assertEquals(new Route.Point(20, 0), points.get(2), "The gate of the start");
        assertEquals(new Route.Point(80, 100), points.get(points.size() - 3), "The gate of the end");
        assertEquals(new Route.Point(100, 100), points.get(points.size() - 1), "The route ends at the heart");
    }

    @Test
    void routeThroughAGateDoesNotChange() {
        Route gated = RouteGates.atStart(route(), List.of(EAST));
        assertSame(gated, RouteGates.atStart(gated, List.of(EAST)));
    }

    @Test
    void villageWithNoGatesDoesNotChangeTheRoute() {
        Route route = route();
        assertSame(route, RouteGates.atStart(route, List.of()));
    }
}
