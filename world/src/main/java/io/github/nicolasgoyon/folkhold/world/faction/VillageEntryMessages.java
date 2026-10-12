package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Shows a message in the action bar when a player enters the area of a faction village:
 * the faction name and the reputation tier of the player in this village.
 */
public final class VillageEntryMessages {
    /** Radius of the village area for the message, in blocks. The village structure is 39 × 39 blocks. */
    private static final double ENTRY_RADIUS = 28.0;
    private static final int CHECK_INTERVAL = 20;
    private static final Map<UUID, BlockPos> CURRENT = new HashMap<>();

    private VillageEntryMessages() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        Optional<BlockPos> heartPos = VillageHearts.findNearest(level, player.blockPosition(), ENTRY_RADIUS);
        BlockPos previous = CURRENT.get(player.getUUID());
        if (heartPos.isEmpty()) {
            CURRENT.remove(player.getUUID());
            return;
        }
        if (heartPos.get().equals(previous)) {
            return;
        }
        CURRENT.put(player.getUUID(), heartPos.get());
        if (level.getBlockEntity(heartPos.get()) instanceof VillageHeartBlockEntity heart) {
            FactionVisuals.faction(level.getServer(), heart.getFactionId()).ifPresent(faction -> {
                Component tier = Component.translatable("folkhold_world.reputation_tier."
                        + Reputations.tier(level.getServer(), player, heart).serializedName());
                player.displayClientMessage(Component.translatable("message.folkhold_world.village_entry",
                        FactionVisuals.name(faction), tier), true);
            });
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        CURRENT.clear();
    }
}
