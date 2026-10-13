package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server to client: the quests of a village for a player. The client opens the quest board screen with it, or
 * updates the open screen.
 *
 * <p>The server makes the texts, so a new kind of quest needs few client changes. The client counts down the times
 * from the moment the payload comes.
 *
 * @param title        the village name
 * @param settlementId the id of the village
 * @param tier         the reputation tier of the player in the village (serialized name)
 * @param maxActive    the maximum number of active quests of a player
 * @param offerMinutes the time of an offer on the board, in minutes
 * @param offers       the offers of the village
 * @param active       the active quests of the player (all villages)
 * @param status       the result of the last action of the player, or empty
 * @param statusGood   true if the result is a success
 */
public record QuestBoardPayload(Component title, String settlementId, String tier, int maxActive, int offerMinutes,
        List<Offer> offers, List<Active> active, Optional<Component> status, boolean statusGood)
        implements CustomPacketPayload {

    /** No button. */
    public static final int ACTION_NONE = 0;
    /** The button accepts the offer. */
    public static final int ACTION_ACCEPT = 1;
    /** The button abandons the quest. */
    public static final int ACTION_ABANDON = 2;
    /** The button gives the items of a delivery. */
    public static final int ACTION_DELIVER = 3;

    public static final Type<QuestBoardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "quest_board"));

    /**
     * An offer of the village: one paper on the board.
     *
     * @param questId   the id of the quest
     * @param kind      the kind of the quest (serialized name), for the sort
     * @param itemId    the item of the icon
     * @param name      what to do
     * @param info      the kind, the distance, the time, and the minimum tier
     * @param reward    the reward: emeralds and reputation
     * @param progress  the items of the player for a delivery, or empty
     * @param countdown the seconds before the caravan of an escort leaves, or -1
     * @param emeralds  the reward in emeralds, for the sort
     * @param minutes   the time to complete the quest, for the sort
     * @param blocked   the reason why the player cannot accept the offer, or empty
     */
    public record Offer(String questId, String kind, String itemId, Component name, Component info,
            Component reward, Component progress, int countdown, int emeralds, int minutes,
            Optional<Component> blocked) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Offer> STREAM_CODEC = StreamCodec.of(
                (buf, offer) -> {
                    buf.writeUtf(offer.questId);
                    buf.writeUtf(offer.kind);
                    buf.writeUtf(offer.itemId);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, offer.name);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, offer.info);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, offer.reward);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, offer.progress);
                    buf.writeVarInt(offer.countdown);
                    buf.writeVarInt(offer.emeralds);
                    buf.writeVarInt(offer.minutes);
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, offer.blocked);
                },
                buf -> new Offer(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf)));
    }

    /**
     * An active quest of the player.
     *
     * @param questId     the id of the quest
     * @param itemId      the item of the icon
     * @param name        what to do, short
     * @param info        the village, the reward, the progress or the state of the caravan
     * @param secondsLeft the seconds before the deadline
     * @param action      {@link #ACTION_DELIVER} if the player can give the items here, else {@link #ACTION_NONE}
     * @param blocked     the reason why the player cannot deliver now (missing items, other village), or empty
     * @param ready       true if the quest is ready to complete here: the player has all the items
     */
    public record Active(String questId, String itemId, Component name, Component info, int secondsLeft,
            int action, Optional<Component> blocked, boolean ready) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Active> STREAM_CODEC = StreamCodec.of(
                (buf, active) -> {
                    buf.writeUtf(active.questId);
                    buf.writeUtf(active.itemId);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, active.name);
                    ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, active.info);
                    buf.writeVarInt(active.secondsLeft);
                    buf.writeVarInt(active.action);
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, active.blocked);
                    buf.writeBoolean(active.ready);
                },
                buf -> new Active(buf.readUtf(), buf.readUtf(),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                        buf.readVarInt(), buf.readVarInt(),
                        ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf), buf.readBoolean()));
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestBoardPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, payload.title);
                buf.writeUtf(payload.settlementId);
                buf.writeUtf(payload.tier);
                buf.writeVarInt(payload.maxActive);
                buf.writeVarInt(payload.offerMinutes);
                Offer.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.offers);
                Active.STREAM_CODEC.apply(ByteBufCodecs.list()).encode(buf, payload.active);
                ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.encode(buf, payload.status);
                buf.writeBoolean(payload.statusGood);
            },
            buf -> new QuestBoardPayload(ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readUtf(),
                    buf.readUtf(), buf.readVarInt(), buf.readVarInt(),
                    Offer.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    Active.STREAM_CODEC.apply(ByteBufCodecs.list()).decode(buf),
                    ComponentSerialization.TRUSTED_OPTIONAL_STREAM_CODEC.decode(buf), buf.readBoolean()));

    /** The same payload with a status line. */
    public QuestBoardPayload withStatus(Component text, boolean good) {
        return new QuestBoardPayload(title, settlementId, tier, maxActive, offerMinutes, offers, active,
                Optional.of(text), good);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
