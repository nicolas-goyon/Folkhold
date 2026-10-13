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
 * Server to client: the village heart screen for one player. The player part comes first: the welcome, the
 * reputation, what the player can do here, and an advice. The village part is in sections (village, garrison,
 * economy, territory).
 *
 * @param settlementId  the id of the settlement, or an empty text for a heart with no settlement
 * @param title         the village name
 * @param faction       the faction name in its color, or an empty text
 * @param culture       the name of the culture, or an empty text
 * @param bannerColor   the faction color (RGB), or -1
 * @param level         the level of the village, or 0 with no settlement
 * @param tier          the name of the tier of the village
 * @param alert         the alert state (serialized name: calm, alert, assault)
 * @param playerTier    the reputation tier of the player in the village (serialized name)
 * @param reputation    the effective reputation of the player in the village
 * @param villageValue  the reputation of the player with the village
 * @param factionValue  the reputation of the player with the faction
 * @param villageWeight the weight of the village value in the effective value, in percent
 * @param welcome       the welcome line, for the tier of the player
 * @param services      what the player can do here
 * @param advice        an advice: what not to do, or how to improve the reputation
 * @param adviceDanger  true if the advice is a warning (what not to do)
 * @param sections      the village sections
 */
public record VillageHeartPayload(String settlementId, Component title, Component faction, Component culture,
        int bannerColor, int level, Component tier, String alert, String playerTier, int reputation,
        int villageValue, int factionValue, int villageWeight, Component welcome, List<Service> services,
        Component advice, boolean adviceDanger, List<Section> sections) implements CustomPacketPayload {

    public static final Type<VillageHeartPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village_heart"));

    /** The service is available. */
    public static final int AVAILABLE = 0;
    /** The service is available with a limit. */
    public static final int LIMITED = 1;
    /** The service is refused to the player. */
    public static final int REFUSED = 2;
    /** The service does not exist in this village now. */
    public static final int LATER = 3;

    /** No button. */
    public static final int ACTION_NONE = 0;
    /** The button opens the quest board screen of the village. */
    public static final int ACTION_QUEST_BOARD = 1;

    /**
     * One thing that the player can do in the village, or not.
     *
     * @param name    the name: quests, trade, shelter, inn
     * @param state   the short state, with a glyph (✓, ✕, –)
     * @param status  {@link #AVAILABLE}, {@link #LIMITED}, {@link #REFUSED} or {@link #LATER}
     * @param details the details, or an empty text
     * @param items   the items of the details, for example the items that the village looks for
     * @param action  {@link #ACTION_NONE} or {@link #ACTION_QUEST_BOARD}
     */
    public record Service(Component name, Component state, int status, Component details, List<String> items,
            int action) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Service> STREAM_CODEC = StreamCodec.of(
                (buf, service) -> {
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, service.name);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, service.state);
                    buf.writeVarInt(service.status);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, service.details);
                    ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).encode(buf, service.items);
                    buf.writeVarInt(service.action);
                },
                buf -> new Service(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).decode(buf), buf.readVarInt()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, VillageHeartPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeUtf(payload.settlementId);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.faction);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.culture);
                buf.writeInt(payload.bannerColor);
                buf.writeVarInt(payload.level);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.tier);
                buf.writeUtf(payload.alert);
                buf.writeUtf(payload.playerTier);
                buf.writeInt(payload.reputation);
                buf.writeInt(payload.villageValue);
                buf.writeInt(payload.factionValue);
                buf.writeVarInt(payload.villageWeight);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.welcome);
                Service.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.services);
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.advice);
                buf.writeBoolean(payload.adviceDanger);
                Section.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.sections);
            },
            buf -> new VillageHeartPayload(buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readInt(), buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(), buf.readUtf(),
                    buf.readInt(), buf.readInt(), buf.readInt(), buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    Service.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(),
                    Section.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
