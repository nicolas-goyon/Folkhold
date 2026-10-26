package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
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
 * Server to client: the journal of the player, with the villages and the factions that the player discovered (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.Journals}). The client opens the journal, or updates it if it
 * is open.
 *
 * @param villages the discovered villages, nearest to the player first
 * @param factions the factions of the discovered villages
 * @param today    the day of the world at the time of the payload
 */
public record JournalPayload(List<Village> villages, List<Faction> factions, long today)
        implements CustomPacketPayload {

    public static final Type<JournalPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "journal"));

    /**
     * A village of the journal.
     *
     * @param id         the settlement id
     * @param name       the name
     * @param factionId  the id of the faction
     * @param faction    the name of the faction, in its color
     * @param level      the level
     * @param tier       the name of the tier of the level
     * @param active     false for ruins
     * @param x          block X
     * @param z          block Z
     * @param distance   the horizontal distance to the player, in blocks
     * @param reputation the tier of the player in the village
     * @param discovered the day of the discovery
     * @param lastVisit  the day of the last visit, or -1 if the player did not enter the village
     * @param inhabitants the number of inhabitants that the village keeps
     * @param places     the places of the inhabitants, or -1 before the first count of the beds of the village
     */
    public record Village(String id, String name, String factionId, Component faction, int level, Component tier,
            boolean active, int x, int z, int distance, ReputationTier reputation, long discovered, long lastVisit,
            int inhabitants, int places) {

        /** True if the player entered the village. */
        public boolean visited() {
            return lastVisit >= 0;
        }

        static void write(RegistryFriendlyByteBuf buf, Village village) {
            buf.writeUtf(village.id);
            buf.writeUtf(village.name);
            buf.writeUtf(village.factionId);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, village.faction);
            buf.writeVarInt(village.level);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, village.tier);
            buf.writeBoolean(village.active);
            buf.writeVarInt(village.x);
            buf.writeVarInt(village.z);
            buf.writeVarInt(village.distance);
            buf.writeEnum(village.reputation);
            buf.writeVarLong(village.discovered);
            buf.writeLong(village.lastVisit);
            buf.writeVarInt(village.inhabitants);
            buf.writeInt(village.places);
        }

        static Village read(RegistryFriendlyByteBuf buf) {
            return new Village(buf.readUtf(), buf.readUtf(), buf.readUtf(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readVarInt(),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readBoolean(), buf.readVarInt(),
                    buf.readVarInt(), buf.readVarInt(), buf.readEnum(ReputationTier.class), buf.readVarLong(),
                    buf.readLong(), buf.readVarInt(), buf.readInt());
        }
    }

    /**
     * A faction of the journal.
     *
     * @param id         the faction id
     * @param name       the name, in the color of the faction
     * @param culture    the name of the culture
     * @param attitude   the base attitude of the faction to the players
     * @param reputation the tier of the player with the faction
     * @param value      the reputation of the player with the faction
     * @param villages   the number of discovered villages of the faction
     */
    public record Faction(String id, Component name, Component culture, Component attitude,
            ReputationTier reputation, int value, int villages) {

        static void write(RegistryFriendlyByteBuf buf, Faction faction) {
            buf.writeUtf(faction.id);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, faction.name);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, faction.culture);
            ComponentSerialization.TRUSTED_STREAM_CODEC.encode(buf, faction.attitude);
            buf.writeEnum(faction.reputation);
            buf.writeVarInt(faction.value);
            buf.writeVarInt(faction.villages);
        }

        static Faction read(RegistryFriendlyByteBuf buf) {
            return new Faction(buf.readUtf(), ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf),
                    ComponentSerialization.TRUSTED_STREAM_CODEC.decode(buf), buf.readEnum(ReputationTier.class),
                    buf.readVarInt(), buf.readVarInt());
        }
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, JournalPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.villages.size());
                payload.villages.forEach(village -> Village.write(buf, village));
                buf.writeVarInt(payload.factions.size());
                payload.factions.forEach(faction -> Faction.write(buf, faction));
                buf.writeVarLong(payload.today);
            },
            buf -> {
                int villageCount = buf.readVarInt();
                List<Village> villages = new ArrayList<>(villageCount);
                for (int i = 0; i < villageCount; i++) {
                    villages.add(Village.read(buf));
                }
                int factionCount = buf.readVarInt();
                List<Faction> factions = new ArrayList<>(factionCount);
                for (int i = 0; i < factionCount; i++) {
                    factions.add(Faction.read(buf));
                }
                return new JournalPayload(villages, factions, buf.readVarLong());
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
