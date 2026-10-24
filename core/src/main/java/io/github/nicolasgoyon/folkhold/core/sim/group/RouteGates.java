package io.github.nicolasgoyon.folkhold.core.sim.group;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import java.util.ArrayList;
import java.util.List;

/**
 * The passage of a route through a gate of the palisade of its villages.
 *
 * <p>The search of a route does not see the walls of a village: a route goes from the heart of a village in a
 * straight line. This class changes the two ends of a route. At each end, the route goes from the heart to a gate of
 * the village, through the gate to the position outside it, then to the route outside the village.
 *
 * <ul>
 *   <li>The route leaves the village at its first point that is as far from the heart as the position outside the
 *       gate. The points before it are removed.</li>
 *   <li>The selected gate gives the shortest route.</li>
 *   <li>The new segments are on land.</li>
 *   <li>A route that goes through a gate already does not change. A village with no known gates does not change the
 *       route.</li>
 * </ul>
 */
public final class RouteGates {
    /** The distance from the gate to the point before it, inside the village, in blocks. */
    static final int INSIDE = 3;

    private RouteGates() {
    }

    /**
     * The route through the gates of its two villages.
     *
     * @param route      the route, from the heart of the start to the heart of the end
     * @param startGates the gates of the village at the start
     * @param endGates   the gates of the village at the end
     * @return the route through the gates
     */
    public static Route through(Route route, List<Settlement.Gate> startGates, List<Settlement.Gate> endGates) {
        Route start = atStart(route, startGates);
        return atStart(start.reversed(), endGates).reversed();
    }

    /**
     * The route through a gate of the village at its start.
     *
     * @param route the route, from the heart of the village
     * @param gates the gates of the village
     * @return the route through the gate with the shortest route, or the same route
     */
    public static Route atStart(Route route, List<Settlement.Gate> gates) {
        List<Route.Point> points = route.points();
        if (gates.isEmpty() || passesAGate(points, gates)) {
            return route;
        }
        Route.Point heart = points.get(0);
        double best = Double.MAX_VALUE;
        Settlement.Gate bestGate = null;
        int bestIndex = 0;
        for (Settlement.Gate gate : gates) {
            Route.Point inside = inside(gate);
            Route.Point door = new Route.Point(gate.x(), gate.z());
            Route.Point out = new Route.Point(gate.outX(), gate.outZ());
            double toOut = distance(heart, inside) + distance(inside, door) + distance(door, out);
            int k = exitIndex(points, distance(heart, out));
            double cost = toOut + distance(out, points.get(k)) + route.length() - route.progressOf(k);
            if (cost < best) {
                best = cost;
                bestGate = gate;
                bestIndex = k;
            }
        }
        if (bestGate == null) {
            return route;
        }
        List<Route.Point> newPoints = new ArrayList<>();
        List<Route.Surface> newSurfaces = new ArrayList<>();
        newPoints.add(heart);
        add(newPoints, newSurfaces, inside(bestGate), Route.Surface.LAND);
        add(newPoints, newSurfaces, new Route.Point(bestGate.x(), bestGate.z()), Route.Surface.LAND);
        add(newPoints, newSurfaces, new Route.Point(bestGate.outX(), bestGate.outZ()), Route.Surface.LAND);
        add(newPoints, newSurfaces, points.get(bestIndex), Route.Surface.LAND);
        for (int k = bestIndex + 1; k < points.size(); k++) {
            add(newPoints, newSurfaces, points.get(k), route.surfaces().get(k - 1));
        }
        if (newPoints.size() < 2) {
            return route;
        }
        return new Route(newPoints, newSurfaces);
    }

    /**
     * The index of the first point of the route at a distance from the heart, or the last point: the route leaves the
     * village there.
     */
    private static int exitIndex(List<Route.Point> points, double radius) {
        Route.Point heart = points.get(0);
        for (int k = 1; k < points.size(); k++) {
            if (distance(heart, points.get(k)) >= radius) {
                return k;
            }
        }
        return points.size() - 1;
    }

    /** True if one of the first points of the route is the middle of a gate. */
    private static boolean passesAGate(List<Route.Point> points, List<Settlement.Gate> gates) {
        int limit = Math.min(points.size(), 4);
        for (int i = 1; i < limit; i++) {
            for (Settlement.Gate gate : gates) {
                if (points.get(i).x() == gate.x() && points.get(i).z() == gate.z()) {
                    return true;
                }
            }
        }
        return false;
    }

    /** The point inside the village before a gate: {@link #INSIDE} blocks from the gate, away from the outside. */
    static Route.Point inside(Settlement.Gate gate) {
        double dx = gate.outX() - gate.x();
        double dz = gate.outZ() - gate.z();
        double length = Math.hypot(dx, dz);
        if (length < 1.0E-6) {
            return new Route.Point(gate.x(), gate.z());
        }
        return new Route.Point((int) Math.round(gate.x() - dx / length * INSIDE),
                (int) Math.round(gate.z() - dz / length * INSIDE));
    }

    /** Adds a point and the surface of the segment to it. A point equal to the last point is not added. */
    private static void add(List<Route.Point> points, List<Route.Surface> surfaces, Route.Point point,
            Route.Surface surface) {
        if (points.get(points.size() - 1).equals(point)) {
            return;
        }
        points.add(point);
        surfaces.add(surface);
    }

    private static double distance(Route.Point a, Route.Point b) {
        return Math.hypot(b.x() - a.x(), b.z() - a.z());
    }
}
