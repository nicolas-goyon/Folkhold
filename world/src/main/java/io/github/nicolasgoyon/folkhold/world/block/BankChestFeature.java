package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The features of the bank chest block of the library (see {@link BlockFeature}). The chest shows the stocks of the
 * village and a part of the treasury of its faction. To open or break it is a pillage (see {@link BankChests}). In
 * ruins, it is a normal chest.
 */
public final class BankChestFeature implements BlockFeature {
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BankChestBlockEntity(pos, state);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            BankChests.onUse(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** With no active village, the chest drops its items (see {@link BankChests#onChestRemoved}). */
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof BankChestBlockEntity chest) {
            BankChests.onChestRemoved(serverLevel, pos, chest);
        }
    }
}
