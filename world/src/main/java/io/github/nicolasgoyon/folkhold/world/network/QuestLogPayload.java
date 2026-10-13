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
 * Server to client: the active quests of the player. The quest HUD shows them.
 *
 * <p>The client counts down the times from the moment the payload comes. For a delivery, the client counts the
 * items in the inventory of the player. During an escort, the server sends the payload each second.
 *
 * @param maxActive the maximum number of active quests of a player
 * @param quests    the active quests
 */
public record QuestLogPayload(int maxActive, List<Entry> quests) implements CustomPacketPayload {

    /** No active quest. */
    public static final QuestLogPayload EMPTY = new QuestLogPayload(0, List.of());

    public static final Type<QuestLogPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "quest_log"));

    /**
     * An active quest.
     *
     * @param questId     the id of the quest
     * @param itemId      the item of the icon, and the item to deliver for a delivery
     * @param name        what to do, short
     * @param count       the number of items to deliver, or 0 for an escort
     * @param secondsLeft the seconds before the deadline
     * @param escort      the state of the escorted caravan, or empty for a delivery
     */
    public record Entry(String questId, String itemId, Component name, int count, int secondsLeft,
            Optional<Escort> escort) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::questId,
                ByteBufCodecs.STRING_UTF8, Entry::itemId,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::name,
                ByteBufCodecs.VAR_INT, Entry::count,
                ByteBufCodecs.VAR_INT, Entry::secondsLeft,
                ByteBufCodecs.optional(Escort.STREAM_CODEC), Entry::escort,
                Entry::new);
    }

    /**
     * The state of an escorted caravan.
     *
     * @param progress     the blocks that the caravan traveled
     * @param length       the length of the route, in blocks
     * @param health       the health of the merchant, or -1 if its entity is not loaded
     * @param maxHealth    the maximum health of the merchant, or -1
     * @param distance     the distance from the player to the caravan, in blocks
     * @param maxDistance  the escort distance: farther, the caravan stops
     * @param awaySeconds  the seconds before the quest fails because the player is too far, or -1 if the player is
     *                     near
     */
    public record Escort(int progress, int length, int health, int maxHealth, int distance, int maxDistance,
            int awaySeconds) {

        public static final StreamCodec<RegistryFriendlyByteBuf, Escort> STREAM_CODEC = StreamCodec.of(
                (buf, escort) -> {
                    buf.writeVarInt(escort.progress);
                    buf.writeVarInt(escort.length);
                    buf.writeVarInt(escort.health);
                    buf.writeVarInt(escort.maxHealth);
                    buf.writeVarInt(escort.distance);
                    buf.writeVarInt(escort.maxDistance);
                    buf.writeVarInt(escort.awaySeconds);
                },
                buf -> new Escort(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                        buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

        /** True if the player is farther than the escort distance. */
        public boolean away() {
            return awaySeconds >= 0;
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestLogPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, QuestLogPayload::maxActive,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), QuestLogPayload::quests,
                    QuestLogPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
