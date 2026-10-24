package io.github.nicolasgoyon.folkhold.core.sim.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class RouteFinderTest {
    private static final int SEA_LEVEL = 62;
    private static final int GROUND = 70;
    private static final RouteFinder.Settings SETTINGS = settings(16, RouteFinder.NO_RISE_LIMIT, false, 3000.0, 1.0);

    /** Settings with a slope cost of 2, a ford cost of 1, a boat cost of 0.5 and an embark cost of 40. */
    private static RouteFinder.Settings settings(int step, int maxRise, boolean boats, double maxLength,
            double weight) {
        return new RouteFinder.Settings(step, 2, maxRise, 2.0, 1.0, boats, 0.5, 40.0, maxLength, 20_000, weight);
    }

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

        assertTrue(route.points().stream().allMatch(point -> point.z() == 0),
                "The route crosses the ford in a straight line: " + route.points());
        assertEquals(Route.Surface.FORD, route.surfaceAt(500.0));
        assertEquals(Route.Surface.LAND, route.surfaceAt(100.0));
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
        RouteFinder.Settings shortMax = settings(16, RouteFinder.NO_RISE_LIMIT, false, 1100.0, 1.0);
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
        RouteFinder.Settings weighted = settings(16, RouteFinder.NO_RISE_LIMIT, false, 3000.0, 1.5);

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

    @Test
    void boatsCrossTheSea() {
        RouteFinder.HeightSource sea = withSea((x, z) -> x > 300 && x < 700 ? 30 : GROUND);

        Route route = RouteFinder.find(0, 0, 1000, 0, sea, settings(16, RouteFinder.NO_RISE_LIMIT, true, 3000.0, 1.0))
                .route().orElseThrow();

        assertEquals(Route.Surface.WATER, route.surfaceAt(route.progressNear(500, 0)),
                "The route must cross the sea in boats: " + route.points());
        assertEquals(Route.Surface.LAND, route.surfaceAt(100.0));
        assertEquals(Route.Surface.LAND, route.surfaceAt(route.length() - 100.0));
        assertTrue(route.length() < 1050.0, "The route goes across the sea: " + route.length());
    }

    @Test
    void boatsGoAroundASmallLake() {
        Route route = RouteFinder.find(0, 0, 1000, 0, LAKE, settings(16, RouteFinder.NO_RISE_LIMIT, true, 3000.0, 1.0))
                .route().orElseThrow();

        assertTrue(route.surfaces().stream().noneMatch(surface -> surface == Route.Surface.WATER),
                "The way around the lake costs less than the boats: " + route.surfaces());
    }

    @Test
    void cliffIsNotPassable() {
        // A cliff of 20 blocks at x = 200, with a ramp between z = 100 and z = 140.
        RouteFinder.HeightSource cliff = withSea((x, z) -> {
            if (Math.abs(z - 120) <= 20) {
                return GROUND + Math.max(0, Math.min(20, (x - 180) / 4));
            }
            return x < 200 ? GROUND : GROUND + 20;
        });

        Route free = RouteFinder.find(0, 0, 400, 0, cliff, settings(4, RouteFinder.NO_RISE_LIMIT, false, 3000.0, 1.0))
                .route().orElseThrow();
        Route limited = RouteFinder.find(0, 0, 400, 0, cliff, settings(4, 3, false, 3000.0, 1.0))
                .route().orElseThrow();

        Route.Position freeAtCliff = free.positionAt(free.progressNear(200, 0));
        assertTrue(Math.abs(freeAtCliff.z()) < 20, "With no limit, the route climbs the cliff: " + freeAtCliff);
        double atCliff = 0.0;
        for (double progress = 0.0; progress <= limited.length(); progress += 1.0) {
            Route.Position position = limited.positionAt(progress);
            if (Math.abs(position.x() - 200) < 2.0) {
                atCliff = position.z();
            }
        }
        assertTrue(atCliff >= 96 && atCliff <= 144, "The route must use the ramp, not the cliff: z = " + atCliff);
    }

    @Test
    void corridorKeepsTheRouteNearTheCoarseRoute() {
        Route coarse = new Route(List.of(new Route.Point(0, 0), new Route.Point(200, 200), new Route.Point(400, 0)));
        RouteFinder.Settings fine = settings(4, 3, false, 3000.0, 1.0);

        RouteFinder.Result result = RouteFinder.find(0, 0, 400, 0, FLAT, fine, new RouteFinder.Corridor(coarse, 24));

        Route route = result.route().orElseThrow();
        for (double progress = 0.0; progress <= route.length(); progress += 2.0) {
            Route.Position position = route.positionAt(progress);
            assertTrue(coarse.distanceTo(position.x(), position.z()) <= 24 + 4,
                    "The route must stay in the corridor at " + position);
        }
    }

    @Test
    void refineFindsAFineRouteAroundTheLake() {
        Route coarse = RouteFinder.find(0, 0, 1000, 0, LAKE, SETTINGS).route().orElseThrow();
        RouteFinder.Settings fine = settings(4, 3, false, 3000.0, 1.2);

        RouteFinder.Result result = RouteFinder.refine(0, 0, 1000, 0, coarse, 32, LAKE, fine);

        Route route = result.route().orElseThrow();
        assertEquals(new Route.Point(1000, 0), route.points().get(route.points().size() - 1));
        for (double progress = 0.0; progress <= route.length(); progress += 4.0) {
            Route.Position position = route.positionAt(progress);
            double dx = position.x() - 500;
            assertTrue(dx * dx + position.z() * position.z() >= 145 * 145,
                    "The fine route must not cross the lake at " + position);
        }
    }

    @Test
    void refineWithNoWayGivesNoRoute() {
        RouteFinder.HeightSource wall = withSea((x, z) -> x > 300 && x < 320 ? GROUND + 50 : GROUND);
        Route coarse = new Route(List.of(new Route.Point(0, 0), new Route.Point(1000, 0)));

        RouteFinder.Result result = RouteFinder.refine(0, 0, 1000, 0, coarse, 32, wall,
                settings(4, 3, false, 3000.0, 1.0));

        assertTrue(result.route().isEmpty(), "A wall across the corridor gives no route");
        assertTrue(result.examined() > 0);
    }

    /** Fine settings with a road factor: maximum rise 3, no boats. */
    private static RouteFinder.Settings roadSettings(double roadFactor) {
        return new RouteFinder.Settings(4, 2, 3, 2.0, 1.0, false, 0.5, 40.0, 3000.0, 200_000, 1.0, roadFactor);
    }

    /** Adds roads to a height source: a road at the given height where the predicate is true. */
    private static RouteFinder.HeightSource withRoad(RouteFinder.HeightSource terrain, Floor road) {
        return (x, z) -> {
            RouteFinder.Column column = terrain.column(x, z);
            int y = road.at(x, z);
            return y == RouteFinder.NO_ROAD ? column : new RouteFinder.Column(column.floor(), column.water(), y);
        };
    }

    private static double surfaceLength(Route route, Route.Surface surface) {
        double length = 0.0;
        for (int i = 0; i < route.surfaces().size(); i++) {
            if (route.surfaces().get(i) == surface) {
                length += route.progressOf(i + 1) - route.progressOf(i);
            }
        }
        return length;
    }

    /** A deep river across the whole map: with no boats, only a bridge with a road gives a route. */
    @Test
    void bridgeCrossesDeepWater() {
        RouteFinder.HeightSource river = withSea((x, z) -> x > 400 && x < 600 ? 40 : GROUND);
        RouteFinder.HeightSource bridge = withRoad(river,
                (x, z) -> x > 380 && x < 620 && Math.abs(z - 40) <= 4 ? GROUND : RouteFinder.NO_ROAD);

        assertTrue(RouteFinder.find(0, 0, 1000, 0, river, roadSettings(0.7)).route().isEmpty(),
                "With no boats and no bridge, the river stops the route");
        Route route = RouteFinder.find(0, 0, 1000, 0, bridge, roadSettings(0.7)).route().orElseThrow();
        Route.Position middle = route.positionAt(route.progressNear(500, 40));
        assertTrue(Math.abs(middle.z() - 40) <= 6, "The route must cross on the bridge: " + middle);
        assertEquals(0.0, surfaceLength(route, Route.Surface.WATER), 1e-9);
        assertTrue(surfaceLength(route, Route.Surface.ROAD) >= 200.0, "The bridge is a road");
    }

    /** A ridge with cliffs across the whole map: a tunnel with a road floor goes through it. */
    @Test
    void tunnelGoesThroughARidge() {
        RouteFinder.HeightSource ridge = withSea((x, z) -> x > 400 && x < 600 ? GROUND + 60 : GROUND);
        RouteFinder.HeightSource tunnel = withRoad(ridge,
                (x, z) -> x > 390 && x < 610 && Math.abs(z) <= 4 ? GROUND : RouteFinder.NO_ROAD);

        assertTrue(RouteFinder.find(0, 0, 1000, 0, ridge, roadSettings(0.7)).route().isEmpty(),
                "The cliffs stop the route");
        Route route = RouteFinder.find(0, 0, 1000, 0, tunnel, roadSettings(0.7)).route().orElseThrow();
        assertTrue(route.length() < 1010.0, "The route goes straight through the tunnel: " + route.length());
        assertTrue(surfaceLength(route, Route.Surface.ROAD) >= 200.0, "The tunnel is a road");
    }

    /** A road near the direct line: the route takes it, because a move on a road costs less. */
    @Test
    void routeTakesANearRoad() {
        RouteFinder.HeightSource road = withRoad(FLAT,
                (x, z) -> Math.abs(z - 12) <= 2 ? GROUND : RouteFinder.NO_ROAD);

        Route route = RouteFinder.find(0, 0, 1000, 0, road, roadSettings(0.7)).route().orElseThrow();

        assertTrue(surfaceLength(route, Route.Surface.ROAD) > 900.0,
                "The route must follow the road: " + surfaceLength(route, Route.Surface.ROAD));
    }

    /** A road far below the terrain (a mine) does not change a route on the terrain. */
    @Test
    void deepRoadWithNoEntranceIsNotUsed() {
        RouteFinder.HeightSource mine = withRoad(FLAT, (x, z) -> x > 300 && x < 700 ? GROUND - 40 : RouteFinder.NO_ROAD);

        Route route = RouteFinder.find(0, 0, 1000, 0, mine, roadSettings(0.7)).route().orElseThrow();

        assertEquals(0.0, surfaceLength(route, Route.Surface.ROAD), 1e-9);
        assertEquals(1000.0, route.length(), 1e-9);
    }
}
