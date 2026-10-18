package io.github.nicolasgoyon.folkhold.blocks.feature;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * A block of the library with a horizontal direction ({@link #FACING}). It calls the {@link BlockFeature} of the
 * block (see {@link BlockFeatures}), as {@link FeatureBlock} does.
 */
public abstract class HorizontalFeatureBlock extends HorizontalDirectionalBlock {
    protected HorizontalFeatureBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        InteractionResult result = BlockFeatures.of(this).useWithoutItem(state, level, pos, player, hitResult);
        return result != InteractionResult.PASS ? result : super.useWithoutItem(state, level, pos, player, hitResult);
    }

    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        BlockFeatures.of(this).playerWillDestroy(level, pos, state, player);
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        BlockFeatures.of(this).onPlace(state, level, pos, oldState, movedByPiston);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        BlockFeatures.of(this).onRemove(state, level, pos, newState, movedByPiston);
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        BlockFeatures.of(this).tick(state, level, pos, random);
    }
}
