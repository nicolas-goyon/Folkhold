package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The search of a way from the ground to a position, for a builder that walks as a villager.
 *
 * <ul>
 *   <li>The feet of the builder are in a position with a block below it that the builder walks on. The builder walks
 *       through the blocks of its feet and of its head (see {@link Walk}). Thus a door does not stop it.</li>
 *   <li>The builder walks to the next position at the same height. It goes up 1 block with a jump, when the block
 *       above its head is free before the jump. It goes down {@link #MAX_DROP} blocks at most.</li>
 *   <li>The builders come from the ground: the positions out of the zone of the search, near the bottom of the
 *       zone (see {@link Zone#around}). The zone is the box of the target and a band around it. Thus the search reads
 *       only the blocks of the zone and of the positions next to it.</li>
 *   <li>A builder climbs a scaffolding column of the plan from the bottom of the column. It does not walk from the
 *       top of a column to another position.</li>
 * </ul>
 *
 * <p>The search goes back from the position to the ground. A search with no way marks the positions that it visited:
 * a closed area. No way goes from the ground to the positions of a closed area until a block near the area changes
 * (see {@link #blockChanged}). Thus the plan uses one search for all its steps, and tells it about each change. A
 * construction site uses a search on the blocks of the level, for a position to stand that is not in the plan.
 *
 * <p>The search keeps the walk of the blocks in a grid: the zone and the positions next to it, between two heights.
 */
public final class WalkSearch {
    /** A builder goes down this number of blocks at most, as a villager. */
    static final int MAX_DROP = 3;
    /**
     * The searches of a plan visit this number of positions at most. After that, the search accepts all the
     * positions: the plan of a very large target does not stop the server for a long time.
     */
    static final long MAX_VISITS = 500_000L;

    private static final byte UNKNOWN = 0;
    private static final byte THROUGH = 1;
    private static final byte ON = 2;
    private static final byte BLOCKED = 3;
    /** A scaffolding block: a builder climbs the column, and does not walk from its top. */
    private static final byte SCAFFOLDING = 4;
    private static final int[] SIDE_X = {0, 0, -1, 1};
    private static final int[] SIDE_Z = {-1, 1, 0, 0};

    private final Function<BuildPos, Walk> blocks;
    private final Predicate<BuildPos> scaffolding;
    private final Zone zone;
    private final int gridX;
    private final int gridY;
    private final int gridZ;
    private final int sizeX;
    private final int sizeY;
    private final int sizeZ;
    /** The walk of each position of the grid, or {@link #UNKNOWN} before the first read. */
    private final byte[] walks;
    /** The closed area of each position of the grid, or 0. */
    private final int[] areaOf;
    /** The positions of each closed area, by the number of the area. An area that is not closed any more is null. */
    private final List<int[]> areas = new ArrayList<>();
    /** The number of the last search that visited each position of the grid. */
    private final int[] visitedBy;
    /** The origins of a move that {@link #origins} finds: X, Y and Z of each origin. */
    private final int[] origins = new int[3 * (MAX_DROP + 2)];
    private int searches;
    private long visits;

    /**
     * Makes a search.
     *
     * @param blocks      the walk of the block of each position now
     * @param scaffolding true for a scaffolding block now
     * @param zone        the zone of the search: the ground is out of it
     * @param lowestY     the lowest Y of the grid, for example the lowest bottom of a scaffolding column
     * @param highestY    the highest Y of the grid
     */
    WalkSearch(Function<BuildPos, Walk> blocks, Predicate<BuildPos> scaffolding, Zone zone, int lowestY,
            int highestY) {
        this.blocks = blocks;
        this.scaffolding = scaffolding;
        this.zone = zone;
        this.gridX = zone.minX() - 1;
        this.gridZ = zone.minZ() - 1;
        this.gridY = lowestY;
        this.sizeX = zone.maxX() - zone.minX() + 3;
        this.sizeZ = zone.maxZ() - zone.minZ() + 3;
        this.sizeY = Math.max(1, highestY - lowestY + 1);
        int cells = sizeX * sizeY * sizeZ;
        this.walks = new byte[cells];
        this.areaOf = new int[cells];
        this.visitedBy = new int[cells];
        areas.add(null);
    }

    /**
     * Makes a search around some positions, for example the positions of a target. The zone of the search is the box
     * of the positions and a band of the horizontal reach around it. The ground is out of the zone, at most 1 block
     * above the bottom of the box. The grid goes down below the bottom of the longest scaffolding column, and up above
     * the reach.
     *
     * @param positions   the positions
     * @param reach       the reach zone of the builders
     * @param blocks      the walk of the block of each position now
     * @param scaffolding true for a scaffolding block now
     * @return the search
     */
    public static WalkSearch around(Collection<BuildPos> positions, Reach reach, Function<BuildPos, Walk> blocks,
            Predicate<BuildPos> scaffolding) {
        Zone zone = Zone.around(positions, reach.horizontal());
        int minY = positions.stream().mapToInt(BuildPos::y).min().orElse(0);
        int maxY = positions.stream().mapToInt(BuildPos::y).max().orElse(0);
        return new WalkSearch(blocks, scaffolding, zone, minY - ConstructionPlanner.MAX_SCAFFOLDING - MAX_DROP - 2,
                maxY + reach.above() + MAX_DROP + 2);
    }

    /**
     * True if a builder can go from the ground to a position to stand. On a scaffolding column, the builder climbs the
     * column from its bottom.
     *
     * @param feet the position of the feet
     * @return true if a way goes from the ground to the position
     */
    public boolean canReach(BuildPos feet) {
        int y = feet.y();
        if (walk(feet.x(), y - 1, feet.z()) != SCAFFOLDING) {
            return search(feet.x(), y, feet.z(), false);
        }
        y--;
        while (walk(feet.x(), y - 1, feet.z()) == SCAFFOLDING) {
            y--;
        }
        return search(feet.x(), y, feet.z(), true);
    }

    /**
     * True if a builder can go from the ground to the bottom of a scaffolding column, to climb it. The column can be
     * a column that the plan adds now.
     *
     * @param bottom the position of the lowest block of the column: the feet of the builder before the climb
     * @return true if a way goes from the ground to the bottom of the column
     */
    boolean canClimb(BuildPos bottom) {
        return search(bottom.x(), bottom.y(), bottom.z(), true);
    }

    /**
     * Tells the search that the block of a position changed. The closed areas near the position are not closed any
     * more: the change can open a way to them.
     *
     * @param pos the position of the block
     */
    public void blockChanged(BuildPos pos) {
        int index = index(pos.x(), pos.y(), pos.z());
        if (index >= 0) {
            walks[index] = UNKNOWN;
        }
        // The moves to a position read the blocks around it, from 2 blocks below it to MAX_DROP + 1 blocks above it.
        for (int y = pos.y() - MAX_DROP - 1; y <= pos.y() + 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    int near = index(pos.x() + dx, y, pos.z() + dz);
                    if (near >= 0 && areaOf[near] != 0) {
                        open(areaOf[near]);
                    }
                }
            }
        }
    }

    private void open(int area) {
        for (int index : areas.get(area)) {
            areaOf[index] = 0;
        }
        areas.set(area, null);
    }

    private boolean search(int startX, int startY, int startZ, boolean column) {
        if (!zone.contains(startX, startZ)) {
            return zone.isGround(startY);
        }
        int start = index(startX, startY, startZ);
        if (start < 0 || visits > MAX_VISITS) {
            return true;
        }
        if (areaOf[start] != 0) {
            return false;
        }
        searches++;
        Queue queue = new Queue();
        int[] visited = new int[64];
        int count = 0;
        visitedBy[start] = searches;
        visited[count++] = start;
        queue.add(start, zone.toGround(startX, startZ));
        while (!queue.isEmpty()) {
            int feet = queue.poll();
            visits++;
            int x = gridX + feet % sizeX;
            int z = gridZ + feet / sizeX % sizeZ;
            int y = gridY + feet / (sizeX * sizeZ);
            boolean columnBottom = column && feet == start;
            for (int side = 0; side < SIDE_X.length; side++) {
                int found = origins(x, y, z, side, columnBottom);
                for (int k = 0; k < 3 * found; k += 3) {
                    int ox = origins[k];
                    int oy = origins[k + 1];
                    int oz = origins[k + 2];
                    if (!zone.contains(ox, oz)) {
                        if (zone.isGround(oy)) {
                            return true;
                        }
                        continue;
                    }
                    int origin = index(ox, oy, oz);
                    if (origin < 0) {
                        // Out of the grid: the search does not know the way, and accepts the position.
                        return true;
                    }
                    if (visitedBy[origin] != searches && areaOf[origin] == 0) {
                        visitedBy[origin] = searches;
                        if (count == visited.length) {
                            visited = Arrays.copyOf(visited, 2 * count);
                        }
                        visited[count++] = origin;
                        queue.add(origin, zone.toGround(ox, oz));
                    }
                }
            }
        }
        // The bottom of a column has its own moves: it is not a usual position for the feet, and not in the area.
        int[] area = Arrays.copyOfRange(visited, column ? 1 : 0, count);
        if (area.length > 0) {
            int number = areas.size();
            areas.add(area);
            for (int index : area) {
                areaOf[index] = number;
            }
        }
        return false;
    }

    /**
     * Finds the positions on a side from where a builder goes to a position of the feet with one move: a walk, a drop
     * or a jump. A builder goes into the bottom of a scaffolding column with a walk or a jump only.
     *
     * @return the number of positions in {@link #origins}
     */
    private int origins(int x, int y, int z, int side, boolean columnBottom) {
        int found = 0;
        int sx = x + SIDE_X[side];
        int sz = z + SIDE_Z[side];
        int maxDrop = columnBottom ? 0 : MAX_DROP;
        // A walk at the same height (drop 0), or a drop: the builder steps into the free space above the feet, and
        // falls.
        for (int drop = 0; drop <= maxDrop; drop++) {
            if (drop > 0 && walk(x, y + drop + 1, z) != THROUGH) {
                break;
            }
            if (isFeet(sx, y + drop, sz)) {
                found = add(found, sx, y + drop, sz);
            }
        }
        // A jump on the block below the feet, from the next position 1 block lower.
        if (isFeet(sx, y - 1, sz) && walk(sx, y + 1, sz) == THROUGH) {
            found = add(found, sx, y - 1, sz);
        }
        return found;
    }

    private int add(int found, int x, int y, int z) {
        origins[3 * found] = x;
        origins[3 * found + 1] = y;
        origins[3 * found + 2] = z;
        return found + 1;
    }

    /**
     * True if the feet of a builder that walks can be in a position. The builder walks through the blocks of its feet
     * and of its head, and walks on the block below. It is not on the top of a scaffolding column of the plan.
     */
    private boolean isFeet(int x, int y, int z) {
        return walk(x, y, z) == THROUGH && walk(x, y + 1, z) == THROUGH && walk(x, y - 1, z) == ON;
    }

    /** The walk of a position: {@link #THROUGH}, {@link #ON}, {@link #BLOCKED} or {@link #SCAFFOLDING}. */
    private byte walk(int x, int y, int z) {
        int index = index(x, y, z);
        if (index < 0) {
            return read(new BuildPos(x, y, z));
        }
        byte walk = walks[index];
        if (walk == UNKNOWN) {
            walk = read(new BuildPos(x, y, z));
            walks[index] = walk;
        }
        return walk;
    }

    private byte read(BuildPos pos) {
        if (scaffolding.test(pos)) {
            return SCAFFOLDING;
        }
        return switch (blocks.apply(pos)) {
            case THROUGH -> THROUGH;
            case ON -> ON;
            case BLOCKED -> BLOCKED;
        };
    }

    /** The index of a position in the grid, or -1 out of the grid. */
    private int index(int x, int y, int z) {
        int gx = x - gridX;
        int gy = y - gridY;
        int gz = z - gridZ;
        if (gx < 0 || gx >= sizeX || gy < 0 || gy >= sizeY || gz < 0 || gz >= sizeZ) {
            return -1;
        }
        return (gy * sizeZ + gz) * sizeX + gx;
    }

    /**
     * The positions to visit: the position nearest to the ground comes first. The distances to the ground are small
     * numbers, thus the queue has one list of positions for each distance.
     */
    private static final class Queue {
        private int[][] lists = new int[8][];
        private int[] sizes = new int[8];
        private int lowest = Integer.MAX_VALUE;
        private int size;

        void add(int index, int distance) {
            if (distance >= lists.length) {
                int length = Math.max(2 * lists.length, distance + 1);
                lists = Arrays.copyOf(lists, length);
                sizes = Arrays.copyOf(sizes, length);
            }
            if (lists[distance] == null) {
                lists[distance] = new int[16];
            } else if (sizes[distance] == lists[distance].length) {
                lists[distance] = Arrays.copyOf(lists[distance], 2 * sizes[distance]);
            }
            lists[distance][sizes[distance]++] = index;
            lowest = Math.min(lowest, distance);
            size++;
        }

        boolean isEmpty() {
            return size == 0;
        }

        int poll() {
            while (sizes[lowest] == 0) {
                lowest++;
            }
            size--;
            return lists[lowest][--sizes[lowest]];
        }
    }

    /**
     * The zone of a search on the X axis and on the Z axis, and the height of the ground around it.
     *
     * @param minX    the lowest X of the zone
     * @param maxX    the highest X of the zone
     * @param minZ    the lowest Z of the zone
     * @param maxZ    the highest Z of the zone
     * @param groundY the highest Y of the feet on the ground. Out of the zone, a position at this height or lower is
     *                the ground.
     */
    record Zone(int minX, int maxX, int minZ, int maxZ, int groundY) {
        /**
         * The box of some positions and a band around it, on the X axis and on the Z axis. The ground is at most
         * 1 block above the bottom of the box, as the ground of the fences of a construction site (see
         * {@link WorkZone}). A roof out of the zone is not the ground.
         *
         * @param positions the positions, for example the positions of a target
         * @param band      the width of the band in blocks, for example the horizontal reach of the builders
         * @return the zone, or a zone around the position 0 0 0 with no positions
         */
        static Zone around(Collection<BuildPos> positions, int band) {
            int minX = positions.stream().mapToInt(BuildPos::x).min().orElse(0);
            int maxX = positions.stream().mapToInt(BuildPos::x).max().orElse(0);
            int minZ = positions.stream().mapToInt(BuildPos::z).min().orElse(0);
            int maxZ = positions.stream().mapToInt(BuildPos::z).max().orElse(0);
            int minY = positions.stream().mapToInt(BuildPos::y).min().orElse(0);
            return new Zone(minX - band, maxX + band, minZ - band, maxZ + band, minY + 1);
        }

        /** True if a position is in the zone, on the X axis and on the Z axis. */
        boolean contains(int x, int z) {
            return x >= minX && x <= maxX && z >= minZ && z <= maxZ;
        }

        /** True if a position out of the zone, at this height, is on the ground: it is not higher than the ground. */
        boolean isGround(int y) {
            return y <= groundY;
        }

        /** The number of horizontal moves from a position in the zone to the ground. */
        int toGround(int x, int z) {
            return 1 + Math.min(Math.min(x - minX, maxX - x), Math.min(z - minZ, maxZ - z));
        }
    }
}
