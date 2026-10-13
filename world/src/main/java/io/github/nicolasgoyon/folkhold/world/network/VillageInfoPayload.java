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
 * Server to client: an information screen with sections of label-value rows, for example the caravans and quests
 * overview of the admin screen. The village heart screen uses the sections too (see {@link VillageHeartPayload}).
 *
 * <p>The server makes all texts (translation keys and values), so new information needs no client change:
 * add a section or a row on the server.
 *
 * @param title    the title of the screen
 * @param sections the sections of the screen, top to bottom
 */
public record VillageInfoPayload(Component title, List<Section> sections) implements CustomPacketPayload {

    public static final Type<VillageInfoPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village_info"));

    /** No meter below the row. */
    public static final int METER_NONE = 0;
    /** A progress bar below the row. */
    public static final int METER_BAR = 1;
    /** A bar of segments below the row: one for each unit. */
    public static final int METER_SEGMENTS = 2;

    /**
     * One line of a section: a label and a value, and an optional meter below.
     *
     * @param label     the label
     * @param value     the value
     * @param meterKind {@link #METER_NONE}, {@link #METER_BAR} or {@link #METER_SEGMENTS}
     * @param meter     the value of the bar, or the number of filled segments
     * @param meterMax  the maximum of the bar, or the number of segments
     * @param meterLost the number of lost segments after the filled segments
     */
    public record Row(Component label, Component value, int meterKind, int meter, int meterMax, int meterLost) {

        /** A row with no meter. */
        public Row(Component label, Component value) {
            this(label, value, METER_NONE, 0, 0, 0);
        }

        /** A row with a progress bar. */
        public static Row bar(Component label, Component value, int current, int max) {
            return new Row(label, value, METER_BAR, current, max, 0);
        }

        /** A row with a bar of segments: filled, lost, and empty. */
        public static Row segments(Component label, Component value, int filled, int total, int lost) {
            return new Row(label, value, METER_SEGMENTS, filled, total, lost);
        }

        public static final StreamCodec<RegistryFriendlyByteBuf, Row> STREAM_CODEC = StreamCodec.of(
                (buf, row) -> {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, row.label);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, row.value);
                    buf.writeVarInt(row.meterKind);
                    buf.writeVarInt(row.meter);
                    buf.writeVarInt(row.meterMax);
                    buf.writeVarInt(row.meterLost);
                },
                buf -> new Row(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt()));
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
