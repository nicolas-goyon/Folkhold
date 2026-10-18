package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stairs at the gates of a village. The village compound is flat, but the terrain outside a gate can be some blocks
 * higher or lower. The heart builds the stairs once, after the scan of the layout.
 *
 * <ul>
 *   <li>The gates of the palisade are the fence gates at {@link #WALL_DISTANCE} blocks or more from the heart, on
 *       the axis of their passage. The other fence gates (gardens, pens) do not get stairs.</li>
 *   <li>The fence gates side by side make one exit. The column in the middle of the exit gives the terrain level, and
 *       all the columns of the exit get the same steps.</li>
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

    private VillageExits() {
    }

    /** The fence gates of one exit, side by side, and the direction out of the village. */
    private record Exit(List<BlockPos> columns, Direction out) {
        BlockPos middle() {
            return columns.get(columns.size() / 2);
        }
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

    /** The exits: the gates of the palisade, in groups of gates side by side. */
    private static List<Exit> exits(ServerLevel level, BlockPos heart, List<BlockPos> gates) {
        List<Exit> exits = new ArrayList<>();
        List<BlockPos> sorted = new ArrayList<>(gates);
        sorted.sort(Comparator.<BlockPos>comparingInt(pos -> pos.getY()).thenComparingInt(pos -> pos.getX())
                .thenComparingInt(pos -> pos.getZ()));
        for (BlockPos gate : sorted) {
            BlockState state = level.getBlockState(gate);
            if (!(state.getBlock() instanceof FenceGateBlock)) {
                continue;
            }
            Direction facing = state.getValue(FenceGateBlock.FACING);
            Direction out = gate.relative(facing).distSqr(heart) > gate.relative(facing.getOpposite()).distSqr(heart)
                    ? facing : facing.getOpposite();
            int distance = Math.abs(out.getAxis().choose(gate.getX() - heart.getX(), 0, gate.getZ() - heart.getZ()));
            if (distance < WALL_DISTANCE) {
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
            } else {
                List<BlockPos> columns = new ArrayList<>();
                columns.add(gate.immutable());
                exits.add(new Exit(columns, out));
            }
        }
        return exits;
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
