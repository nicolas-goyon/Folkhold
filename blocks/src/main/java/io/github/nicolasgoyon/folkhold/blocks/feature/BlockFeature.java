package io.github.nicolasgoyon.folkhold.blocks.feature;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The features that a mod adds to a block of the library: block entity, click, scheduled tick, and actions when the
 * block goes into the level or away. A mod registers the feature of a block with {@link BlockFeatures#register}.
 *
 * <p>Each method has the parameters of the method of the block with the same name. A method with no override does
 * nothing. The blocks of the library call these methods on the client and on the server.
 */
public interface BlockFeature {
    /** The feature of a block with no registered feature: it does nothing. */
    BlockFeature NONE = new BlockFeature() {
    };

    /**
     * The block entity of the block. Only the blocks with a block entity call it (see {@link EntityFeatureBlock}).
     *
     * @return the block entity, or null for no block entity
     */
    @Nullable
    default BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return null;
    }

    /**
     * The ticker of the block entity. Only the blocks with a block entity call it (see {@link EntityFeatureBlock}).
     *
     * @return the ticker, or null for no tick
     */
    @Nullable
    default <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return null;
    }

    /**
     * A click on the block with no item in the hand.
     *
     * @return the result of the click, or {@link InteractionResult#PASS} for the action of the block with no feature
     */
    default InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        return InteractionResult.PASS;
    }

    /** Called before a player breaks the block. */
    default void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
    }

    /** Called after the block goes into the level, or after a change of its state. */
    default void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
    }

    /**
     * Called before the block goes away, or before a change of its state: then {@code newState} is a state of the
     * same block. The block entity is still in the level.
     */
    default void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
    }

    /** A scheduled tick of the block (see {@code Level.scheduleTick}). */
    default void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
    }

    /**
     * The ticker for a block entity type: the ticker if the type is the expected type, else null. A feature uses it
     * in {@link #getTicker}.
     *
     * @param type     the type of the block entity of the level
     * @param expected the type of the ticker
     * @param ticker   the ticker
     * @return the ticker, or null if the types are different
     */
    @Nullable
    @SuppressWarnings("unchecked")
    static <E extends BlockEntity, T extends BlockEntity> BlockEntityTicker<T> ticker(BlockEntityType<T> type,
            BlockEntityType<E> expected, BlockEntityTicker<? super E> ticker) {
        return expected == type ? (BlockEntityTicker<T>) ticker : null;
    }
}
