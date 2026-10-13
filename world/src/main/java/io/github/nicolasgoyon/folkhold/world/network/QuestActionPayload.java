package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: a button of the quest board screen (see
 * {@link io.github.nicolasgoyon.folkhold.world.quest.QuestBoards}).
 *
 * @param action       the action: {@link QuestBoardPayload#ACTION_ACCEPT}, {@link QuestBoardPayload#ACTION_ABANDON}
 *                     or {@link QuestBoardPayload#ACTION_DELIVER}
 * @param questId      the id of the quest
 * @param settlementId the id of the village of the screen
 */
public record QuestActionPayload(int action, String questId, String settlementId) implements CustomPacketPayload {

    public static final Type<QuestActionPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "quest_action"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestActionPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, QuestActionPayload::action,
                    ByteBufCodecs.STRING_UTF8, QuestActionPayload::questId,
                    ByteBufCodecs.STRING_UTF8, QuestActionPayload::settlementId,
                    QuestActionPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
