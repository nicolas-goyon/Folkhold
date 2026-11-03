package io.github.nicolasgoyon.folkhold.balance.network;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the links of one minecart. The server sends it when a link changes and when a player starts to
 * track a minecart with a link. The client draws a chain for each link.
 *
 * @param cartId  the entity id of the minecart
 * @param frontId the entity id of the minecart in front, or -1 for no link or a minecart that is not loaded
 * @param backId  the entity id of the minecart behind, or -1 for no link or a minecart that is not loaded
 */
public record CartLinkPayload(int cartId, int frontId, int backId) implements CustomPacketPayload {
    /** The value of {@link #frontId} and {@link #backId} for no minecart. */
    public static final int NONE = -1;

    public static final Type<CartLinkPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdBalance.MOD_ID, "cart_link"));

    public static final StreamCodec<ByteBuf, CartLinkPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, CartLinkPayload::cartId,
            ByteBufCodecs.VAR_INT, CartLinkPayload::frontId,
            ByteBufCodecs.VAR_INT, CartLinkPayload::backId,
            CartLinkPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
