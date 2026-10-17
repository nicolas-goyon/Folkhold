package io.github.nicolasgoyon.folkhold.core.sim.group;

import io.github.nicolasgoyon.folkhold.core.sim.IntPair;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.PriorityQueue;

/**
 * Finds a route between two positions on a grid (A* search).
 *
 * <ul>
 *   <li>The grid has its origin at the start position. One cell is {@code step} blocks.</li>
 *   <li>Each cell has a surface from its water depth: land (no water), ford (water of {@code fordDepth} blocks or
 *       less) or water (deeper water). A group crosses water in boats, only if {@code boats} is true.</li>
 *   <li>The cost of a move is its length, plus a cost for the slope, for the ford and for the boat, plus a fixed
 *       cost each time the group gets into or out of the boats.</li>
 *   <li>A move with a height change of more than {@code maxRise} blocks is not possible (a cliff). The first move
 *       and the move to the end are always possible: the villages are on terrain that the search does not see.</li>
 *   <li>The heuristic is the direct distance to the end, multiplied by a weight. A weight above 1 examines fewer
 *       cells, and the route can be a little longer than the best route.</li>
 *   <li>With a {@link Corridor}, the search uses only the cells near a route (for example a route on a coarse
 *       grid). So a search on a fine grid examines fewer cells.</li>
 *   <li>A route that is longer than {@code maxLength} is not valid. The search also stops after
 *       {@code maxNodes} cells.</li>
 *   <li>The result keeps only the points where the direction or the surface changes. The last point is the exact
 *       end position.</li>
 * </ul>
 */
public final class RouteFinder {
    /** No limit for the height change of a move. */
    public static final int NO_RISE_LIMIT = Integer.MAX_VALUE;

    /** The 8 directions on the grid. */
    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /**
     * The ground and the water at one position.
     *
     * @param floor the Y of the highest solid block. Water is not solid.
     * @param water the depth of the water above the floor, in blocks (0 on dry ground)
     */
    public record Column(int floor, int water) {
    }

    /** Gives the ground and the water at a position, without the generation of chunks. */
    @FunctionalInterface
    public interface HeightSource {
        /**
         * The column at a position.
         *
         * @param x block X
         * @param z block Z
         * @return the column
         */
        Column column(int x, int z);
    }

    /**
     * Settings of the search.
     *
     * @param step       size of a grid cell in blocks (1 or more)
     * @param fordDepth  maximum depth of a ford, in blocks. Deeper water needs boats.
     * @param maxRise    maximum height change of one move, in blocks ({@link #NO_RISE_LIMIT} for no limit)
     * @param slopeCost  extra cost for each block of height change
     * @param fordCost   extra cost for each block of distance in a ford
     * @param boats      true if the group can cross deep water in boats
     * @param boatCost   extra cost for each block of distance in a boat
     * @param embarkCost extra cost each time the group gets into or out of the boats
     * @param maxLength  maximum length of a route in blocks
     * @param maxNodes   maximum number of cells that the search examines
     * @param weight     weight of the heuristic (1 or more)
     */
    public record Settings(int step, int fordDepth, int maxRise, double slopeCost, double fordCost, boolean boats,
            double boatCost, double embarkCost, double maxLength, int maxNodes, double weight) {
        public Settings {
            if (step < 1 || fordDepth < 0 || maxRise < 0 || slopeCost < 0 || fordCost < 0 || boatCost < 0
                    || embarkCost < 0 || maxLength <= 0 || maxNodes < 1 || weight < 1.0) {
                throw new IllegalArgumentException("Invalid route settings");
            }
        }
    }

    /**
     * The area of a search: the cells at a distance of {@code width} blocks or less from a route.
     *
     * @param route the route
     * @param width the maximum distance from the route, in blocks
     */
    public record Corridor(Route route, double width) {
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
     * Finds a route in all the grid.
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
        return find(startX, startZ, endX, endZ, heights, settings, null);
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
     * @param corridor the area of the search, or null for all the grid
     * @return the result
     */
    public static Result find(int startX, int startZ, int endX, int endZ, HeightSource heights, Settings settings,
            Corridor corridor) {
        double direct = Math.hypot(endX - startX, endZ - startZ);
        if (direct > settings.maxLength()) {
            return new Result(Optional.empty(), 0);
        }
        Grid grid = new Grid(startX, startZ, settings, heights, corridor);
        int goalI = Math.round((endX - startX) / (float) settings.step());
        int goalJ = Math.round((endZ - startZ) / (float) settings.step());
        Map<Long, Double> bestCost = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate(), b.estimate()));
        long startKey = IntPair.pack(0, 0);
        bestCost.put(startKey, 0.0);
        open.add(new Node(0, 0, 0.0, settings.weight() * heuristic(0, 0, goalI, goalJ, settings.step())));
        int examined = 0;
        while (!open.isEmpty() && examined < settings.maxNodes()) {
            Node node = open.poll();
            long nodeKey = IntPair.pack(node.i(), node.j());
            if (node.cost() > bestCost.getOrDefault(nodeKey, Double.MAX_VALUE)) {
                continue;
            }
            examined++;
            if (node.i() == goalI && node.j() == goalJ) {
                return new Result(build(nodeKey, parent, grid, endX, endZ, settings), examined);
            }
            boolean fromStart = nodeKey == startKey;
            for (int[] direction : DIRECTIONS) {
                int ni = node.i() + direction[0];
                int nj = node.j() + direction[1];
                boolean goal = ni == goalI && nj == goalJ;
                double moveCost = grid.moveCost(node.i(), node.j(), ni, nj, fromStart || goal);
                if (moveCost < 0.0) {
                    continue;
                }
                double cost = node.cost() + moveCost;
                long neighborKey = IntPair.pack(ni, nj);
                if (cost < bestCost.getOrDefault(neighborKey, Double.MAX_VALUE)) {
                    bestCost.put(neighborKey, cost);
                    parent.put(neighborKey, nodeKey);
                    open.add(new Node(ni, nj, cost,
                            cost + settings.weight() * heuristic(ni, nj, goalI, goalJ, settings.step())));
                }
            }
        }
        return new Result(Optional.empty(), examined);
    }

    /**
     * Finds a route on a fine grid near a coarse route. If the corridor has no route, the search tries again with a
     * corridor two times wider.
     *
     * @param startX   block X of the start
     * @param startZ   block Z of the start
     * @param endX     block X of the end
     * @param endZ     block Z of the end
     * @param coarse   the coarse route
     * @param width    the width of the first corridor, in blocks
     * @param heights  the height source
     * @param settings the settings of the fine grid
     * @return the result. The number of examined cells is the total of the searches.
     */
    public static Result refine(int startX, int startZ, int endX, int endZ, Route coarse, double width,
            HeightSource heights, Settings settings) {
        Result first = find(startX, startZ, endX, endZ, heights, settings, new Corridor(coarse, width));
        if (first.route().isPresent()) {
            return first;
        }
        Result second = find(startX, startZ, endX, endZ, heights, settings, new Corridor(coarse, width * 2.0));
        return new Result(second.route(), first.examined() + second.examined());
    }

    /** The surface of a column. */
    static Route.Surface surface(Column column, Settings settings) {
        if (column.water() <= 0) {
            return Route.Surface.LAND;
        }
        return column.water() <= settings.fordDepth() ? Route.Surface.FORD : Route.Surface.WATER;
    }

    /** The surface of a move between two cells: water only from boat to boat, ford if one cell is a ford. */
    private static Route.Surface moveSurface(Route.Surface from, Route.Surface to) {
        if (from == Route.Surface.WATER && to == Route.Surface.WATER) {
            return Route.Surface.WATER;
        }
        if (from == Route.Surface.FORD || to == Route.Surface.FORD) {
            return Route.Surface.FORD;
        }
        return Route.Surface.LAND;
    }

    private static Optional<Route> build(long goalKey, Map<Long, Long> parent, Grid grid, int endX, int endZ,
            Settings settings) {
        List<long[]> cells = new ArrayList<>();
        Long current = goalKey;
        while (current != null) {
            cells.add(new long[] {IntPair.first(current), IntPair.second(current)});
            current = parent.get(current);
        }
        Collections.reverse(cells);
        List<Route.Point> points = new ArrayList<>();
        List<Route.Surface> surfaces = new ArrayList<>();
        points.add(new Route.Point(grid.startX, grid.startZ));
        if (cells.size() < 2) {
            points.add(new Route.Point(endX, endZ));
            surfaces.add(Route.Surface.LAND);
            return Optional.of(new Route(points, surfaces));
        }
        int step = settings.step();
        Route.Surface previousSurface = null;
        for (int index = 1; index < cells.size(); index++) {
            long[] previous = cells.get(index - 1);
            long[] cell = cells.get(index);
            Route.Surface moveSurface = moveSurface(grid.surface((int) previous[0], (int) previous[1]),
                    grid.surface((int) cell[0], (int) cell[1]));
            if (index > 1) {
                long[] before = cells.get(index - 2);
                boolean turn = cell[0] - previous[0] != previous[0] - before[0]
                        || cell[1] - previous[1] != previous[1] - before[1];
                if (turn || moveSurface != previousSurface) {
                    points.add(new Route.Point(grid.startX + (int) previous[0] * step,
                            grid.startZ + (int) previous[1] * step));
                    surfaces.add(previousSurface);
                }
            }
            previousSurface = moveSurface;
        }
        points.add(new Route.Point(endX, endZ));
        surfaces.add(previousSurface);
        Route route = new Route(points, surfaces);
        return route.length() <= settings.maxLength() ? Optional.of(route) : Optional.empty();
    }

    private static double heuristic(int i, int j, int goalI, int goalJ, int step) {
        return step * Math.hypot(goalI - i, goalJ - j);
    }

    /** The cells of one search, with their columns and their place in the corridor. */
    private static final class Grid {
        private final int startX;
        private final int startZ;
        private final Settings settings;
        private final HeightSource heights;
        private final Corridor corridor;
        private final Map<Long, Column> columns = new HashMap<>();
        private final Map<Long, Boolean> inCorridor = new HashMap<>();

        Grid(int startX, int startZ, Settings settings, HeightSource heights, Corridor corridor) {
            this.startX = startX;
            this.startZ = startZ;
            this.settings = settings;
            this.heights = heights;
            this.corridor = corridor;
        }

        Column column(int i, int j) {
            return columns.computeIfAbsent(IntPair.pack(i, j),
                    k -> heights.column(startX + i * settings.step(), startZ + j * settings.step()));
        }

        Route.Surface surface(int i, int j) {
            return RouteFinder.surface(column(i, j), settings);
        }

        boolean inCorridor(int i, int j) {
            if (corridor == null) {
                return true;
            }
            return inCorridor.computeIfAbsent(IntPair.pack(i, j), k -> corridor.route().distanceTo(
                    startX + i * settings.step(), startZ + j * settings.step()) <= corridor.width());
        }

        /** The height where a group stands in a cell: the floor, or the water surface for a boat. */
        int standHeight(Column column) {
            return RouteFinder.surface(column, settings) == Route.Surface.WATER
                    ? column.floor() + column.water() : column.floor();
        }

        /**
         * The cost of a move, or a negative value if the move is not possible.
         *
         * @param free true for the first move and the move to the end: no limits for the corridor, the water and
         *             the height change
         */
        double moveCost(int fromI, int fromJ, int toI, int toJ, boolean free) {
            if (!free && !inCorridor(toI, toJ)) {
                return -1.0;
            }
            Column from = column(fromI, fromJ);
            Column to = column(toI, toJ);
            Route.Surface fromSurface = RouteFinder.surface(from, settings);
            Route.Surface toSurface = RouteFinder.surface(to, settings);
            if (toSurface == Route.Surface.WATER && !settings.boats() && !free) {
                return -1.0;
            }
            int rise = Math.abs(standHeight(to) - standHeight(from));
            if (rise > settings.maxRise() && !free) {
                return -1.0;
            }
            double length = settings.step() * Math.hypot(toI - fromI, toJ - fromJ);
            Route.Surface surface = moveSurface(fromSurface, toSurface);
            double cost = length;
            if (surface == Route.Surface.WATER) {
                cost += settings.boatCost() * length;
            } else {
                cost += settings.slopeCost() * rise;
                if (surface == Route.Surface.FORD || toSurface == Route.Surface.WATER
                        || fromSurface == Route.Surface.WATER) {
                    cost += settings.fordCost() * length;
                }
            }
            if ((fromSurface == Route.Surface.WATER) != (toSurface == Route.Surface.WATER)) {
                cost += settings.embarkCost();
            }
            return cost;
        }
    }
}
