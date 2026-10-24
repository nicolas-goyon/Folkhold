package io.github.nicolasgoyon.folkhold.world.journal;

import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The villages that each player discovered and visited (see {@link Journals}). Saved in the Overworld.
 */
public final class JournalData extends SavedData {
    private static final String FILE_ID = "folkhold_world_journal";
    private static final SavedData.Factory<JournalData> FACTORY =
            new SavedData.Factory<>(JournalData::new, JournalData::load, null);

    /** For each player, the villages of the journal by settlement id, in the order of the discovery. */
    private final Map<UUID, Map<String, Visit>> players = new HashMap<>();

    /**
     * A village in the journal of a player.
     *
     * @param discovered the day of the discovery (see {@link Journals#day})
     * @param lastVisit  the day of the last visit, or -1 if the player did not enter the village
     */
    public record Visit(long discovered, long lastVisit) {
        /** True if the player entered the village. */
        public boolean visited() {
            return lastVisit >= 0;
        }
    }

    private JournalData() {
    }

    /** The journal data of the server. Makes it when the world has none. */
    public static JournalData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /**
     * The villages of a player.
     *
     * @param player the UUID of the player
     * @return the visits by settlement id, in the order of the discovery (an empty map for a new player)
     */
    public Map<String, Visit> visits(UUID player) {
        return Collections.unmodifiableMap(players.getOrDefault(player, Map.of()));
    }

    /**
     * Records the discovery of a village. A village that the player discovered stays as it is.
     *
     * @return true if the village is new in the journal
     */
    public boolean discover(UUID player, String settlementId, long day) {
        Map<String, Visit> visits = players.computeIfAbsent(player, key -> new LinkedHashMap<>());
        if (visits.containsKey(settlementId)) {
            return false;
        }
        visits.put(settlementId, new Visit(day, -1));
        setDirty();
        return true;
    }

    /**
     * Records a visit of a village. A visit also discovers the village.
     *
     * @return true if the journal changed
     */
    public boolean visit(UUID player, String settlementId, long day) {
        Map<String, Visit> visits = players.computeIfAbsent(player, key -> new LinkedHashMap<>());
        Visit old = visits.get(settlementId);
        if (old != null && old.lastVisit() == day) {
            return false;
        }
        visits.put(settlementId, new Visit(old == null ? day : old.discovered(), day));
        setDirty();
        return true;
    }

    /**
     * Replaces the id of a settlement in all the journals.
     *
     * @param oldId the id that the settlement had
     * @param newId the new id
     */
    public void renameSettlement(String oldId, String newId) {
        for (Map<String, Visit> visits : players.values()) {
            Visit visit = visits.remove(oldId);
            if (visit != null) {
                visits.merge(newId, visit, (a, b) -> new Visit(Math.min(a.discovered(), b.discovered()),
                        Math.max(a.lastVisit(), b.lastVisit())));
                setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, Map<String, Visit>> player : players.entrySet()) {
            ListTag villages = new ListTag();
            for (Map.Entry<String, Visit> entry : player.getValue().entrySet()) {
                CompoundTag village = new CompoundTag();
                village.putString("Id", entry.getKey());
                village.putLong("Discovered", entry.getValue().discovered());
                village.putLong("LastVisit", entry.getValue().lastVisit());
                villages.add(village);
            }
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Player", player.getKey());
            entry.put("Villages", villages);
            list.add(entry);
        }
        tag.put("Players", list);
        return tag;
    }

    private static JournalData load(CompoundTag tag, HolderLookup.Provider registries) {
        JournalData data = new JournalData();
        for (Tag element : tag.getList("Players", Tag.TAG_COMPOUND)) {
            CompoundTag entry = (CompoundTag) element;
            Map<String, Visit> visits = new LinkedHashMap<>();
            for (Tag village : entry.getList("Villages", Tag.TAG_COMPOUND)) {
                CompoundTag compound = (CompoundTag) village;
                visits.put(compound.getString("Id"), new Visit(compound.getLong("Discovered"),
                        compound.getLong("LastVisit")));
            }
            data.players.put(entry.getUUID("Player"), visits);
        }
        return data;
    }
}
