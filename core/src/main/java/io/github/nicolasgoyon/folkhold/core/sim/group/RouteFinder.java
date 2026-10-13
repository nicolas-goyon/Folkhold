package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Finds a route between two positions on a coarse grid (A* search).
 *
 * <ul>
 *   <li>The grid has its origin at the start position. One cell is {@code step} blocks.</li>
 *   <li>The cost of a move is its length, plus a cost for the slope and a cost for shallow water.</li>
 *   <li>The route does not go into water that is deeper than {@code maxWaterDepth}.</li>
 *   <li>A route that is longer than {@code maxLength} is not valid. The search also stops after
 *       {@code maxNodes} cells.</li>
 *   <li>The result keeps only the points where the direction changes. The last point is the exact end
 *       position.</li>
 * </ul>
 */
public final class RouteFinder {
    /** The 8 directions on the grid. */
    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /** Gives the height of the ground under the water at a position, without the generation of chunks. */
    @FunctionalInterface
    public interface HeightSource {
        /**
         * The Y of the highest solid block at a position. Water is not solid.
         *
         * @param x block X
         * @param z block Z
         * @return block Y
         */
        int floorHeight(int x, int z);
    }

    /**
     * Settings of the search.
     *
     * @param step          size of a grid cell in blocks (1 or more)
     * @param seaLevel      the Y of the water surface
     * @param maxWaterDepth maximum depth of water on the route, in blocks (0: no water)
     * @param slopeCost     extra cost for each block of height change, for each block of distance
     * @param waterCost     extra cost for each block of distance in shallow water
     * @param maxLength     maximum length of a route in blocks
     * @param maxNodes      maximum number of cells that the search examines
     */
    public record Settings(int step, int seaLevel, int maxWaterDepth, double slopeCost, double waterCost,
            double maxLength, int maxNodes) {
        public Settings {
            if (step < 1 || maxWaterDepth < 0 || slopeCost < 0 || waterCost < 0 || maxLength <= 0 || maxNodes < 1) {
                throw new IllegalArgumentException("Invalid route settings");
            }
        }
    }

    /**
     * Result of a search.
     *
     * @param route    the route, or an empty value if there is no valid route
     * @param examined the number of cells that the search examined
     */
    public record Result(Optional<Route> route, int examined) {
    }

    private record Node(int i, int j, double cost, double estimate) {
    }

    private RouteFinder() {
    }

    /**
     * Finds a route.
     *
     * @param startX   block X of the start
     * @param startZ   block Z of the start
     * @param endX     block X of the end
     * @param endZ     block Z of the end
     * @param heights  the height source
     * @param settings the settings
     * @return the result
     */
    public static Result find(int startX, int startZ, int endX, int endZ, HeightSource heights, Settings settings) {
        double direct = Math.hypot(endX - startX, endZ - startZ);
        if (direct > settings.maxLength()) {
            return new Result(Optional.empty(), 0);
        }
        int step = settings.step();
        int goalI = Math.round((endX - startX) / (float) step);
        int goalJ = Math.round((endZ - startZ) / (float) step);
        Map<Long, Integer> heightCache = new HashMap<>();
        Map<Long, Double> bestCost = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate(), b.estimate()));
        long startKey = key(0, 0);
        bestCost.put(startKey, 0.0);
        open.add(new Node(0, 0, 0.0, heuristic(0, 0, goalI, goalJ, step)));
        int examined = 0;
        while (!open.isEmpty() && examined < settings.maxNodes()) {
            Node node = open.poll();
            long nodeKey = key(node.i(), node.j());
            if (node.cost() > bestCost.getOrDefault(nodeKey, Double.MAX_VALUE)) {
                continue;
            }
            examined++;
            if (node.i() == goalI && node.j() == goalJ) {
                return new Result(build(nodeKey, parent, startX, startZ, endX, endZ, step, settings), examined);
            }
            int fromFloor = height(node.i(), node.j(), startX, startZ, step, heights, heightCache);
            for (int[] direction : DIRECTIONS) {
                int ni = node.i() + direction[0];
                int nj = node.j() + direction[1];
                boolean goal = ni == goalI && nj == goalJ;
                int toFloor = height(ni, nj, startX, startZ, step, heights, heightCache);
                int depth = Math.max(0, settings.seaLevel() - toFloor);
                if (depth > settings.maxWaterDepth() && !goal) {
                    continue;
                }
                double length = step * Math.hypot(direction[0], direction[1]);
                double cost = node.cost() + length
                        + settings.slopeCost() * Math.abs(toFloor - fromFloor)
                        + (depth > 0 ? settings.waterCost() * length : 0.0);
                long neighborKey = key(ni, nj);
                if (cost < bestCost.getOrDefault(neighborKey, Double.MAX_VALUE)) {
                    bestCost.put(neighborKey, cost);
                    parent.put(neighborKey, nodeKey);
                    open.add(new Node(ni, nj, cost, cost + heuristic(ni, nj, goalI, goalJ, step)));
                }
            }
        }
        return new Result(Optional.empty(), examined);
    }

    private static Optional<Route> build(long goalKey, Map<Long, Long> parent, int startX, int startZ, int endX,
            int endZ, int step, Settings settings) {
        List<long[]> cells = new ArrayList<>();
        Long current = goalKey;
        while (current != null) {
            cells.add(new long[] {cellI(current), cellJ(current)});
            current = parent.get(current);
        }
        Collections.reverse(cells);
        List<Route.Point> points = new ArrayList<>();
        points.add(new Route.Point(startX, startZ));
        for (int index = 1; index < cells.size() - 1; index++) {
            long[] previous = cells.get(index - 1);
            long[] cell = cells.get(index);
            long[] next = cells.get(index + 1);
            boolean turn = cell[0] - previous[0] != next[0] - cell[0] || cell[1] - previous[1] != next[1] - cell[1];
            if (turn) {
                points.add(new Route.Point(startX + (int) cell[0] * step, startZ + (int) cell[1] * step));
            }
        }
        points.add(new Route.Point(endX, endZ));
        Route route = new Route(points);
        return route.length() <= settings.maxLength() ? Optional.of(route) : Optional.empty();
    }

    private static int height(int i, int j, int startX, int startZ, int step, HeightSource heights,
            Map<Long, Integer> cache) {
        return cache.computeIfAbsent(key(i, j), k -> heights.floorHeight(startX + i * step, startZ + j * step));
    }

    private static double heuristic(int i, int j, int goalI, int goalJ, int step) {
        return step * Math.hypot(goalI - i, goalJ - j);
    }

    private static long key(int i, int j) {
        return ((long) i << 32) | (j & 0xFFFFFFFFL);
    }

    private static int cellI(long key) {
        return (int) (key >> 32);
    }

    private static int cellJ(long key) {
        return (int) key;
    }
}
