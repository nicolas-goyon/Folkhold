package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Client to server: the journal asks for the route of the caravans between two villages of the journal of the
 * player. The server answers with a {@link RoutePayload} (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.RouteViews}).
 *
 * @param fromId the settlement id of the start
 * @param toId   the settlement id of the end
 */
public record RouteRequestPayload(String fromId, String toId) implements CustomPacketPayload {

    public static final Type<RouteRequestPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "route_request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, RouteRequestPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.STRING_UTF8, RouteRequestPayload::fromId, ByteBufCodecs.STRING_UTF8,
                    RouteRequestPayload::toId, RouteRequestPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
