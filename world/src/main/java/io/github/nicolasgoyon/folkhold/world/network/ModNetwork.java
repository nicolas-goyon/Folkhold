package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.client.AdminScreen;
import io.github.nicolasgoyon.folkhold.world.client.BankPillageScreen;
import io.github.nicolasgoyon.folkhold.world.client.ClientQuestLog;
import io.github.nicolasgoyon.folkhold.world.client.ClientRouteView;
import io.github.nicolasgoyon.folkhold.world.client.ClientTerritory;
import io.github.nicolasgoyon.folkhold.world.client.ClientTerritoryMap;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionGhost;
import io.github.nicolasgoyon.folkhold.world.client.ConstructionScreen;
import io.github.nicolasgoyon.folkhold.world.client.InfoScreen;
import io.github.nicolasgoyon.folkhold.world.client.JournalScreen;
import io.github.nicolasgoyon.folkhold.world.client.NoticeToasts;
import io.github.nicolasgoyon.folkhold.world.client.QuestBoardScreen;
import io.github.nicolasgoyon.folkhold.world.client.TerritoryTitleHud;
import io.github.nicolasgoyon.folkhold.world.client.VillageHeartScreen;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement;
import io.github.nicolasgoyon.folkhold.world.journal.Journals;
import io.github.nicolasgoyon.folkhold.world.journal.RouteViews;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the world mod. The handlers of the client payloads run only on the client. The lambdas
 * refer to the client classes only in their bodies, so this class also loads on a dedicated server.
 *
 * <p>The server sends its payloads with {@link #send(ServerPlayer, CustomPacketPayload)}, which checks the channel
 * of the connection first.
 */
public final class ModNetwork {
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    /**
     * Sends a payload to a player. A connection with no channel for the payload (a vanilla client, a mock player
     * of the GameTests) gets nothing: the payload is dropped.
     *
     * @param player  the player
     * @param payload the payload
     * @return true if the payload is sent, false if the connection has no channel for it
     */
    public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TerritoryStatusPayload.TYPE, TerritoryStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientTerritory.set(payload));
        registrar.playToClient(TerritoryTitlePayload.TYPE, TerritoryTitlePayload.STREAM_CODEC,
                (payload, context) -> TerritoryTitleHud.show(payload));
        registrar.playToClient(TerritoryMapPayload.TYPE, TerritoryMapPayload.STREAM_CODEC,
                (payload, context) -> ClientTerritoryMap.set(payload));
        registrar.playToClient(VillageInfoPayload.TYPE, VillageInfoPayload.STREAM_CODEC,
                (payload, context) -> InfoScreen.open(payload));
        registrar.playToClient(VillageHeartPayload.TYPE, VillageHeartPayload.STREAM_CODEC,
                (payload, context) -> VillageHeartScreen.open(payload));
        registrar.playToServer(JournalRequestPayload.TYPE, JournalRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        Journals.request(player);
                    }
                });
        registrar.playToClient(JournalPayload.TYPE, JournalPayload.STREAM_CODEC,
                (payload, context) -> JournalScreen.open(payload));
        registrar.playToServer(RouteRequestPayload.TYPE, RouteRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        RouteViews.request(player, payload.fromId(), payload.toId(), payload.recalculate());
                    }
                });
        registrar.playToClient(RoutePayload.TYPE, RoutePayload.STREAM_CODEC,
                (payload, context) -> ClientRouteView.set(payload));
        registrar.playToServer(QuestBoardRequestPayload.TYPE, QuestBoardRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        QuestBoards.request(player, payload.settlementId());
                    }
                });
        registrar.playToClient(BankPillageRequestPayload.TYPE, BankPillageRequestPayload.STREAM_CODEC,
                (payload, context) -> BankPillageScreen.open(payload));
        registrar.playToServer(BankPillageConfirmPayload.TYPE, BankPillageConfirmPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        BankChests.onConfirm(player, payload.pos());
                    }
                });
        registrar.playToClient(QuestBoardPayload.TYPE, QuestBoardPayload.STREAM_CODEC,
                (payload, context) -> QuestBoardScreen.open(payload));
        registrar.playToClient(QuestLogPayload.TYPE, QuestLogPayload.STREAM_CODEC,
                (payload, context) -> ClientQuestLog.set(payload, context.player().level().getGameTime()));
        registrar.playToClient(NoticePayload.TYPE, NoticePayload.STREAM_CODEC,
                (payload, context) -> NoticeToasts.show(payload));
        registrar.playToServer(QuestActionPayload.TYPE, QuestActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        QuestBoards.handle(player, payload);
                    }
                });
        registrar.playToClient(AdminListPayload.TYPE, AdminListPayload.STREAM_CODEC,
                (payload, context) -> AdminScreen.open(payload));
        registrar.playToClient(AdminDetailsPayload.TYPE, AdminDetailsPayload.STREAM_CODEC,
                (payload, context) -> AdminScreen.showDetails(payload));
        registrar.playToClient(AdminResultPayload.TYPE, AdminResultPayload.STREAM_CODEC,
                (payload, context) -> AdminScreen.showResult(payload));
        registrar.playToServer(AdminActionPayload.TYPE, AdminActionPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        AdminActions.handle(player, payload);
                    }
                });
        registrar.playToClient(ConstructionTemplatesPayload.TYPE, ConstructionTemplatesPayload.STREAM_CODEC,
                (payload, context) -> ConstructionScreen.open(payload));
        registrar.playToServer(ConstructionPreviewRequestPayload.TYPE, ConstructionPreviewRequestPayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ConstructionPlacement.sendPreview(player, payload.template());
                    }
                });
        registrar.playToClient(ConstructionPreviewPayload.TYPE, ConstructionPreviewPayload.STREAM_CODEC,
                (payload, context) -> ConstructionGhost.start(payload));
        registrar.playToServer(ConstructionPlacePayload.TYPE, ConstructionPlacePayload.STREAM_CODEC,
                (payload, context) -> {
                    if (context.player() instanceof ServerPlayer player) {
                        ConstructionPlacement.place(player, payload);
                    }
                });
        registrar.playToClient(ConstructionStatusPayload.TYPE, ConstructionStatusPayload.STREAM_CODEC,
                (payload, context) -> ConstructionGhost.showStatus(payload));
        registrar.playToClient(ConstructionEndPayload.TYPE, ConstructionEndPayload.STREAM_CODEC,
                (payload, context) -> ConstructionGhost.onConstructionEnd(payload));
    }
}
