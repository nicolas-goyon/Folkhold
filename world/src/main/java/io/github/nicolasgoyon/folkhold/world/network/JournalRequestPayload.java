package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the player opens the journal, or refreshes it. The server answers with the journal of the player
 * ({@link JournalPayload}).
 */
public record JournalRequestPayload() implements CustomPacketPayload {
    /** The request. It has no data. */
    public static final JournalRequestPayload INSTANCE = new JournalRequestPayload();

    public static final Type<JournalRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "journal_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, JournalRequestPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
