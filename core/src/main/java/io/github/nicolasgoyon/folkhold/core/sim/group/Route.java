package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.List;

/**
 * A list of points between two villages. A mobile group follows the route from the first point to the last point.
 *
 * <p>The progress on a route is a distance in blocks from the first point, from 0 to {@link #length()}.
 */
public final class Route {
    private final List<Point> points;
    /** Distance from the first point to each point, in blocks. */
    private final double[] distances;

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

    /**
     * Makes a route.
     *
     * @param points the points, from the origin to the destination (2 or more)
     */
    public Route(List<Point> points) {
        if (points.size() < 2) {
            throw new IllegalArgumentException("A route needs 2 or more points: " + points.size());
        }
        this.points = List.copyOf(points);
        this.distances = new double[points.size()];
        for (int i = 1; i < points.size(); i++) {
            distances[i] = distances[i - 1] + distance(points.get(i - 1), points.get(i));
        }
    }

    /** The points, from the origin to the destination. */
    public List<Point> points() {
        return points;
    }

    /** The length of the route, in blocks. */
    public double length() {
        return distances[distances.length - 1];
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
     * The progress of the point of the route that is nearest to a position.
     *
     * @param x block X
     * @param z block Z
     * @return the distance from the first point, in blocks
     */
    public double progressNear(double x, double z) {
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
        return best;
    }

    /** The index of the segment that contains a progress. Segment i goes from point i to point i + 1. */
    private int segmentAt(double progress) {
        for (int i = 1; i < distances.length; i++) {
            if (progress <= distances[i]) {
                return i - 1;
            }
        }
        return distances.length - 2;
    }

    private static double distance(Point a, Point b) {
        double dx = b.x() - a.x();
        double dz = b.z() - a.z();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
