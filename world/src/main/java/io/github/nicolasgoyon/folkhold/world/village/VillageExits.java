package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stairs at the gates of a village. The village compound is flat, but the terrain outside a gate can be some blocks
 * higher or lower. The heart builds the stairs once, after the scan of the layout.
 *
 * <p>The exits also give the approaches of the village ({@link #approaches}): the raiders of a raid gather there
 * before they enter (see {@link VillageRaids}).
 *
 * <ul>
 *   <li>The gates of the palisade are the fence gates at {@link #WALL_DISTANCE} blocks or more from the heart, on
 *       the axis of their passage. The other fence gates (gardens, pens) do not get stairs.</li>
 *   <li>The fence gates side by side make one exit. The column in the middle of the exit gives the terrain level, and
 *       all the columns of the exit get the same steps.</li>
 *   <li>The fence gates stacked on a fence gate of an exit, on the same axis, are in the same column of the exit. The
 *       lowest fence gate of a column gives the floor of the exit. The stacked fence gates get no steps.</li>
 *   <li>A broken fence gate (a position of the layout with no fence gate and no collision, see {@link #isBroken}) stays
 *       a part of its exit, on the main axis from the heart. It is always open: the opening is an exit with no
 *       gate.</li>
 *   <li>Lower terrain: stairs that go down, with cobblestone under each step to the terrain.</li>
 *   <li>Higher terrain: stairs that go up, with the terrain above each step removed.</li>
 *   <li>At most {@link #MAX_STEPS} steps. A terrain that is higher than this (a cliff, a tree) gets no stairs.</li>
 *   <li>The headroom never removes a block with a block entity or a block that a player cannot break.</li>
 * </ul>
 */
public final class VillageExits {
    /** The maximum number of steps of an exit. */
    public static final int MAX_STEPS = 6;
    /** The minimum distance from the heart to a gate of the palisade, on the axis of the passage. */
    public static final int WALL_DISTANCE = 15;
    /** The free height above a step, in blocks. */
    private static final int HEADROOM = 3;
    /** The maximum depth of the support under a step, in blocks. */
    private static final int MAX_SUPPORT = 8;
    /** The distance from the middle of a gate to its approach, in blocks: after the stairs of the gate. */
    public static final int APPROACH_DISTANCE = MAX_STEPS + 2;

    private VillageExits() {
    }

    /**
     * The fence gates of one exit and the direction out of the village.
     *
     * @param columns the lowest fence gate of each column, side by side
     * @param blocks  all the fence gates of the exit, with the stacked fence gates
     * @param out     the direction out of the village
     */
    private record Exit(List<BlockPos> columns, List<BlockPos> blocks, Direction out) {
        BlockPos middle() {
            return columns.get(columns.size() / 2);
        }
    }

    /**
     * The approach of a gate of the palisade.
     *
     * @param point the position outside the gate, {@link #APPROACH_DISTANCE} blocks from its middle, at the level of
     *              the feet on the terrain. In water, the point is at the surface: a raider swims there.
     * @param gates  the lowest fence gate of each column of the gate, side by side
     * @param blocks all the fence gates of the gate: the fence gates of {@code gates} and the fence gates stacked on
     *               them
     */
    public record Approach(BlockPos point, List<BlockPos> gates, List<BlockPos> blocks) {
        /**
         * An approach of a gate with no stacked fence gates.
         *
         * @param point the position outside the gate
         * @param gates the fence gates of the gate, side by side
         */
        public Approach(BlockPos point, List<BlockPos> gates) {
            this(point, gates, gates);
        }

        /**
         * True if a column of the gate is open: all the fence gates of the column are open or broken (see
         * {@link VillageExits#isBroken}).
         */
        public boolean isOpen(Level level) {
            return isOpen(level, 1.0);
        }

        /**
         * True if the gate is open for an entity of a width, with no limit on the height (see
         * {@link #isOpen(Level, double, double)}).
         *
         * @param level the level
         * @param width the width of the entity, in blocks
         * @return true if the entity can go through the gate
         */
        public boolean isOpen(Level level, double width) {
            return isOpen(level, width, 1.0);
        }

        /**
         * True if the gate is open for an entity of a width and a height: the open columns side by side on the line
         * of the gate are as wide as the entity or wider. A column of the gate is open when all its fence gates are
         * open or broken (see {@link VillageExits#isBroken}) and its blocks from the floor to the height of the entity
         * have no collision. The positions next to the gate on its line (for example a broken part of the palisade)
         * are open columns when their blocks from the floor to the height of the entity have no collision. Thus a gate
         * with a broken middle column and closed side columns is open for a pillager, and closed for a ravager. A gate
         * of 2 blocks of free height is closed for a ravager (2.2 blocks high).
         *
         * @param level  the level
         * @param width  the width of the entity, in blocks
         * @param height the height of the entity, in blocks
         * @return true if the entity can go through the gate
         */
        public boolean isOpen(Level level, double width, double height) {
            int high = blocks(height);
            return hasRun(line(blocks(width) - 1), blocks(width), pos -> isOpenColumn(level, pos, high));
        }

        /**
         * True if the gate can open for an entity of a width and a height: the gate is open for the entity (see
         * {@link #isOpen(Level, double, double)}) when all its fence gates are open. A raider that forces the gate
         * thus goes through it. A position of the gate with a block that is not a fence gate and has a collision (for
         * example a block that a player put in a broken gate) does not open. The method changes no block.
         *
         * @param level  the level
         * @param width  the width of the entity, in blocks
         * @param height the height of the entity, in blocks
         * @return true if the entity can go through the gate after a force
         */
        public boolean fits(Level level, double width, double height) {
            int high = blocks(height);
            return hasRun(line(blocks(width) - 1), blocks(width), pos -> {
                for (int dy = 0; dy < high; dy++) {
                    BlockPos above = pos.above(dy);
                    boolean gate = blocks.contains(above)
                            && level.getBlockState(above).getBlock() instanceof FenceGateBlock;
                    if (!gate && !isFree(level, above)) {
                        return false;
                    }
                }
                return true;
            });
        }

        /**
         * True if a column on the line of the gate is open for an entity of a height in blocks: the fence gates of a
         * column of the gate are open or broken, and the blocks from the floor to the height have no collision.
         *
         * @param level the level
         * @param pos   the floor of the column, on {@link #line}
         * @param high  the height of the entity, in blocks
         * @return true if the entity of this height can go through the column
         */
        public boolean isOpenColumn(Level level, BlockPos pos, int high) {
            if (gates.contains(pos) && !isOpenColumn(level, pos)) {
                return false;
            }
            for (int dy = 0; dy < high; dy++) {
                if (!isFree(level, pos.above(dy))) {
                    return false;
                }
            }
            return true;
        }

        /**
         * The floor positions on the line of the gate: the columns of the gate, with a number of positions of the
         * palisade on each side of the gate.
         *
         * @param extra the number of positions on each side of the gate
         * @return the positions, in the order of the line
         */
        public List<BlockPos> line(int extra) {
            Direction along = along();
            List<BlockPos> line = new ArrayList<>();
            for (int i = extra; i > 0; i--) {
                line.add(gates.get(0).relative(along, -i));
            }
            line.addAll(gates);
            for (int i = 1; i <= extra; i++) {
                line.add(gates.get(gates.size() - 1).relative(along, i));
            }
            return line;
        }

        /** The horizontal direction along the line of the gate, from its first column to its last column. */
        private Direction along() {
            if (gates.size() > 1) {
                BlockPos step = gates.get(1).subtract(gates.get(0));
                return Direction.fromDelta(step.getX(), 0, step.getZ());
            }
            BlockPos middle = gates.get(0);
            return Math.abs(point.getX() - middle.getX()) >= Math.abs(point.getZ() - middle.getZ())
                    ? Direction.SOUTH : Direction.EAST;
        }

        /** True if a number of positions side by side in a line pass a test. */
        private static boolean hasRun(List<BlockPos> line, int needed, Predicate<BlockPos> open) {
            int run = 0;
            for (BlockPos pos : line) {
                run = open.test(pos) ? run + 1 : 0;
                if (run >= needed) {
                    return true;
                }
            }
            return false;
        }

        /**
         * The number of blocks for a size of an entity: 1 at least.
         *
         * @param size the width or the height of the entity, in blocks
         * @return the number of blocks
         */
        public static int blocks(double size) {
            return Math.max(1, (int) Math.ceil(size - 1.0E-6));
        }

        private static boolean isFree(Level level, BlockPos pos) {
            return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
        }

        /**
         * The fence gates of one column of the gate: the floor fence gate and the fence gates stacked on it.
         *
         * @param floor the floor fence gate of the column, one of {@link #gates}
         * @return the fence gates of the column
         */
        public List<BlockPos> column(BlockPos floor) {
            return blocks.stream().filter(block -> block.getX() == floor.getX() && block.getZ() == floor.getZ())
                    .toList();
        }

        /** True if all the fence gates of the column of a floor fence gate are open or broken. */
        public boolean isOpenColumn(Level level, BlockPos floor) {
            return column(floor).stream().allMatch(block -> isOpenGate(level, block));
        }

        private static boolean isOpenGate(Level level, BlockPos pos) {
            BlockState state = level.getBlockState(pos);
            return state.getBlock() instanceof FenceGateBlock ? state.getValue(FenceGateBlock.OPEN)
                    : isBroken(level, pos);
        }
    }

    /**
     * True if a gate position of the layout has no fence gate and no collision: for example a raider broke the fence
     * gate. An entity goes through the position.
     *
     * @param level the level
     * @param pos   the gate position
     * @return true for a broken fence gate
     */
    public static boolean isBroken(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return !(state.getBlock() instanceof FenceGateBlock) && state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The approaches of the gates of the palisade of a village: one position outside each gate. The method reads only
     * the loaded chunks: a gate in a chunk that is not loaded has no approach.
     *
     * @param level the level
     * @param heart the position of the heart
     * @param gates the fence gates of the village
     * @return the approaches
     */
    public static List<Approach> approaches(ServerLevel level, BlockPos heart, List<BlockPos> gates) {
        List<BlockPos> loaded = gates.stream().filter(level::isLoaded).toList();
        List<Approach> approaches = new ArrayList<>();
        for (Exit exit : exits(level, heart, loaded)) {
            BlockPos middle = exit.middle();
            BlockPos outside = middle.relative(exit.out(), APPROACH_DISTANCE);
            if (level.isLoaded(outside)) {
                BlockPos point = surface(level, outside.atY(terrain(level, outside, middle.getY())));
                approaches.add(new Approach(point, List.copyOf(exit.columns()), List.copyOf(exit.blocks())));
            }
        }
        return approaches;
    }

    /**
     * The first position with no fluid at or above a position: the surface of the water. A position with no fluid
     * does not change. The search stops at {@link #MAX_STEPS} + {@link #MAX_SUPPORT} blocks up, the depth of the
     * search of {@link #terrain}.
     */
    private static BlockPos surface(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos surface = pos.mutable();
        int top = pos.getY() + MAX_STEPS + MAX_SUPPORT;
        while (surface.getY() < top && !level.getFluidState(surface).isEmpty()) {
            surface.move(Direction.UP);
        }
        return surface.immutable();
    }

    /**
     * Builds the stairs of all exits of a village.
     *
     * @param level the level
     * @param heart the position of the heart
     * @param gates the fence gates of the village
     * @return the number of stair blocks, or -1 if a chunk near a gate is not ready (nothing changes then, see
     *         {@link ReadyChunks})
     */
    public static int build(ServerLevel level, BlockPos heart, List<BlockPos> gates) {
        List<Exit> exits = exits(level, heart, gates);
        for (Exit exit : exits) {
            for (BlockPos column : exit.columns()) {
                if (!ReadyChunks.isReady(level, column.relative(exit.out(), MAX_STEPS + 1))) {
                    return -1;
                }
            }
        }
        int stairs = 0;
        for (Exit exit : exits) {
            stairs += build(level, exit);
        }
        return stairs;
    }

    /**
     * The exits: the gates of the palisade, in groups of gates side by side. The method reads the fence gates from the
     * lowest to the highest, so a fence gate on a fence gate of an exit goes into the column of this exit.
     */
    private static List<Exit> exits(ServerLevel level, BlockPos heart, List<BlockPos> gates) {
        List<Exit> exits = new ArrayList<>();
        List<BlockPos> sorted = new ArrayList<>(gates);
        sorted.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));
        for (BlockPos gate : sorted) {
            BlockState state = level.getBlockState(gate);
            boolean broken = isBroken(level, gate);
            if (!(state.getBlock() instanceof FenceGateBlock) && !broken) {
                continue;
            }
            Direction facing = broken ? brokenFacing(level, heart, gate, gates)
                    : state.getValue(FenceGateBlock.FACING);
            Direction out = gate.relative(facing).distSqr(heart) > gate.relative(facing.getOpposite()).distSqr(heart)
                    ? facing : facing.getOpposite();
            int distance = Math.abs(out.getAxis().choose(gate.getX() - heart.getX(), 0, gate.getZ() - heart.getZ()));
            if (distance < WALL_DISTANCE) {
                continue;
            }
            BlockPos below = gate.below();
            BlockState belowState = level.getBlockState(below);
            boolean onColumn = belowState.getBlock() instanceof FenceGateBlock
                    ? belowState.getValue(FenceGateBlock.FACING).getAxis() == facing.getAxis()
                    : isBroken(level, below);
            Exit stacked = onColumn
                    ? exits.stream().filter(exit -> exit.blocks().contains(below)).findFirst().orElse(null)
                    : null;
            if (stacked != null) {
                stacked.blocks().add(gate.immutable());
                continue;
            }
            Exit joined = null;
            for (Exit exit : exits) {
                BlockPos last = exit.columns().get(exit.columns().size() - 1);
                if (exit.out() == out && last.getY() == gate.getY() && last.distManhattan(gate) == 1) {
                    joined = exit;
                }
            }
            if (joined != null) {
                joined.columns().add(gate.immutable());
                joined.blocks().add(gate.immutable());
            } else {
                List<BlockPos> columns = new ArrayList<>();
                columns.add(gate.immutable());
                List<BlockPos> blocks = new ArrayList<>(columns);
                exits.add(new Exit(columns, blocks, out));
            }
        }
        return exits;
    }

    /**
     * The facing of a broken fence gate: the facing of a fence gate of the layout next to it (on a side, below or
     * above), else the direction from the heart on the main axis.
     */
    private static Direction brokenFacing(ServerLevel level, BlockPos heart, BlockPos gate, List<BlockPos> gates) {
        for (Direction direction : Direction.values()) {
            BlockPos next = gate.relative(direction);
            BlockState state = level.getBlockState(next);
            if (gates.contains(next) && state.getBlock() instanceof FenceGateBlock) {
                return state.getValue(FenceGateBlock.FACING);
            }
        }
        return mainDirection(heart, gate);
    }

    /** The direction from the heart to a gate position, on the main axis. */
    private static Direction mainDirection(BlockPos heart, BlockPos gate) {
        int dx = gate.getX() - heart.getX();
        int dz = gate.getZ() - heart.getZ();
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST)
                : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    /** Builds the steps of one exit. Returns the number of stair blocks. */
    private static int build(ServerLevel level, Exit exit) {
        BlockPos middle = exit.middle();
        int floor = middle.getY();
        int first = terrain(level, middle.relative(exit.out(), 1), floor);
        if (first == floor || first - floor > MAX_STEPS) {
            return 0;
        }
        boolean down = first < floor;
        int stairs = 0;
        for (int k = 1; k <= MAX_STEPS; k++) {
            int walking = down ? floor - (k - 1) : floor + (k - 1);
            int terrain = terrain(level, middle.relative(exit.out(), k), walking);
            if (down ? terrain >= walking : terrain <= walking) {
                break;
            }
            int y = down ? floor - k : floor + k - 1;
            Direction high = down ? exit.out().getOpposite() : exit.out();
            for (BlockPos column : exit.columns()) {
                BlockPos step = column.relative(exit.out(), k).atY(y);
                placeStep(level, step, high, down);
                stairs++;
            }
        }
        return stairs;
    }

    /**
     * Places one step: a stair block with its high side in a direction, the headroom above it, and the support below
     * it for stairs that go down.
     */
    private static void placeStep(ServerLevel level, BlockPos step, Direction high, boolean support) {
        level.setBlock(step, Blocks.COBBLESTONE_STAIRS.defaultBlockState().setValue(StairBlock.FACING, high),
                Block.UPDATE_ALL);
        for (int dy = 1; dy <= HEADROOM; dy++) {
            BlockPos above = step.above(dy);
            BlockState state = level.getBlockState(above);
            if (!state.isAir() && !state.hasBlockEntity() && state.getDestroySpeed(level, above) >= 0) {
                level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        if (support) {
            for (int dy = 1; dy <= MAX_SUPPORT; dy++) {
                BlockPos below = step.below(dy);
                if (!level.getBlockState(below).canBeReplaced()) {
                    break;
                }
                level.setBlock(below, Blocks.COBBLESTONE.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    /**
     * The level of the feet of a player on the terrain of a column, near a level: the first free block above the
     * ground. The search starts at the level and goes up through the solid blocks, or down through the free blocks.
     * So a roof, a tree crown or an overhang above the ground does not count. The search stops at
     * {@link #MAX_STEPS} + 1 blocks up and at {@link #MAX_STEPS} + {@link #MAX_SUPPORT} blocks down.
     */
    private static int terrain(ServerLevel level, BlockPos column, int near) {
        BlockPos.MutableBlockPos pos = column.mutable().setY(near);
        if (isSolid(level, pos)) {
            int top = near + MAX_STEPS + 1;
            while (pos.getY() < top && isSolid(level, pos)) {
                pos.move(Direction.UP);
            }
            return pos.getY();
        }
        int bottom = near - MAX_STEPS - MAX_SUPPORT;
        while (pos.getY() > bottom && !isSolid(level, pos.below())) {
            pos.move(Direction.DOWN);
        }
        return pos.getY();
    }

    private static boolean isSolid(ServerLevel level, BlockPos pos) {
        return !level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }
}
