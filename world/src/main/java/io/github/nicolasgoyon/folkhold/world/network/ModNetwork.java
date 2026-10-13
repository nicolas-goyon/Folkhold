package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.admin.AdminActions;
import io.github.nicolasgoyon.folkhold.world.client.AdminScreen;
import io.github.nicolasgoyon.folkhold.world.client.BankPillageScreen;
import io.github.nicolasgoyon.folkhold.world.client.ClientQuestLog;
import io.github.nicolasgoyon.folkhold.world.client.ClientTerritory;
import io.github.nicolasgoyon.folkhold.world.client.InfoScreen;
import io.github.nicolasgoyon.folkhold.world.client.NoticeToasts;
import io.github.nicolasgoyon.folkhold.world.client.QuestBoardScreen;
import io.github.nicolasgoyon.folkhold.world.client.VillageHeartScreen;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.village.BankChests;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the world mod. The handlers of the client payloads run only on the client. The lambdas
 * refer to the client classes only in their bodies, so this class also loads on a dedicated server.
 */
public final class ModNetwork {
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TerritoryStatusPayload.TYPE, TerritoryStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientTerritory.set(payload));
        registrar.playToClient(VillageInfoPayload.TYPE, VillageInfoPayload.STREAM_CODEC,
                (payload, context) -> InfoScreen.open(payload));
        registrar.playToClient(VillageHeartPayload.TYPE, VillageHeartPayload.STREAM_CODEC,
                (payload, context) -> VillageHeartScreen.open(payload));
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
                (payload, context) -> ClientQuestLog.set(payload));
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
    }
}
