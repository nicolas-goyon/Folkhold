package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementRegistry;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The settlements of a world and the placement regions that the discovery checked. Saved in the Overworld data
 * ({@code folkhold_settlements.dat}).
 */
public final class SettlementData extends SavedData {
    private static final String FILE_ID = "folkhold_settlements";

    private final SettlementRegistry registry = new SettlementRegistry();
    private final Set<Long> checkedRegions = new HashSet<>();
    private long tickNumber;
    private long nextTickAt = -1;

    private SettlementData() {
    }

    public static SettlementData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(SettlementData::new, SettlementData::load, null), FILE_ID);
    }

    /**
     * The settlements. Call {@link #setDirty()} after a change.
     *
     * @return the registry
     */
    public SettlementRegistry registry() {
        return registry;
    }

    /** True if the discovery checked this placement region. */
    public boolean isRegionChecked(int regionX, int regionZ) {
        return checkedRegions.contains(SettlementRegistry.chunkKey(regionX, regionZ));
    }

    /** Records that the discovery checked this placement region. */
    public void markRegionChecked(int regionX, int regionZ) {
        if (checkedRegions.add(SettlementRegistry.chunkKey(regionX, regionZ))) {
            setDirty();
        }
    }

    /** Number of the last abstract tick. */
    public long tickNumber() {
        return tickNumber;
    }

    /** Game time of the next abstract tick, or -1 if the simulation did not start. */
    public long nextTickAt() {
        return nextTickAt;
    }

    /**
     * Records the state of the scheduler.
     *
     * @param tickNumber the number of the last abstract tick
     * @param nextTickAt the game time of the next abstract tick
     */
    public void saveSchedule(long tickNumber, long nextTickAt) {
        if (this.tickNumber != tickNumber || this.nextTickAt != nextTickAt) {
            this.tickNumber = tickNumber;
            this.nextTickAt = nextTickAt;
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Settlement settlement : registry.all()) {
            CompoundTag entry = new CompoundTag();
            entry.putString("Id", settlement.id());
            entry.putInt("StartX", settlement.startChunkX());
            entry.putInt("StartZ", settlement.startChunkZ());
            entry.putString("Faction", settlement.factionId());
            entry.putString("Name", settlement.name());
            entry.putInt("X", settlement.x());
            entry.putInt("Z", settlement.z());
            if (settlement.hasHeart()) {
                entry.putInt("HeartY", settlement.heartY());
            }
            entry.putString("State", settlement.state().serializedName());
            list.add(entry);
        }
        tag.put("Settlements", list);
        tag.putLong("TickNumber", tickNumber);
        tag.putLong("NextTickAt", nextTickAt);
        tag.putLongArray("CheckedRegions", checkedRegions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementData data = new SettlementData();
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Settlement settlement = new Settlement(entry.getString("Id"), entry.getInt("StartX"),
                    entry.getInt("StartZ"), entry.getString("Faction"), entry.getString("Name"),
                    entry.getInt("X"), entry.getInt("Z"));
            if (entry.contains("HeartY")) {
                settlement.setHeart(entry.getInt("X"), entry.getInt("HeartY"), entry.getInt("Z"));
            }
            settlement.setState(SettlementState.fromSerializedName(entry.getString("State")));
            if (data.registry.byId(settlement.id()).isEmpty()) {
                data.registry.add(settlement);
            }
        }
        data.tickNumber = tag.getLong("TickNumber");
        data.nextTickAt = tag.contains("NextTickAt") ? tag.getLong("NextTickAt") : -1;
        for (long key : tag.getLongArray("CheckedRegions")) {
            data.checkedRegions.add(key);
        }
        return data;
    }
}
