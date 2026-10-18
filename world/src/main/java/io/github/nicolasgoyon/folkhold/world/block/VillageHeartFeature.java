package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.VillageInfo;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The features of the village heart block of the library (see {@link BlockFeature}).
 *
 * <ul>
 *   <li>The block entity holds the state of the village ({@link VillageHeartBlockEntity}). It ticks on the
 *       server.</li>
 *   <li>A right click opens a screen with the village information (see {@link VillageInfo}).</li>
 *   <li>When the block is removed, the units lose their village. If the captain of the village is dead, the
 *       village becomes ruins (see {@link VillageHeartBlockEntity#onDestroyed()}). The player who breaks the heart
 *       is the cause of the destruction.</li>
 * </ul>
 */
public final class VillageHeartFeature implements BlockFeature {
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VillageHeartBlockEntity(pos, state);
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : BlockFeature.ticker(type, ModBlockEntities.VILLAGE_HEART.get(), VillageHeartBlockEntity::serverTick);
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.ensureSettlement();
            ModNetwork.send(serverPlayer, VillageInfo.build(heart, serverPlayer));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.setBreaker(serverPlayer);
        }
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.onDestroyed();
        }
    }
}
