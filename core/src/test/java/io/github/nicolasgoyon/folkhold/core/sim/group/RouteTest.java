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
}
