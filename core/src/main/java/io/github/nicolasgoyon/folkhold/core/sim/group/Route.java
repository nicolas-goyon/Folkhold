package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A list of points between two villages. A mobile group follows the route from the first point to the last point.
 *
 * <ul>
 *   <li>The progress on a route is a distance in blocks from the first point, from 0 to {@link #length()}.</li>
 *   <li>Each segment (from point i to point i + 1) has a {@link Surface}: land, ford or water.</li>
 * </ul>
 */
public final class Route {
    private final List<Point> points;
    private final List<Surface> surfaces;
    /** Distance from the first point to each point, in blocks. */
    private final double[] distances;

    /**
     * The key of the route between two settlements: the same key in both directions.
     *
     * @param firstId  the id of one settlement
     * @param secondId the id of the other settlement
     * @return the key, {@code a|b} with a before b in alphabetical order
     */
    public static String pairKey(String firstId, String secondId) {
        return firstId.compareTo(secondId) <= 0 ? firstId + "|" + secondId : secondId + "|" + firstId;
    }

    /**
     * A point of a route, in block coordinates.
     *
     * @param x block X
     * @param z block Z
     */
    public record Point(int x, int z) {
    }

    /**
     * A position on a route.
     *
     * @param x block X
     * @param z block Z
     */
    public record Position(double x, double z) {
    }

    /** The surface of a segment of a route. */
    public enum Surface {
        /** Dry ground: the group walks. */
        LAND(0),
        /** Shallow water: the group walks in the water. */
        FORD(1),
        /** Deep water: the group crosses in boats. */
        WATER(2);

        private final int code;

        Surface(int code) {
            this.code = code;
        }

        /** The code of the surface in the saved data. */
        public int code() {
            return code;
        }

        /**
         * The surface with a code.
         *
         * @param code the code
         * @return the surface, or {@link #LAND} for an unknown code
         */
        public static Surface fromCode(int code) {
            for (Surface surface : values()) {
                if (surface.code == code) {
                    return surface;
                }
            }
            return LAND;
        }
    }

    /**
     * Makes a route on land.
     *
     * @param points the points, from the origin to the destination (2 or more)
     */
    public Route(List<Point> points) {
        this(points, Collections.nCopies(Math.max(0, points.size() - 1), Surface.LAND));
    }

    /**
     * Makes a route.
     *
     * @param points   the points, from the origin to the destination (2 or more)
     * @param surfaces the surface of each segment: one less than the points
     */
    public Route(List<Point> points, List<Surface> surfaces) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A route needs 2 or more points: " + points.size());
        }
        if (surfaces.size() != points.size() - 1) {
            throw new IllegalArgumentException("A route needs one surface for each segment: " + surfaces.size());
        }
        this.points = List.copyOf(points);
        this.surfaces = List.copyOf(surfaces);
        this.distances = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            distances[i] = distances[i - 1] + distance(points.get(i - 1), points.get(i));
        }
    }

    public List<Point> points() {
        return points;
    }

    /** The surface of each segment. Segment i goes from point i to point i + 1. */
    public List<Surface> surfaces() {
        return surfaces;
    }

    /** The length of the route, in blocks. */
    public double length() {
        return distances[distances.length - 1];
    }

    /**
     * The distance from the first point to a point.
     *
     * @param index the index of the point
     * @return the progress of the point, in blocks
     */
    public double progressOf(int index) {
        return distances[index];
    }

    /**
     * The position at a progress. The progress is clamped to the route.
     *
     * @param progress the distance from the first point, in blocks
     * @return the position
     */
    public Position positionAt(double progress) {
        double clamped = Math.max(0.0, Math.min(progress, length()));
        int segment = segmentAt(clamped);
        Point from = points.get(segment);
        Point to = points.get(segment + 1);
        double segmentLength = distances[segment + 1] - distances[segment];
        double t = segmentLength <= 0.0 ? 0.0 : (clamped - distances[segment]) / segmentLength;
        return new Position(from.x() + (to.x() - from.x()) * t, from.z() + (to.z() - from.z()) * t);
    }

    /**
     * The surface at a progress. The progress is clamped to the route.
     *
     * @param progress the distance from the first point, in blocks
     * @return the surface of the segment at this progress
     */
    public Surface surfaceAt(double progress) {
        return surfaces.get(segmentAt(Math.max(0.0, Math.min(progress, length()))));
    }

    /**
     * The index of the segment that contains a progress. Segment i goes from point i to point i + 1. The progress of
     * a point is in the segment that ends at this point.
     *
     * @param progress the distance from the first point, in blocks
     * @return the index of the segment
     */
    public int segmentAt(double progress) {
        for (int i = 1; i < distances.length; i++) {
            if (progress <= distances[i]) {
                return i - 1;
            }
        }
        return distances.length - 2;
    }

    /**
     * The progress of the point of the route that is nearest to a position.
     *
     * @param x block X
     * @param z block Z
     * @return the distance from the first point, in blocks
     */
    public double progressNear(double x, double z) {
        return nearest(x, z)[0];
    }

    /**
     * The horizontal distance from a position to the route.
     *
     * @param x block X
     * @param z block Z
     * @return the distance to the nearest point of the route, in blocks
     */
    public double distanceTo(double x, double z) {
        return Math.sqrt(nearest(x, z)[1]);
    }

    /** The same route in the other direction. */
    public Route reversed() {
        List<Point> reversedPoints = new ArrayList<>(points);
        Collections.reverse(reversedPoints);
        List<Surface> reversedSurfaces = new ArrayList<>(surfaces);
        Collections.reverse(reversedSurfaces);
        return new Route(reversedPoints, reversedSurfaces);
    }

    /** The progress and the squared distance of the point of the route that is nearest to a position. */
    private double[] nearest(double x, double z) {
        double best = 0.0;
        double bestDistance = Double.MAX_VALUE;
        for (int i = 0; i < points.size() - 1; i++) {
            Point from = points.get(i);
            Point to = points.get(i + 1);
            double dx = to.x() - from.x();
            double dz = to.z() - from.z();
            double lengthSquared = dx * dx + dz * dz;
            double t = lengthSquared <= 0.0 ? 0.0
                    : Math.max(0.0, Math.min(1.0, ((x - from.x()) * dx + (z - from.z()) * dz) / lengthSquared));
            double px = from.x() + dx * t;
            double pz = from.z() + dz * t;
            double distance = (px - x) * (px - x) + (pz - z) * (pz - z);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = distances[i] + Math.sqrt(lengthSquared) * t;
            }
        }
        return new double[] {best, bestDistance};
    }

    private static double distance(Point a, Point b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
