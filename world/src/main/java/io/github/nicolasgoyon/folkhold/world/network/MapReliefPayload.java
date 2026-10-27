package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Relief;
import javax.annotation.Nullable;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the relief of the map of the journal, around the discovered territories of the player (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.TerritoryReliefs}).
 *
 * @param status the state of the relief
 * @param relief the relief when the status is {@link Status#READY}, else null
 */
public record MapReliefPayload(Status status, @Nullable Relief relief) implements CustomPacketPayload {

    public static final Type<MapReliefPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "map_relief"));

    /** The player discovered no territory: the map has no relief. */
    public static final MapReliefPayload EMPTY = new MapReliefPayload(Status.EMPTY, null);
    /** The server calculates the relief. It sends the relief when the calculation ends. */
    public static final MapReliefPayload PENDING = new MapReliefPayload(Status.PENDING, null);

    public static final StreamCodec<RegistryFriendlyByteBuf, MapReliefPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeEnum(payload.status);
                if (payload.relief != null) {
                    payload.relief.write(buf);
                }
            },
            buf -> {
                Status status = buf.readEnum(Status.class);
                return new MapReliefPayload(status, status == Status.READY ? Relief.read(buf) : null);
            });

    /** The state of the relief. */
    public enum Status {
        /** No discovered territory. */
        EMPTY,
        /** The server calculates the relief. */
        PENDING,
        /** The payload has the relief. */
        READY
    }

    public MapReliefPayload {
        if ((status == Status.READY) != (relief != null)) {
            throw new IllegalArgumentException("Only a ready payload has a relief: " + status);
        }
    }

    /**
     * A payload with a relief.
     *
     * @param relief the relief of the map
     * @return the payload
     */
    public static MapReliefPayload ready(Relief relief) {
        return new MapReliefPayload(Status.READY, relief);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
