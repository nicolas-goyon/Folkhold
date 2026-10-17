package io.github.nicolasgoyon.folkhold.world.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import io.github.nicolasgoyon.folkhold.world.village.VillageInfo;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Village heart: the block at the center of a faction village. It holds the state of the village.
 *
 * <p>A right click opens a screen with the village information. When the block is removed, the units lose their
 * village. If the captain of the village is dead, the village becomes ruins (see
 * {@link VillageHeartBlockEntity#onDestroyed()}).
 */
public class VillageHeartBlock extends BaseEntityBlock {
    public static final MapCodec<VillageHeartBlock> CODEC = simpleCodec(VillageHeartBlock::new);

    public VillageHeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new VillageHeartBlockEntity(pos, state);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    @Nullable
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state,
            BlockEntityType<T> type) {
        return level.isClientSide()
                ? null
                : createTickerHelper(type, ModBlockEntities.VILLAGE_HEART.get(), VillageHeartBlockEntity::serverTick);
    }

    /** A right click opens the village heart screen (see {@link VillageInfo}). */
    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.ensureSettlement();
            ModNetwork.send(serverPlayer, VillageInfo.build(heart, serverPlayer));
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** Records the player who breaks the heart, as the cause of a destruction. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.setBreaker(serverPlayer);
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.onDestroyed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
