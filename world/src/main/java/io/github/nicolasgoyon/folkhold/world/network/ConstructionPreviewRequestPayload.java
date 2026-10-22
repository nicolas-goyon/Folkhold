package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: an operator selects a structure template in the construction menu. The server checks the
 * permission and the size of the template, then sends the blocks of the template for the ghost
 * ({@link ConstructionPreviewPayload}).
 *
 * @param template the id of the template
 */
public record ConstructionPreviewRequestPayload(ResourceLocation template) implements CustomPacketPayload {

    public static final Type<ConstructionPreviewRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "construction_preview_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ConstructionPreviewRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ResourceLocation.STREAM_CODEC, ConstructionPreviewRequestPayload::template,
                    ConstructionPreviewRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
