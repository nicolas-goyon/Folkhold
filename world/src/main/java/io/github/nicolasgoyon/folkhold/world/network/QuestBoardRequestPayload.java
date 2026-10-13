package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player opens the quest board screen of a village from the village heart screen. The server
 * checks that the player is near the village and that the village gives quests to the player.
 *
 * @param settlementId the id of the village
 */
public record QuestBoardRequestPayload(String settlementId) implements CustomPacketPayload {

    public static final Type<QuestBoardRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "quest_board_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestBoardRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, QuestBoardRequestPayload::settlementId,
                    QuestBoardRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
