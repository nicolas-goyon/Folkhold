package io.github.nicolasgoyon.folkhold.events.network;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: a gust of a storm pushes the player. The client adds the push to the motion of its player: the
 * player keeps its own motion.
 *
 * @param x the push along X, in blocks per tick
 * @param y the push along Y, in blocks per tick
 * @param z the push along Z, in blocks per tick
 */
public record WindGustPayload(float x, float y, float z) implements CustomPacketPayload {

    public static final Type<WindGustPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdEvents.MOD_ID, "wind_gust"));

    public static final StreamCodec<RegistryFriendlyByteBuf, WindGustPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, WindGustPayload::x,
                    ByteBufCodecs.FLOAT, WindGustPayload::y,
                    ByteBufCodecs.FLOAT, WindGustPayload::z,
                    WindGustPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
