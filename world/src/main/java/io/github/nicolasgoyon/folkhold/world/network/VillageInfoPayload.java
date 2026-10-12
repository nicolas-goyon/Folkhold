package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the information of a village. The client opens the village heart screen with it.
 *
 * <p>The server makes all texts (translation keys and values), so new information needs no client change:
 * add a section or a row on the server.
 *
 * @param title    the title of the screen (the village name)
 * @param sections the sections of the screen, top to bottom
 */
public record VillageInfoPayload(Component title, List<Section> sections) implements CustomPacketPayload {

    public static final Type<VillageInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village_info"));

    /** One line of a section: a label and a value. */
    public record Row(Component label, Component value) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Row::label,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Row::value,
                Row::new);
    }

    /** A group of rows with a heading. */
    public record Section(Component heading, List<Row> rows) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Section> STREAM_CODEC = StreamCodec.composite(
                ComponentSerialization.TRUSTED_STREAM_CODEC, Section::heading,
                Row.STREAM_CODEC.apply(ByteBufCodecs.list()), Section::rows,
                Section::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageInfoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_STREAM_CODEC, VillageInfoPayload::title,
                    Section.STREAM_CODEC.apply(ByteBufCodecs.list()), VillageInfoPayload::sections,
                    VillageInfoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
