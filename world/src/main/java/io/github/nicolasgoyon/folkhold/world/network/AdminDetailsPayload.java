package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.network.VillageInfoPayload.Section;
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
 * <p>The screen shows the sections in two columns: the sections with an even index on the left, the sections with
 * an odd index on the right, and the stock below the right column.
 *
 * @param id         the settlement id
 * @param title      the name of the settlement
 * @param active     false for ruins
 * @param faction    the name of the faction, in its color
 * @param biome      the biome id, or an empty text
 * @param level      the level
 * @param tier       the name of the tier
 * @param alert      the ordinal of the alert state of the heart, or {@link #NO_ALERT} if the heart is not loaded
 * @param x          the block X of the heart
 * @param y          the block Y of the heart, or {@link #NO_Y} if it is not known
 * @param z          the block Z of the heart
 * @param sections   the sections of rows
 * @param stocks     the largest stocks of the settlement
 * @param moreStocks the number of other items in the stock
 */
public record AdminDetailsPayload(String id, Component title, boolean active, Component faction, String biome,
        int level, Component tier, int alert, int x, int y, int z, List<Section> sections, List<Stock> stocks,
        int moreStocks) implements CustomPacketPayload {

    /** The alert state is not known: the heart is not loaded. */
    public static final int NO_ALERT = -1;
    /** The Y of the heart is not known. */
    public static final int NO_Y = Integer.MIN_VALUE;

    public static final Type<AdminDetailsPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "admin_details"));

    /**
     * One item of the stock.
     *
     * @param itemId the item id
     * @param count  the quantity
     */
    public record Stock(String itemId, int count) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Stock> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Stock::itemId,
                ByteBufCodecs.VAR_INT, Stock::count,
                Stock::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdminDetailsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.id);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title);
                buf.writeBoolean(payload.active);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.faction);
                buf.writeUtf(payload.biome);
                buf.writeVarInt(payload.level);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.tier);
                buf.writeVarInt(payload.alert);
                buf.writeInt(payload.x);
                buf.writeInt(payload.y);
                buf.writeInt(payload.z);
                Section.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.sections);
                Stock.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.stocks);
                buf.writeVarInt(payload.moreStocks);
            },
            buf -> new AdminDetailsPayload(buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    buf.readBoolean(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(),
                    buf.readVarInt(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(),
                    buf.readInt(), buf.readInt(), buf.readInt(),
                    Section.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    Stock.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt()));

    /** True if the Y of the heart is known. */
    public boolean hasY() {
        return y != NO_Y;
    }

    /** True if the heart is loaded: the garrison actions are available. */
    public boolean heartLoaded() {
        return alert != NO_ALERT;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
