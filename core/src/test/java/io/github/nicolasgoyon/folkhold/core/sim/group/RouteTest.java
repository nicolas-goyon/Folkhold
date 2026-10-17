package io.github.nicolasgoyon.folkhold.core.sim.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteTest {
    private static final double EPSILON = 1e-9;

    /** An L-shaped route: 30 blocks to the east, then 40 blocks to the south. */
    private static Route lRoute() {
        return new Route(List.of(new Route.Point(0, 0), new Route.Point(30, 0), new Route.Point(30, 40)));
    }

    @Test
    void lengthIsTheSumOfTheSegments() {
        assertEquals(70.0, lRoute().length(), EPSILON);
        assertEquals(5.0, new Route(List.of(new Route.Point(0, 0), new Route.Point(3, 4))).length(), EPSILON);
    }

    @Test
    void positionInterpolatesOnTheSegments() {
        Route route = lRoute();
        assertEquals(new Route.Position(0.0, 0.0), route.positionAt(0.0));
        assertEquals(new Route.Position(15.0, 0.0), route.positionAt(15.0));
        assertEquals(new Route.Position(30.0, 0.0), route.positionAt(30.0));
        assertEquals(new Route.Position(30.0, 20.0), route.positionAt(50.0));
        assertEquals(new Route.Position(30.0, 40.0), route.positionAt(70.0));
    }

    @Test
    void positionIsClampedToTheRoute() {
        Route route = lRoute();
        assertEquals(new Route.Position(0.0, 0.0), route.positionAt(-10.0));
        assertEquals(new Route.Position(30.0, 40.0), route.positionAt(500.0));
    }

    @Test
    void progressNearFindsTheNearestPointOfTheRoute() {
        Route route = lRoute();
        assertEquals(10.0, route.progressNear(10.0, -5.0), EPSILON);
        assertEquals(50.0, route.progressNear(35.0, 20.0), EPSILON);
        assertEquals(0.0, route.progressNear(-20.0, 0.0), EPSILON);
        assertEquals(70.0, route.progressNear(30.0, 90.0), EPSILON);
    }

    @Test
    void progressNearIsTheInverseOfPositionAt() {
        Route route = lRoute();
        for (double progress = 0.0; progress <= route.length(); progress += 7.0) {
            Route.Position position = route.positionAt(progress);
            assertEquals(progress, route.progressNear(position.x(), position.z()), EPSILON);
        }
    }

    @Test
    void routeNeedsTwoPoints() {
        assertThrows(IllegalArgumentException.class, () -> new Route(List.of(new Route.Point(0, 0))));
    }

    @Test
    void surfacesFollowTheSegments() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(30, 0), new Route.Point(30, 40)),
                List.of(Route.Surface.LAND, Route.Surface.WATER));

        assertEquals(Route.Surface.LAND, route.surfaceAt(10.0));
        assertEquals(Route.Surface.LAND, route.surfaceAt(30.0));
        assertEquals(Route.Surface.WATER, route.surfaceAt(31.0));
        assertEquals(Route.Surface.WATER, route.surfaceAt(500.0));
        assertEquals(List.of(Route.Surface.LAND, Route.Surface.LAND), lRoute().surfaces());
    }

    @Test
    void reversedRouteHasTheSurfacesInTheOtherOrder() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(30, 0), new Route.Point(30, 40)),
                List.of(Route.Surface.LAND, Route.Surface.WATER));

        Route reversed = route.reversed();

        assertEquals(List.of(new Route.Point(30, 40), new Route.Point(30, 0), new Route.Point(0, 0)),
                reversed.points());
        assertEquals(List.of(Route.Surface.WATER, Route.Surface.LAND), reversed.surfaces());
        assertEquals(route.length(), reversed.length(), EPSILON);
    }

    @Test
    void distanceToTheRoute() {
        Route route = lRoute();

        assertEquals(0.0, route.distanceTo(15, 0), EPSILON);
        assertEquals(5.0, route.distanceTo(15, -5), EPSILON);
        assertEquals(10.0, route.distanceTo(40, 20), EPSILON);
        assertEquals(30.0, route.progressOf(1), EPSILON);
    }

    @Test
    void routeNeedsOneSurfaceForEachSegment() {
        assertThrows(IllegalArgumentException.class, () -> new Route(
                List.of(new Route.Point(0, 0), new Route.Point(3, 4)), List.of()));
    }

    @Test
    void surfaceCodesComeBack() {
        for (Route.Surface surface : Route.Surface.values()) {
            assertEquals(surface, Route.Surface.fromCode(surface.code()));
        }
        assertEquals(Route.Surface.LAND, Route.Surface.fromCode(99));
    }
}
