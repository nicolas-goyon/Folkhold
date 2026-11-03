package io.github.nicolasgoyon.folkhold.events.common;

import io.github.nicolasgoyon.folkhold.events.block.EventBlockTags;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;

/** The rules that tell which blocks an event can move or break. All the events use them. */
public final class BlockRules {
    /** An item frame or a painting at this distance from a block or nearer hangs on it, in blocks. */
    private static final double HANGING_MARGIN = 0.1;
    /** The highest trunk of a natural tree, in blocks. */
    private static final int MAX_TRUNK = 32;

    private BlockRules() {
    }

    /**
     * True if an event can change a block, before the checks of its neighbors: a block with a collision box, no
     * fluid, not protected (see {@link EventBlockTags#isProtected}), not a part of a double block (a door, a tall
     * plant), not an attached block (a torch, a flower, a rail), and not in the tag {@code folkhold_events:natural_tree}.
     */
    public static boolean isChangeable(ServerLevel level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !EventBlockTags.isProtected(level, pos, state)
                && !state.is(EventBlockTags.NATURAL_TREE)
                && !state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getPistonPushReaction() != PushReaction.DESTROY
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * True if a neighbor of a position can lose its support or its data when the block at the position goes away: a
     * protected block, a block with a block entity (a sign, a bell, a banner), an attached block (a torch, a
     * lantern), or a hanging entity (an item frame, a painting). An event does not move such a support.
     */
    public static boolean holdsABlock(ServerLevel level, BlockPos pos) {
        if (!level.getEntitiesOfClass(HangingEntity.class, new AABB(pos).inflate(HANGING_MARGIN)).isEmpty()) {
            return true;
        }
        for (Direction direction : Direction.values()) {
            BlockPos neighborPos = pos.relative(direction);
            if (!level.isLoaded(neighborPos)) {
                continue;
            }
            BlockState neighbor = level.getBlockState(neighborPos);
            if (neighbor.isAir() || neighbor.canBeReplaced()) {
                continue;
            }
            if (EventBlockTags.isProtected(level, neighborPos, neighbor)
                    || neighbor.getPistonPushReaction() == PushReaction.DESTROY) {
                return true;
            }
        }
        return false;
    }

    /** True for the leaves of a natural tree: leaves that decay. A player places persistent leaves. */
    public static boolean isNaturalLeaves(BlockState state) {
        return state.is(BlockTags.LEAVES) && state.hasProperty(BlockStateProperties.PERSISTENT)
                && !state.getValue(BlockStateProperties.PERSISTENT);
    }

    /** True if natural leaves are on the top of the column of logs of a position: a natural tree. */
    public static boolean isTreeTrunk(ServerLevel level, BlockPos pos) {
        BlockPos.MutableBlockPos cursor = pos.mutable();
        for (int i = 0; i < MAX_TRUNK; i++) {
            cursor.move(Direction.UP);
            BlockState state = level.getBlockState(cursor);
            if (state.is(BlockTags.LEAVES)) {
                return isNaturalLeaves(state);
            }
            if (!state.is(BlockTags.LOGS)) {
                return false;
            }
        }
        return false;
    }

    /** True if a block can move into a position: air, fire, a fluid or a replaceable block. */
    public static boolean isFree(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && FallingBlock.isFree(level.getBlockState(pos));
    }

    /** True if the chunk of a position is loaded and its entities tick: the falling blocks move there. */
    public static boolean isReady(ServerLevel level, BlockPos pos) {
        return level.isLoaded(pos) && level.isPositionEntityTicking(pos);
    }
}
