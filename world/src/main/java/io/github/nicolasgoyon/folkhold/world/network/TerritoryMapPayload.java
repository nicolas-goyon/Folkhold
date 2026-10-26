package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the territories and the villages that the player discovered, for the maps (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.TerritoryMaps}). The client calculates the owner of each chunk
 * from the claims, as the server does.
 *
 * @param villages the discovered villages with a territory, in the order of the discovery
 * @param others   the other settlements that claim chunks near the discovered villages. The borders of the
 *                 discovered territories depend on them, but the maps do not show their territories.
 */
public record TerritoryMapPayload(List<Village> villages, List<Claimant> others) implements CustomPacketPayload {

    public static final Type<TerritoryMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "territory_map"));

    /** The claim of a settlement: id, position, level and claim radius. */
    public static final StreamCodec<FriendlyByteBuf, Claimant> CLAIMANT_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Claimant::id,
            ByteBufCodecs.VAR_INT, Claimant::x,
            ByteBufCodecs.VAR_INT, Claimant::z,
            ByteBufCodecs.VAR_INT, Claimant::level,
            ByteBufCodecs.VAR_INT, Claimant::radius,
            Claimant::new);

    /** A discovered village. */
    public static final StreamCodec<FriendlyByteBuf, Village> VILLAGE_CODEC = StreamCodec.composite(
            CLAIMANT_CODEC, Village::claimant,
            ByteBufCodecs.VAR_INT, Village::y,
            ByteBufCodecs.STRING_UTF8, Village::name,
            ByteBufCodecs.STRING_UTF8, Village::faction,
            ByteBufCodecs.INT, Village::color,
            Village::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryMapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    VILLAGE_CODEC.apply(ByteBufCodecs.list()), TerritoryMapPayload::villages,
                    CLAIMANT_CODEC.apply(ByteBufCodecs.list()), TerritoryMapPayload::others,
                    TerritoryMapPayload::new);

    /** No discovered territories. */
    public static final TerritoryMapPayload EMPTY = new TerritoryMapPayload(List.of(), List.of());

    public TerritoryMapPayload {
        villages = List.copyOf(villages);
        others = List.copyOf(others);
    }

    /**
     * A discovered village with a territory.
     *
     * @param claimant the claim of the village
     * @param y        the Y of the heart of the village, for a waypoint
     * @param name     the name of the village
     * @param faction  the name of the faction of the village
     * @param color    the color of the faction, as 0xRRGGBB
     */
    public record Village(Claimant claimant, int y, String name, String faction, int color) {

        /** The id of the settlement of the village. */
        public String id() {
            return claimant.id();
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
