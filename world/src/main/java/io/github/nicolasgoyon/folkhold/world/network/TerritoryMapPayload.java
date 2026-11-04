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
 * Server to client: the territories that the player discovered (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.TerritoryMaps}). The tab "Map" of the journal uses them for its
 * legend, for the button "Show all" and for its relief. The maps of Cartofold get the chunks of the territories in a
 * layer (see {@link io.github.nicolasgoyon.folkhold.world.journal.CartofoldMaps}).
 *
 * @param villages the discovered villages with a territory, in the order of the discovery
 */
public record TerritoryMapPayload(List<Village> villages) implements CustomPacketPayload {

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
            ByteBufCodecs.STRING_UTF8, Village::name,
            ByteBufCodecs.STRING_UTF8, Village::faction,
            ByteBufCodecs.INT, Village::color,
            Village::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, TerritoryMapPayload> STREAM_CODEC =
            StreamCodec.composite(
                    VILLAGE_CODEC.apply(ByteBufCodecs.list()), TerritoryMapPayload::villages,
                    TerritoryMapPayload::new);

    /** No discovered territories. */
    public static final TerritoryMapPayload EMPTY = new TerritoryMapPayload(List.of());

    public TerritoryMapPayload {
        villages = List.copyOf(villages);
    }

    /**
     * A discovered village with a territory.
     *
     * @param claimant the claim of the village
     * @param name     the name of the village
     * @param faction  the name of the faction of the village
     * @param color    the color of the faction, as 0xRRGGBB
     */
    public record Village(Claimant claimant, String name, String faction, int color) {

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
