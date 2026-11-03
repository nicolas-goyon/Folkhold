package io.github.nicolasgoyon.folkhold.events.network;

import io.github.nicolasgoyon.folkhold.events.FolkholdEvents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the camera of the player shakes for a time.
 *
 * @param amplitude the strength of the shake, in degrees of rotation
 * @param ticks     the duration of the shake, in ticks
 */
public record EarthquakeShakePayload(float amplitude, int ticks) implements CustomPacketPayload {

    public static final Type<EarthquakeShakePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdEvents.MOD_ID, "earthquake_shake"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EarthquakeShakePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.FLOAT, EarthquakeShakePayload::amplitude,
                    ByteBufCodecs.VAR_INT, EarthquakeShakePayload::ticks,
                    EarthquakeShakePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
