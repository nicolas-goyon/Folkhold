package io.github.nicolasgoyon.folkhold.core.sim.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteFinderTest {
    private static final int SEA_LEVEL = 62;
    private static final int GROUND = 70;
    private static final RouteFinder.Settings SETTINGS =
            new RouteFinder.Settings(16, 2, 2.0, 1.0, 3000.0, 20_000, 1.0);

    /** Gives the floor height at a position. */
    @FunctionalInterface
    private interface Floor {
        int at(int x, int z);
    }

    /** A height source with water up to the sea level. */
    private static RouteFinder.HeightSource withSea(Floor floor) {
        return (x, z) -> {
            int height = floor.at(x, z);
            return new RouteFinder.Column(height, Math.max(0, SEA_LEVEL - height));
        };
    }

    /** Flat ground everywhere. */
    private static final RouteFinder.HeightSource FLAT = withSea((x, z) -> GROUND);

    /** A deep lake: a disk with a radius of 150 blocks around (500, 0). */
    private static final RouteFinder.HeightSource LAKE = withSea((x, z) -> {
        double dx = x - 500;
        return dx * dx + (double) z * z < 150 * 150 ? 40 : GROUND;
    });

    @Test
    void flatGroundGivesAStraightRoute() {
        RouteFinder.Result result = RouteFinder.find(0, 0, 1000, 0, FLAT, SETTINGS);

        Route route = result.route().orElseThrow();
        assertEquals(List.of(new Route.Point(0, 0), new Route.Point(1000, 0)), route.points());
        assertEquals(1000.0, route.length(), 1e-9);
    }

    @Test
    void routeEndsAtTheExactPositions() {
        Route route = RouteFinder.find(3, -7, 805, 411, FLAT, SETTINGS).route().orElseThrow();

        assertEquals(new Route.Point(3, -7), route.points().get(0));
        assertEquals(new Route.Point(805, 411), route.points().get(route.points().size() - 1));
        assertTrue(route.length() < Math.hypot(802, 418) * 1.1, "The route must stay near the direct line");
    }

    @Test
    void routeGoesAroundDeepWater() {
        Route route = RouteFinder.find(0, 0, 1000, 0, LAKE, SETTINGS).route().orElseThrow();

        for (double progress = 0.0; progress <= route.length(); progress += 4.0) {
            Route.Position position = route.positionAt(progress);
            double dx = position.x() - 500;
            assertTrue(dx * dx + position.z() * position.z() >= 140 * 140,
                    "The route must not cross the lake at " + position);
        }
        assertTrue(route.length() > 1000.0, "The route is longer than the direct line");
    }

    @Test
    void shallowWaterIsAllowed() {
        RouteFinder.HeightSource ford = withSea((x, z) -> x > 400 && x < 600 ? SEA_LEVEL - 1 : GROUND);

        Route route = RouteFinder.find(0, 0, 1000, 0, ford, SETTINGS).route().orElseThrow();

        assertEquals(2, route.points().size(), "The route crosses the ford in a straight line");
    }

    @Test
    void waterEverywhereGivesNoRoute() {
        RouteFinder.HeightSource sea = withSea((x, z) -> x > 300 && x < 700 ? 30 : GROUND);

        RouteFinder.Result result = RouteFinder.find(0, 0, 1000, 0, sea, SETTINGS);

        assertTrue(result.route().isEmpty());
        assertTrue(result.examined() <= SETTINGS.maxNodes());
    }

    @Test
    void routeLongerThanTheMaximumIsNotValid() {
        assertTrue(RouteFinder.find(0, 0, 3500, 0, FLAT, SETTINGS).route().isEmpty(),
                "The direct line is longer than the maximum");
        RouteFinder.Settings shortMax = new RouteFinder.Settings(16, 2, 2.0, 1.0, 1100.0, 20_000, 1.0);
        assertTrue(RouteFinder.find(0, 0, 1000, 0, LAKE, shortMax).route().isEmpty(),
                "The way around the lake is longer than the maximum");
    }

    @Test
    void lakeAboveTheSeaLevelIsWater() {
        RouteFinder.HeightSource highLake = (x, z) -> {
            double dx = x - 500;
            boolean lake = dx * dx + (double) z * z < 150 * 150;
            return new RouteFinder.Column(lake ? 90 : 100, lake ? 10 : 0);
        };

        Route route = RouteFinder.find(0, 0, 1000, 0, highLake, SETTINGS).route().orElseThrow();

        assertTrue(route.length() > 1000.0, "The route must go around the lake");
    }

    @Test
    void weightedHeuristicExaminesFewerCells() {
        // Rolling hills: the slope cost makes the plain search examine many cells.
        RouteFinder.HeightSource hills =
                withSea((x, z) -> GROUND + (int) (20 * Math.sin(x / 40.0) * Math.cos(z / 40.0)));
        RouteFinder.Settings weighted = new RouteFinder.Settings(16, 2, 2.0, 1.0, 3000.0, 20_000, 1.5);

        RouteFinder.Result plain = RouteFinder.find(0, 0, 2000, 300, hills, SETTINGS);
        RouteFinder.Result fast = RouteFinder.find(0, 0, 2000, 300, hills, weighted);

        assertTrue(plain.route().isPresent() && fast.route().isPresent());
        assertTrue(fast.examined() < plain.examined(),
                "The weighted search must examine fewer cells: " + fast.examined() + " vs " + plain.examined());
    }

    @Test
    void routeAvoidsAHillWhenTheValleyIsShort() {
        // A high ridge across the direct line, with a pass at z = 160.
        RouteFinder.HeightSource ridge =
                withSea((x, z) -> x > 450 && x < 550 && Math.abs(z - 160) > 32 ? 130 : GROUND);

        Route route = RouteFinder.find(0, 0, 1000, 0, ridge, SETTINGS).route().orElseThrow();

        Route.Position atRidge = route.positionAt(route.progressNear(500, 160));
        assertTrue(Math.abs(atRidge.z() - 160) <= 32, "The route must use the pass: " + atRidge);
    }
}
