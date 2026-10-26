package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the map of the journal asks for the relief of the discovered territories of the player. The
 * server answers with {@link MapReliefPayload} (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.TerritoryReliefs}).
 */
public record MapReliefRequestPayload() implements CustomPacketPayload {
    /** The request. It has no data. */
    public static final MapReliefRequestPayload INSTANCE = new MapReliefRequestPayload();

    public static final Type<MapReliefRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "map_relief_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapReliefRequestPayload> STREAM_CODEC =
            StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
