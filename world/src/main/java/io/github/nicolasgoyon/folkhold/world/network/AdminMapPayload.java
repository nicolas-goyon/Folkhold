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
 * Server to client: the map of the admin screen (see {@link io.github.nicolasgoyon.folkhold.world.admin.AdminMap}).
 * The client opens the map screen, or updates it.
 *
 * @param territories the territories of all the active settlements
 * @param marks       the settlements, the caravans and the traveling merchants
 */
public record AdminMapPayload(TerritoryMapPayload territories, List<Mark> marks) implements CustomPacketPayload {
    /** A village in the world: its heart exists. */
    public static final int VILLAGE = 0;
    /** A settlement that exists only in the simulation: the game did not generate its chunks. */
    public static final int SIMULATED = 1;
    /** A ruin. */
    public static final int RUIN = 2;
    /** A caravan. */
    public static final int CARAVAN = 3;
    /** A traveling merchant. */
    public static final int MERCHANT = 4;

    public static final Type<AdminMapPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_map"));

    /** A mark of the map. */
    public static final StreamCodec<FriendlyByteBuf, Mark> MARK_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, Mark::id,
            ByteBufCodecs.STRING_UTF8, Mark::name,
            ByteBufCodecs.VAR_INT, Mark::kind,
            ByteBufCodecs.VAR_INT, Mark::x,
            ByteBufCodecs.VAR_INT, Mark::z,
            ByteBufCodecs.INT, Mark::color,
            Mark::new);

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminMapPayload> STREAM_CODEC = StreamCodec.composite(
            TerritoryMapPayload.STREAM_CODEC, AdminMapPayload::territories,
            MARK_CODEC.apply(ByteBufCodecs.list()), AdminMapPayload::marks,
            AdminMapPayload::new);

    public AdminMapPayload {
        marks = List.copyOf(marks);
    }

    /**
     * A mark of the map.
     *
     * @param id    the id of the settlement, or the id of the group
     * @param name  the name of the settlement, or the journey of the group
     * @param kind  {@link #VILLAGE}, {@link #SIMULATED}, {@link #RUIN}, {@link #CARAVAN} or {@link #MERCHANT}
     * @param x     block X
     * @param z     block Z
     * @param color the color of the faction of a settlement, as 0xRRGGBB
     */
    public record Mark(String id, String name, int kind, int x, int z, int color) {
        /** True for a settlement, false for a group. */
        public boolean settlement() {
            return kind <= RUIN;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
