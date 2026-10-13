package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: all settlements of the world, for the admin screen. The client opens the admin screen, or
 * updates it if it is open.
 *
 * @param settlements the settlements, nearest to the player first
 */
public record AdminListPayload(List<Summary> settlements) implements CustomPacketPayload {

    public static final Type<AdminListPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_list"));

    /**
     * One settlement in the list.
     *
     * @param id         the settlement id
     * @param name       the name
     * @param faction    the name of the faction, in its color
     * @param level      the level
     * @param tier       the name of the tier
     * @param active     false for ruins
     * @param x          block X
     * @param z          block Z
     * @param population the population
     * @param distance   the horizontal distance to the player, in blocks
     * @param generated  true if the village is in the world: its heart exists. A settlement of the discovery exists
     *                   only in the simulation until the game generates its chunks.
     */
    public record Summary(String id, String name, Component faction, int level, Component tier, boolean active, int x,
            int z, int population, int distance, boolean generated) {

        static void write(RegistryFriendlyByteBuf buf, Summary summary) {
            buf.writeUtf(summary.id);
            buf.writeUtf(summary.name);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, summary.faction);
            buf.writeVarInt(summary.level);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, summary.tier);
            buf.writeBoolean(summary.active);
            buf.writeVarInt(summary.x);
            buf.writeVarInt(summary.z);
            buf.writeVarInt(summary.population);
            buf.writeVarInt(summary.distance);
            buf.writeBoolean(summary.generated);
        }

        static Summary read(RegistryFriendlyByteBuf buf) {
            return new Summary(buf.readUtf(), buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean());
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminListPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.settlements.size());
                payload.settlements.forEach(summary -> Summary.write(buf, summary));
            },
            buf -> {
                int size = buf.readVarInt();
                List<Summary> list = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    list.add(Summary.read(buf));
                }
                return new AdminListPayload(list);
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
