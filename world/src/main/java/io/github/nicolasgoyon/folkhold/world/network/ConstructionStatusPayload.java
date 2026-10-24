package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the answer to a request of the construction menu or of the ghost, for example an error. The client
 * shows it in the status line of the menu, or above the hotbar.
 *
 * @param text    the text
 * @param success true for a success, false for an error
 */
public record ConstructionStatusPayload(Component text, boolean success) implements CustomPacketPayload {

    public static final Type<ConstructionStatusPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_status"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionStatusPayload> STREAM_CODEC =
            StreamCodec.composite(ComponentSerialization.TRUSTED_STREAM_CODEC, ConstructionStatusPayload::text,
                    ByteBufCodecs.BOOL, ConstructionStatusPayload::success, ConstructionStatusPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
