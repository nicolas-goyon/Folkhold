package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Row;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the details of one settlement, for the admin screen.
 *
 * @param id     the settlement id
 * @param title  the title (name of the settlement)
 * @param active false for ruins
 * @param rows   the rows of the details
 */
public record AdminDetailsPayload(String id, Component title, boolean active, List<Row> rows)
        implements CustomPacketPayload {

    public static final Type<AdminDetailsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_details"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminDetailsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.STRING_UTF8, AdminDetailsPayload::id,
                    ComponentSerialization.TRUSTED_STREAM_CODEC, AdminDetailsPayload::title,
                    ByteBufCodecs.BOOL, AdminDetailsPayload::active,
                    Row.STREAM_CODEC.apply(ByteBufCodecs.list()), AdminDetailsPayload::rows,
                    AdminDetailsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
