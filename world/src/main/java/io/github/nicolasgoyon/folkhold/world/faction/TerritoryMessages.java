package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryStatusPayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryTitlePayload;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementLevels;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.settlement.Territories;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
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
 *   <li><b>territory message</b>: when a player enters the territory of a faction, a banner of the HUD (the vanilla
 *       title for a client with no channel) shows "Lands of" and the faction name in its color, and the reputation
 *       tier of the player with its glyph and color. This is an
 *       early warning before the villages. When a player enters an unclaimed cell, a title shows "Unclaimed
 *       territory".</li>
 *   <li><b>village message</b>: when a player comes near a village (see {@link WorldConfig#VILLAGE_MESSAGE_RADIUS}),
 *       the action bar shows the village name, the faction, the level and tier of the village, the reputation tier
 *       in this village, and what the guards do.</li>
 *   <li><b>territory status</b>: the server sends the territory, the tier and the nearest village of the
 *       territory (also a village that the game did not generate yet) to the client when they change. Out of the
 *       territories, the village is the village near the player. The client shows them in the territory HUD, if
 *       the player turns it on.</li>
 * </ul>
 *
 * <p>The territories are in the overworld only. The settlements claim the territory cells (see
 * {@link Territories}).
 */
public final class TerritoryMessages {
    private static final int CHECK_INTERVAL = 20;
    /** Title timing, in ticks: fade in, stay, fade out. */
    private static final int TITLE_FADE_IN = 10;
    private static final int TITLE_STAY = 50;
    private static final int TITLE_FADE_OUT = 20;
    private static final String VOWELS = "AEIOUYaeiouyÉéÈèÂâÎîÔôÛû";
    private static final Map<UUID, State> STATES = new HashMap<>();
    /** Territory id of an unclaimed cell. A faction id is never empty, so it cannot be this value. */
    private static final String UNCLAIMED = "";
    /** Territory id outside the overworld. */
    private static final String OUTSIDE = "-";

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
        boolean overworld = level.dimension() == Level.OVERWORLD;
        Optional<FactionSpec> territory = overworld
                ? Territories.factionAt(server, player.blockPosition())
                : Optional.empty();
        String territoryId = !overworld ? OUTSIDE : territory.map(FactionSpec::id).orElse(UNCLAIMED);
        Optional<VillageHeartBlockEntity> village = VillageHearts
                .findNearest(level, player.blockPosition(), WorldConfig.VILLAGE_MESSAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast);
        BlockPos villagePos = village.map(VillageHeartBlockEntity::getBlockPos).orElse(null);
        State previous = STATES.get(player.getUUID());

        // No title for the first check after the login: the player did not cross a border.
        if (previous != null && !previous.territoryId().equals(territoryId) && overworld) {
            if (territory.isPresent()) {
                showTerritoryTitle(player, territory.get());
            } else {
                showUnclaimedTitle(player);
            }
        }
        if (village.isPresent() && (previous == null || !village.get().getBlockPos().equals(previous.village()))) {
            showVillageMessage(server, player, village.get());
        }

        TerritoryStatusPayload status = overworld
                ? status(server, player, territory, village)
                : TerritoryStatusPayload.NONE;
        if (previous == null || !status.equals(previous.status())) {
            PacketDistributor.sendToPlayer(player, status);
        }
        STATES.put(player.getUUID(), new State(territoryId, villagePos, status));
    }

    private static void showUnclaimedTitle(ServerPlayer player) {
        showTitle(player, unclaimedName(), null, -1);
    }

    /**
     * Shows the territory title: a banner of the HUD for a client with the channel, else the vanilla title.
     *
     * @param title    the name of the territory
     * @param subtitle the tier of the player, or null
     * @param color    the color of the faction, or -1
     */
    private static void showTitle(ServerPlayer player, Component title, @Nullable Component subtitle, int color) {
        if (player.connection.hasChannel(TerritoryTitlePayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, new TerritoryTitlePayload(title, Optional.ofNullable(subtitle),
                    color));
            return;
        }
        player.connection.send(new ClientboundSetTitlesAnimationPacket(TITLE_FADE_IN, TITLE_STAY, TITLE_FADE_OUT));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle == null ? Component.empty() : subtitle));
        player.connection.send(new ClientboundSetTitleTextPacket(title));
    }

    private static Component unclaimedName() {
        return Component.translatable("message.folkhold_world.unclaimed_territory").withStyle(ChatFormatting.GRAY);
    }

    private static void showTerritoryTitle(ServerPlayer player, FactionSpec faction) {
        Component tier = tierLabel(Reputations.tier(player.server, player, faction.id()));
        // A second key for a name that starts with a vowel: some languages change the word before it ("d'").
        boolean vowel = !faction.name().isEmpty() && VOWELS.indexOf(faction.name().charAt(0)) >= 0;
        Component title = Component.translatable("message.folkhold_world.territory_title" + (vowel ? ".vowel" : ""),
                faction.name()).withColor(faction.color());
        showTitle(player, title, Component.translatable("message.folkhold_world.territory_entry", tier),
                faction.color());
    }

    private static void showVillageMessage(MinecraftServer server, ServerPlayer player, VillageHeartBlockEntity heart) {
        FactionVisuals.faction(server, heart.getFactionId()).ifPresent(faction -> {
            ReputationTier tier = Reputations.tier(server, player, heart);
            Component mood = Component.translatable("message.folkhold_world.village_mood." + tier.serializedName(),
                    tierLabel(tier)).withColor(tier.color());
            String name = heart.getVillageName();
            Component level = Settlements.of(server, heart)
                    .<Component>map(settlement -> Component.translatable("message.folkhold_world.village_level",
                            SettlementLevels.tierName(settlement.level()), settlement.level())
                            .withStyle(ChatFormatting.GRAY))
                    .orElse(Component.empty());
            Component message = name != null
                    ? Component.translatable("message.folkhold_world.village_entry",
                            Component.literal(name).withStyle(ChatFormatting.BOLD), FactionVisuals.name(faction),
                            level, mood)
                    : Component.translatable("message.folkhold_world.village_entry_no_name",
                            FactionVisuals.name(faction), level, mood);
            player.displayClientMessage(message, true);
        });
    }

    private static TerritoryStatusPayload status(MinecraftServer server, ServerPlayer player,
            Optional<FactionSpec> territory, Optional<VillageHeartBlockEntity> village) {
        Optional<Component> villageName = village
                .map(VillageHeartBlockEntity::getVillageName)
                .map(Component::literal);
        Optional<BlockPos> villagePos = village.map(VillageHeartBlockEntity::getBlockPos);
        if (territory.isEmpty()) {
            return new TerritoryStatusPayload(Optional.of(unclaimedName()), -1, Optional.empty(), villageName,
                    villagePos);
        }
        ReputationTier tier = village
                .filter(heart -> territory.get().id().equals(heart.getFactionId()))
                .map(heart -> Reputations.tier(server, player, heart))
                .orElseGet(() -> Reputations.tier(server, player, territory.get().id()));
        Optional<Settlement> nearest = nearestOfFaction(server, territory.get().id(), player.blockPosition());
        return new TerritoryStatusPayload(Optional.of(FactionVisuals.name(territory.get())), territory.get().color(),
                Optional.of(tier.serializedName()),
                nearest.<Component>map(settlement -> Component.literal(settlement.name())).or(() -> villageName),
                nearest.map(settlement -> new BlockPos(settlement.x(),
                        settlement.hasHeart() ? settlement.heartY() : server.overworld().getSeaLevel(), settlement.z()))
                        .or(() -> villagePos));
    }

    /**
     * The nearest active settlement of a faction, also a settlement that the game did not generate yet.
     *
     * @param server    the server
     * @param factionId the faction
     * @param pos       the position of the player
     * @return the settlement, or empty if the faction has no active settlement
     */
    public static Optional<Settlement> nearestOfFaction(MinecraftServer server, String factionId, BlockPos pos) {
        return SettlementData.get(server).registry().all().stream()
                .filter(Settlement::isActive)
                .filter(settlement -> factionId.equals(settlement.factionId()))
                .min(Comparator.comparingLong(settlement -> settlement.distanceSquared(pos.getX(), pos.getZ())));
    }

    /** The tier with its glyph, in its color: "✓ Friend". */
    private static Component tierLabel(ReputationTier tier) {
        return Component.literal(tier.glyph() + " ")
                .append(Component.translatable("folkhold_world.reputation_tier_title." + tier.serializedName()))
                .withColor(tier.color());
    }

    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        STATES.remove(event.getEntity().getUUID());
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        STATES.clear();
    }
}
