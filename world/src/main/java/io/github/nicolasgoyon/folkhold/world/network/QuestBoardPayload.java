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
 * Server to client: the quests of a village for a player. The client opens the quest board screen with it, or
 * updates the open screen.
 *
 * <p>The server makes all texts, so a new kind of quest needs no client change.
 *
 * @param title        the title of the screen (the village name)
 * @param settlementId the id of the village
 * @param offers       the offers of the village
 * @param active       the active quests of the player
 */
public record QuestBoardPayload(Component title, String settlementId, List<Entry> offers, List<Entry> active)
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
     * One quest.
     *
     * @param questId the id of the quest
     * @param name    the first line: what to do
     * @param details the second line: reward and time
     * @param action  the action of the main button ({@link #ACTION_NONE} for no button)
     * @param abandon true if the quest also has an abandon button
     */
    public record Entry(String questId, Component name, Component details, int action, boolean abandon) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Entry::questId,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::name,
                ComponentSerialization.TRUSTED_STREAM_CODEC, Entry::details,
                ByteBufCodecs.VAR_INT, Entry::action,
                ByteBufCodecs.BOOL, Entry::abandon,
                Entry::new);
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, QuestBoardPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ComponentSerialization.TRUSTED_STREAM_CODEC, QuestBoardPayload::title,
                    ByteBufCodecs.STRING_UTF8, QuestBoardPayload::settlementId,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), QuestBoardPayload::offers,
                    Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), QuestBoardPayload::active,
                    QuestBoardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
