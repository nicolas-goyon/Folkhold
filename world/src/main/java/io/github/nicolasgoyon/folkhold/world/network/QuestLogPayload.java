package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the active quests of the player, one line for each quest. The quest HUD shows them.
 *
 * @param lines the lines
 */
public record QuestLogPayload(List<Component> lines) implements CustomPacketPayload {

    /** No active quest. */
    public static final QuestLogPayload EMPTY = new QuestLogPayload(List.of());

    public static final Type<QuestLogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "quest_log"));

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestLogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_STREAM_CODEC.apply(ByteBufCodecs.list()), QuestLogPayload::lines,
                    QuestLogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
