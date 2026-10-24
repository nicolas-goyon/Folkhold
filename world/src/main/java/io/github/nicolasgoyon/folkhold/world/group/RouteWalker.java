package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The walk of a caravan on the blocks along a part of its route, as the path finder of the mobs: one block at a time,
 * with valid steps only. The line of the route in the journal follows this walk (see
 * {@link io.github.nicolasgoyon.folkhold.world.client.ClientRouteOverlay}).
 *
 * <ul>
 *   <li>A position (the block of the feet) is standable when the merchant stands on a block with collision (at most
 *       one block high), on a low block in the feet (a slab, a carpet, snow), or on the surface of the water (it swims
 *       or takes a boat). The two blocks of the body are free: no collision and no fluid at the head.</li>
 *   <li>Fence gates and doors are free: the merchant opens them. Leaves are not free, and the merchant does not stand
 *       on them.</li>
 *   <li>A move goes to one of the 8 blocks around: up by one block at most (with free space for the jump), or down by
 *       {@link #MAX_DROP} blocks at most. A diagonal move needs the two blocks at its sides free: no corner through a
 *       wall.</li>
 *   <li>The search (A*) stays in a corridor of {@link #CORRIDOR} blocks around the route, and examines
 *       {@link #MAX_NODES} positions at most. A move in water, up or down costs more.</li>
 *   <li>The walk reads only the loaded chunks: a position in a chunk that is not loaded is not standable.</li>
 * </ul>
 */
public final class RouteWalker {
    /** The maximum distance from the route, in blocks. */
    public static final double CORRIDOR = 8.0;
    /** The maximum number of positions that a search examines. */
    public static final int MAX_NODES = 8000;
    /** The maximum drop of one move, in blocks. */
    public static final int MAX_DROP = 3;
    /** The distance to the end that ends the search, in blocks. */
    private static final double GOAL_DISTANCE = 1.5;
    /** The largest climb from one column to the next one of a simple walk ({@link #followColumns}), in blocks. */
    public static final double MAX_CLIMB = 1.6;
    /** The distance between two samples of a simple walk: smaller than a block, so no block is missed. */
    private static final double COLUMN_SAMPLE = 0.25;
    private static final double DIAGONAL = Math.sqrt(2.0);
    private static final double UP_COST = 0.5;
    private static final double DOWN_COST = 0.2;
    private static final double FORD_COST = 1.5;
    private static final double SWIM_COST = 2.0;
    private static final int[][] DIRECTIONS = {
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}
    };

    /**
     * A position of a walk.
     *
     * @param x     block X
     * @param y     block Y of the feet
     * @param z     block Z
     * @param floor the height of the floor under the feet, for example y - 0.5 on a bottom slab
     */
    public record Step(int x, int y, int z, double floor) {
    }

    private record Node(long key, double cost, double estimate) {
    }

    private final LevelReader blocks;
    private final BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();

    /**
     * Makes a walker.
     *
     * @param blocks the level: the walk uses only its loaded chunks
     */
    public RouteWalker(LevelReader blocks) {
        this.blocks = blocks;
    }

    /**
     * The walk along a part of a route.
     *
     * @param start the start of the walk: a standable position
     * @param path  the part of the route, from the start to the end
     * @return the positions of the walk, from the start to a position near the end of the part, or an empty list if
     *         the search finds no walk in the corridor
     */
    public List<Step> walk(Step start, Route path) {
        List<Route.Point> points = path.points();
        Route.Point end = points.get(points.size() - 1);
        Map<Long, Double> best = new HashMap<>();
        Map<Long, Long> parent = new HashMap<>();
        Map<Long, Double> floors = new HashMap<>();
        PriorityQueue<Node> open = new PriorityQueue<>((a, b) -> Double.compare(a.estimate(), b.estimate()));
        long startKey = BlockPos.asLong(start.x(), start.y(), start.z());
        best.put(startKey, 0.0);
        floors.put(startKey, start.floor());
        open.add(new Node(startKey, 0.0, heuristic(start.x(), start.z(), end)));
        int examined = 0;
        while (!open.isEmpty() && examined < MAX_NODES) {
            Node node = open.poll();
            if (node.cost() > best.getOrDefault(node.key(), Double.MAX_VALUE)) {
                continue;
            }
            examined++;
            int x = BlockPos.getX(node.key());
            int y = BlockPos.getY(node.key());
            int z = BlockPos.getZ(node.key());
            if (Math.hypot(x + 0.5 - end.x(), z + 0.5 - end.z()) <= GOAL_DISTANCE) {
                return build(node.key(), parent, floors);
            }
            for (int[] direction : DIRECTIONS) {
                int nx = x + direction[0];
                int nz = z + direction[1];
                if (path.distanceTo(nx + 0.5, nz + 0.5) > CORRIDOR) {
                    continue;
                }
                boolean diagonal = direction[0] != 0 && direction[1] != 0;
                for (int ny = y + 1; ny >= y - MAX_DROP; ny--) {
                    double floor = floorAt(nx, ny, nz);
                    if (Double.isNaN(floor) || !canMove(x, y, z, nx, ny, nz, diagonal)) {
                        continue;
                    }
                    double cost = node.cost() + (diagonal ? DIAGONAL : 1.0) + moveCost(y, ny, nx, nz);
                    long key = BlockPos.asLong(nx, ny, nz);
                    if (cost < best.getOrDefault(key, Double.MAX_VALUE)) {
                        best.put(key, cost);
                        parent.put(key, node.key());
                        floors.put(key, floor);
                        open.add(new Node(key, cost, cost + heuristic(nx, nz, end)));
                    }
                }
            }
        }
        return List.of();
    }

    /**
     * A simple walk along a part of a route when the search finds no walk: one position for each block of the part, in
     * each column the highest standable position that is not more than {@link #MAX_CLIMB} blocks above the position
     * before it. The walk can go through a wall, but it stays on the floors.
     *
     * @param part  the part of the route
     * @param start the position before the part, or null
     * @return the positions, with a gap where a column has no standable position
     */
    public List<Step> followColumns(Route part, @Nullable Step start) {
        List<Step> steps = new ArrayList<>();
        Step previous = start;
        int lastX = Integer.MIN_VALUE;
        int lastZ = Integer.MIN_VALUE;
        for (double progress = 0.0; progress <= part.length() + 1.0E-6; progress += COLUMN_SAMPLE) {
            Route.Position position = part.positionAt(progress);
            int x = (int) Math.floor(position.x());
            int z = (int) Math.floor(position.z());
            if (x == lastX && z == lastZ) {
                continue;
            }
            lastX = x;
            lastZ = z;
            if (!isLoaded(x, z)) {
                continue;
            }
            Step step = column(x, z, previous);
            if (step != null) {
                steps.add(step);
                previous = step;
            }
        }
        return steps;
    }

    /** The position of a column for {@link #followColumns}, or null. */
    @Nullable
    private Step column(int x, int z, @Nullable Step previous) {
        int top = blocks.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        Step lowestAbove = null;
        for (int y = top; y > blocks.getMinBuildHeight(); y--) {
            double floor = floorAt(x, y, z);
            if (Double.isNaN(floor)) {
                continue;
            }
            Step step = new Step(x, y, z, floor);
            if (previous == null || floor <= previous.floor() + MAX_CLIMB) {
                return step;
            }
            lowestAbove = step;
        }
        return lowestAbove;
    }

    /**
     * The standable position of a column with the highest floor at or below a height.
     *
     * @param x    block X
     * @param z    block Z
     * @param maxY the highest block Y of the feet to look at
     * @param minY the lowest block Y of the feet to look at
     * @return the position, or null if the column has no standable position
     */
    @Nullable
    public Step highestStandable(int x, int z, int maxY, int minY) {
        for (int y = maxY; y >= minY; y--) {
            double floor = floorAt(x, y, z);
            if (!Double.isNaN(floor)) {
                return new Step(x, y, z, floor);
            }
        }
        return null;
    }

    /**
     * The floor under the feet at a position, or NaN if the position is not standable (see the class description).
     *
     * @param x block X
     * @param y block Y of the feet
     * @param z block Z
     * @return the height of the floor, or NaN
     */
    public double floorAt(int x, int y, int z) {
        if (!isLoaded(x, z)) {
            return Double.NaN;
        }
        BlockState feet = state(x, y, z);
        if (!isFree(x, y + 1, z, true)) {
            return Double.NaN;
        }
        if (!isOpening(feet)) {
            VoxelShape feetShape = feet.getCollisionShape(blocks, cursor.set(x, y, z));
            if (!feetShape.isEmpty()) {
                double top = feetShape.max(Direction.Axis.Y);
                return top <= 0.5 && isFree(x, y + 2, z, true) ? y + top : Double.NaN;
            }
        }
        if (isBlocking(feet.getFluidState())) {
            return Double.NaN;
        }
        BlockState below = state(x, y - 1, z);
        FluidState belowFluid = below.getFluidState();
        if (!feet.getFluidState().isEmpty()) {
            // A ford: the feet are in the water, on a solid floor.
            double top = solidTop(below, x, y - 1, z);
            return Double.isNaN(top) ? Double.NaN : y - 1 + top;
        }
        if (!belowFluid.isEmpty() && below.getCollisionShape(blocks, cursor.set(x, y - 1, z)).isEmpty()) {
            // The surface of the water: the merchant swims or takes a boat.
            return isBlocking(belowFluid) ? Double.NaN : y - 1 + belowFluid.getHeight(blocks, cursor.set(x, y - 1, z));
        }
        double top = solidTop(below, x, y - 1, z);
        return Double.isNaN(top) ? Double.NaN : y - 1 + top;
    }

    /** The top of the collision shape of a floor block, or NaN for no floor or a floor higher than a block. */
    private double solidTop(BlockState state, int x, int y, int z) {
        if (isOpening(state) || state.is(BlockTags.LEAVES)) {
            return Double.NaN;
        }
        VoxelShape shape = state.getCollisionShape(blocks, cursor.set(x, y, z));
        if (shape.isEmpty()) {
            return Double.NaN;
        }
        double top = shape.max(Direction.Axis.Y);
        return top > 1.0 + 1.0E-6 ? Double.NaN : top;
    }

    /** True if a move from a position to the position next to it is valid. */
    private boolean canMove(int x, int y, int z, int nx, int ny, int nz, boolean diagonal) {
        if (ny > y && !isFree(x, y + 2, z, true)) {
            // No space for the jump.
            return false;
        }
        int high = Math.max(y, ny) + 1;
        for (int k = ny; k <= high; k++) {
            // The column of the target is free from the feet to the head, also above it for a drop.
            if (k >= ny + 2 && !isFree(nx, k, nz, false)) {
                return false;
            }
        }
        if (diagonal) {
            int low = Math.max(y, ny);
            for (int k = low; k <= low + 1; k++) {
                if (!isFree(nx, k, z, false) || !isFree(x, k, nz, false)) {
                    return false;
                }
            }
        }
        return true;
    }

    private double moveCost(int y, int ny, int nx, int nz) {
        double cost = ny > y ? UP_COST : (y - ny) * DOWN_COST;
        if (!state(nx, ny, nz).getFluidState().isEmpty()) {
            cost += FORD_COST;
        } else if (!state(nx, ny - 1, nz).getFluidState().isEmpty()) {
            cost += SWIM_COST;
        }
        return cost;
    }

    /**
     * True if a block is free for the body: no collision (or a fence gate or a door), and no water at the head.
     *
     * @param head true to refuse a fluid
     */
    private boolean isFree(int x, int y, int z, boolean head) {
        BlockState state = state(x, y, z);
        if (head && !state.getFluidState().isEmpty()) {
            return false;
        }
        if (isBlocking(state.getFluidState())) {
            return false;
        }
        return isOpening(state) || state.getCollisionShape(blocks, cursor.set(x, y, z)).isEmpty();
    }

    /** True for a fence gate or a door: the merchant opens it. */
    private static boolean isOpening(BlockState state) {
        return state.is(BlockTags.FENCE_GATES) || state.is(BlockTags.DOORS);
    }

    private static boolean isBlocking(FluidState fluid) {
        return fluid.is(FluidTags.LAVA);
    }

    /** True if the chunk of a column is loaded. */
    private boolean isLoaded(int x, int z) {
        return blocks.getChunk(x >> 4, z >> 4, ChunkStatus.FULL, false) != null;
    }

    private BlockState state(int x, int y, int z) {
        return blocks.getBlockState(cursor.set(x, y, z));
    }

    private static double heuristic(int x, int z, Route.Point end) {
        return Math.hypot(x + 0.5 - end.x(), z + 0.5 - end.z());
    }

    private static List<Step> build(long last, Map<Long, Long> parent, Map<Long, Double> floors) {
        List<Step> steps = new ArrayList<>();
        Long key = last;
        while (key != null) {
            steps.add(new Step(BlockPos.getX(key), BlockPos.getY(key), BlockPos.getZ(key), floors.get(key)));
            key = parent.get(key);
        }
        Collections.reverse(steps);
        return steps;
    }
}
