package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: all the territories in the areas of the vanilla maps near the player (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.MapTerritories}). The vanilla maps of the client show them. The
 * territories are not only the discovered territories. The client does not know the center of a vanilla map: the
 * payload gives the center of each map.
 *
 * @param territories the villages with a territory in the areas, and the settlements that claim chunks near them
 * @param maps        the id and the center of each map
 */
public record MapTerritoryPayload(TerritoryMapPayload territories, List<MapCenter> maps)
        implements CustomPacketPayload {
    public static final Type<MapTerritoryPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "map_territories"));

    /** The center of a map. */
    public static final StreamCodec<FriendlyByteBuf, MapCenter> MAP_CENTER_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, MapCenter::id,
            ByteBufCodecs.VAR_INT, MapCenter::x,
            ByteBufCodecs.VAR_INT, MapCenter::z,
            MapCenter::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, MapTerritoryPayload> STREAM_CODEC =
            StreamCodec.composite(
                    TerritoryMapPayload.STREAM_CODEC, MapTerritoryPayload::territories,
                    MAP_CENTER_CODEC.apply(ByteBufCodecs.list()), MapTerritoryPayload::maps,
                    MapTerritoryPayload::new);

    /** No territories. */
    public static final MapTerritoryPayload EMPTY = new MapTerritoryPayload(TerritoryMapPayload.EMPTY, List.of());

    public MapTerritoryPayload {
        maps = List.copyOf(maps);
    }

    /**
     * The center of a vanilla map.
     *
     * @param id the id of the map
     * @param x  the X of the center, in blocks
     * @param z  the Z of the center, in blocks
     */
    public record MapCenter(int id, int x, int z) {
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
