package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: all the territories in the areas of the vanilla maps near the player (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.MapTerritories}). The vanilla maps of the client show them. The
 * territories are not only the discovered territories.
 *
 * @param territories the villages with a territory in the areas, and the settlements that claim chunks near them
 */
public record MapTerritoryPayload(TerritoryMapPayload territories) implements CustomPacketPayload {
    public static final Type<MapTerritoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "map_territories"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MapTerritoryPayload> STREAM_CODEC =
            TerritoryMapPayload.STREAM_CODEC.map(MapTerritoryPayload::new, MapTerritoryPayload::territories);

    /** No territories. */
    public static final MapTerritoryPayload EMPTY = new MapTerritoryPayload(TerritoryMapPayload.EMPTY);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
