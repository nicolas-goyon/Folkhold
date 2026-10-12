package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryStatusPayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * Territory and village information for the players:
 * <ul>
 *   <li><b>territory message</b>: when a player enters the territory of a faction, a title shows the faction
 *       name and the reputation tier of the player. This is an early warning before the villages.</li>
 *   <li><b>village message</b>: when a player comes near a village (see {@link WorldConfig#VILLAGE_MESSAGE_RADIUS}),
 *       the action bar shows the village name, the faction, the level and tier of the village, and the reputation
 *       tier in this village.</li>
 *   <li><b>territory status</b>: the server sends the territory, the tier and the village to the client when
 *       they change. The client shows them in the territory HUD, if the player turns it on.</li>
 * </ul>
 *
 * <p>The territories are in the overworld only.
 */
public final class TerritoryMessages {
    private static final int CHECK_INTERVAL = 20;
    /** Title timing, in ticks: fade in, stay, fade out. */
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 50;
    private static final int TITLE_FADE_OUT = 20;
    private static final Map<UUID, State> STATES = new HashMap<>();

    /** What the server knows about one player: the last territory, the last village and the last status. */
    private record State(String territoryId, @Nullable BlockPos village, TerritoryStatusPayload status) {
    }

    private TerritoryMessages() {
    }

    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || player.tickCount % CHECK_INTERVAL != 0) {
            return;
        }
        ServerLevel level = player.serverLevel();
        MinecraftServer server = level.getServer();
        Optional<FactionSpec> territory = territoryAt(level, player.blockPosition());
        String territoryId = territory.map(FactionSpec::id).orElse("");
        Optional<VillageHeartBlockEntity> village = VillageHearts
                .findNearest(level, player.blockPosition(), WorldConfig.VILLAGE_MESSAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast);
        BlockPos villagePos = village.map(VillageHeartBlockEntity::getBlockPos).orElse(null);
        State previous = STATES.get(player.getUUID());

        // No title for the first check after the login: the player did not cross a border.
        if (previous != null && !previous.territoryId().equals(territoryId) && territory.isPresent()) {
            showTerritoryTitle(player, territory.get());
        }
        if (village.isPresent() && (previous == null || !village.get().getBlockPos().equals(previous.village()))) {
            showVillageMessage(server, player, village.get());
        }

        TerritoryStatusPayload status = status(server, player, territory, village);
        if (previous == null || !status.equals(previous.status())) {
            PacketDistributor.sendToPlayer(player, status);
        }
        STATES.put(player.getUUID(), new State(territoryId, villagePos, status));
    }

    /** The faction that owns the position, in the overworld only. */
    private static Optional<FactionSpec> territoryAt(ServerLevel level, BlockPos pos) {
        if (level.dimension() != Level.OVERWORLD) {
            return Optional.empty();
        }
        FactionData data = FactionData.get(level.getServer());
        return data.factions().isEmpty() ? Optional.empty() : Optional.of(data.ownerAt(pos));
    }

    private static void showTerritoryTitle(ServerPlayer player, FactionSpec faction) {
        Component tier = tierName(Reputations.tier(player.server, player, faction.id()));
        player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
        player.connection.send(new ClientboundSetSubtitleTextPacket(
                Component.translatable("message.folkhold_world.territory_entry", tier)));
        player.connection.send(new ClientboundSetTitleTextPacket(FactionVisuals.name(faction)));
    }

    private static void showVillageMessage(MinecraftServer server, ServerPlayer player, VillageHeartBlockEntity heart) {
        FactionVisuals.faction(server, heart.getFactionId()).ifPresent(faction -> {
            Component tier = tierName(Reputations.tier(server, player, heart));
            String name = heart.getVillageName();
            Component level = Settlements.of(server, heart)
                    .<Component>map(settlement -> Component.translatable("message.folkhold_world.village_level",
                            SettlementLevels.tierName(settlement.level()), settlement.level()))
                    .orElse(Component.empty());
            Component message = name != null
                    ? Component.translatable("message.folkhold_world.village_entry", name,
                            FactionVisuals.name(faction), level, tier)
                    : Component.translatable("message.folkhold_world.village_entry_no_name",
                            FactionVisuals.name(faction), level, tier);
            player.displayClientMessage(message, true);
        });
    }

    private static TerritoryStatusPayload status(MinecraftServer server, ServerPlayer player,
            Optional<FactionSpec> territory, Optional<VillageHeartBlockEntity> village) {
        if (territory.isEmpty()) {
            return TerritoryStatusPayload.NONE;
        }
        ReputationTier tier = village
                .filter(heart -> territory.get().id().equals(heart.getFactionId()))
                .map(heart -> Reputations.tier(server, player, heart))
                .orElseGet(() -> Reputations.tier(server, player, territory.get().id()));
        Optional<Component> villageName = village
                .map(VillageHeartBlockEntity::getVillageName)
                .map(Component::literal);
        return new TerritoryStatusPayload(Optional.of(FactionVisuals.name(territory.get())),
                Optional.of(tierName(tier)), villageName);
    }

    private static Component tierName(ReputationTier tier) {
        return Component.translatable("folkhold_world.reputation_tier." + tier.serializedName());
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }
}
