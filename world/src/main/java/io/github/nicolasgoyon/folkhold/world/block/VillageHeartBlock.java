package io.github.nicolasgoyon.folkhold.world.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlockEntities;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
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
 * <p>A right click shows the faction, the alert state and the reputation tier of the player. When the block is removed, the units lose their village.
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

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            Component state = Component.translatable(heart.getAlertState().translationKey());
            Optional<FactionSpec> faction = FactionVisuals.faction(level.getServer(), heart.getFactionId());
            if (faction.isPresent() && level.getServer() != null) {
                Component tier = Component.translatable("folkhold_world.reputation_tier."
                        + Reputations.tier(level.getServer(), player, heart).serializedName());
                player.displayClientMessage(Component.translatable("message.folkhold_world.village_heart.status",
                        FactionVisuals.name(faction.get()), state, tier), true);
            } else {
                player.displayClientMessage(Component.translatable(
                        "message.folkhold_world.village_heart.status_no_faction", state), true);
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
            heart.onDestroyed();
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }
}
