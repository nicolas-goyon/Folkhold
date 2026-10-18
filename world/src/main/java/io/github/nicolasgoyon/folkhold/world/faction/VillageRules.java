package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Rules for neutral players in a faction village. These actions are offenses (see
 * {@link VillageHeartBlockEntity#recordOffense(ServerPlayer)}):
 * <ul>
 *   <li>break a block in the village radius;</li>
 *   <li>open a container (chest, barrel, furnace and so on) in the village radius.</li>
 * </ul>
 *
 * <p>Hostile players are attacked on sight, so their actions are not offenses. Friendly players can act freely.
 * Creative and spectator players are ignored.
 */
public final class VillageRules {

    private VillageRules() {
    }

    public static void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level) {
            check(level, player, event.getPos());
        }
    }

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getLevel() instanceof ServerLevel level
                && level.getBlockEntity(event.getPos()) instanceof Container
                && !level.getBlockState(event.getPos()).is(ModBlocks.BANK_CHEST.get())) {
            check(level, player, event.getPos());
        }
    }

    private static void check(ServerLevel level, ServerPlayer player, BlockPos pos) {
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        Optional<VillageHeartBlockEntity> heart = VillageHearts
                .findNearest(level, pos, WorldConfig.VILLAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast)
                .filter(h -> h.getFactionId() != null);
        if (heart.isPresent() && Reputations.tier(level.getServer(), player, heart.get()) == ReputationTier.NEUTRAL) {
            heart.get().recordOffense(player);
        }
    }
}
