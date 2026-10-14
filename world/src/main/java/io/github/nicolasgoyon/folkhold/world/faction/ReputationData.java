package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.PlayerReputation;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Reputation of each player, by player UUID. Saved in the Overworld data ({@code folkhold_reputation.dat}).
 */
public final class ReputationData extends SavedData {
    private static final String FILE_ID = "folkhold_reputation";
    /** Grows at each change of the data. The caches of the units compare it to the version of their value. */
    private static long version;

    private final Map<UUID, PlayerReputation> players = new HashMap<>();

    private ReputationData() {
    }

    public static ReputationData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ReputationData::new, ReputationData::load, null), FILE_ID);
    }

    /**
     * The version of the data: a number that grows at each change (see {@link #setDirty()}). A cached value with
     * another version is stale.
     */
    public static long version() {
        return version;
    }

    /** Marks the data for a save and makes the cached values stale (see {@link #version()}). */
    @Override
    public void setDirty() {
        super.setDirty();
        version++;
    }

    /**
     * The reputation of a player. A new player gets an empty reputation (start values).
     *
     * <p>Call {@link #setDirty()} after a change.
     */
    public PlayerReputation get(UUID player) {
        return players.computeIfAbsent(player, id -> new PlayerReputation());
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, PlayerReputation> entry : players.entrySet()) {
            CompoundTag playerTag = new CompoundTag();
            playerTag.putUUID("Player", entry.getKey());
            CompoundTag factions = new CompoundTag();
            entry.getValue().factionValues().forEach(factions::putInt);
            playerTag.put("Factions", factions);
            CompoundTag villages = new CompoundTag();
            entry.getValue().villageValues().forEach(villages::putInt);
            playerTag.put("Villages", villages);
            list.add(playerTag);
        }
        tag.put("Players", list);
        return tag;
    }

    private static ReputationData load(CompoundTag tag, HolderLookup.Provider registries) {
        ReputationData data = new ReputationData();
        ListTag list = tag.getList("Players", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag playerTag = list.getCompound(i);
            if (!playerTag.hasUUID("Player")) {
                continue;
            }
            PlayerReputation reputation = new PlayerReputation();
            CompoundTag factions = playerTag.getCompound("Factions");
            for (String key : factions.getAllKeys()) {
                reputation.setFaction(key, factions.getInt(key));
            }
            CompoundTag villages = playerTag.getCompound("Villages");
            for (String key : villages.getAllKeys()) {
                reputation.setVillage(key, villages.getInt(key));
            }
            data.players.put(playerTag.getUUID("Player"), reputation);
        }
        return data;
    }
}
