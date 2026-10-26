package io.github.nicolasgoyon.folkhold.world.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomyState;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementRegistry;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlaces;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The settlements of a world, the treasuries of the factions and the placement regions that the discovery
 * checked. Saved in the Overworld data ({@code folkhold_settlements.dat}).
 *
 * <p>The territory claims come from the settlements (position, level, state). The data calculates them again
 * when one of these changes, at the next call that needs them. The other changes (a treasury, a stock) keep them.
 */
public final class SettlementData extends SavedData {
    private static final String FILE_ID = "folkhold_settlements";
    /**
     * The version of the saved format. {@code load} reads the tag of an earlier version (a tag with no version is
     * version 0) and the data saves with the current version. A tag of a later version loads with a warning.
     */
    static final int VERSION = 1;

    private final SettlementRegistry registry = new SettlementRegistry();
    private final Set<Long> checkedRegions = new HashSet<>();
    private long tickNumber;
    private long nextTickAt = -1;
    @Nullable
    private TerritoryClaims claims;
    /** The claimants of the cached claims. */
    private List<Claimant> claimants = List.of();
    private final Map<String, Long> treasuries = new HashMap<>();

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

    /**
     * The emeralds in the treasury of a faction. A faction with no treasury yet has the configured start value.
     *
     * @param factionId the id of the faction
     * @return the emeralds
     */
    public long treasury(String factionId) {
        return treasuries.getOrDefault(factionId, (long) WorldConfig.ECONOMY_START_TREASURY.get());
    }

    /**
     * Changes the treasury of a faction. The treasury does not go below 0.
     *
     * @param factionId the id of the faction
     * @param delta     the change in emeralds
     * @return the new value
     */
    public long addTreasury(String factionId, long delta) {
        long value = Math.max(0, treasury(factionId) + delta);
        treasuries.put(factionId, value);
        setDirty();
        return value;
    }

    /**
     * The territory chunks that the active settlements claim. The claim radius comes from the level, and it is never
     * smaller than {@link TerritoryClaims#minimumRadius} of the village radius ({@link WorldConfig#VILLAGE_RADIUS}).
     */
    public TerritoryClaims claims() {
        int minimumRadius = TerritoryClaims.minimumRadius(WorldConfig.VILLAGE_RADIUS.get());
        List<Claimant> current = new ArrayList<>();
        for (Settlement settlement : registry.all()) {
            if (settlement.isActive()) {
                int radius = Math.max(minimumRadius, LevelTables.get().traits(settlement.level()).claimRadius());
                current.add(new Claimant(settlement.id(), settlement.x(), settlement.z(), settlement.level(),
                        radius));
            }
        }
        if (claims == null || !current.equals(claimants)) {
            claims = TerritoryClaims.compute(current);
            claimants = current;
        }
        return claims;
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
        tag.putInt("Version", VERSION);
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
            entry.putInt("Level", settlement.level());
            entry.putInt("Experience", settlement.experience());
            entry.putString("Biome", settlement.biome());
            if (!settlement.gates().isEmpty()) {
                int[] gates = new int[settlement.gates().size() * 4];
                for (int g = 0; g < settlement.gates().size(); g++) {
                    Settlement.Gate gate = settlement.gates().get(g);
                    gates[4 * g] = gate.x();
                    gates[4 * g + 1] = gate.z();
                    gates[4 * g + 2] = gate.outX();
                    gates[4 * g + 3] = gate.outZ();
                }
                entry.putIntArray("Gates", gates);
            }
            entry.putInt("Beds", settlement.beds());
            entry.put("Economy", saveEconomy(settlement.economy()));
            list.add(entry);
        }
        tag.put("Settlements", list);
        CompoundTag treasuryTag = new CompoundTag();
        treasuries.forEach(treasuryTag::putLong);
        tag.put("Treasuries", treasuryTag);
        tag.putLong("TickNumber", tickNumber);
        tag.putLong("NextTickAt", nextTickAt);
        tag.putLongArray("CheckedRegions", checkedRegions.stream().mapToLong(Long::longValue).toArray());
        return tag;
    }

    private static SettlementData load(CompoundTag tag, HolderLookup.Provider registries) {
        SettlementData data = new SettlementData();
        int version = tag.getInt("Version");
        if (version > VERSION) {
            FolkholdWorld.LOGGER.warn("{} has the format version {}, this version of the mod reads version {}",
                    FILE_ID, version, VERSION);
        }
        ListTag list = tag.getList("Settlements", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Settlement settlement = new Settlement(entry.getString("Id"), entry.getInt("StartX"),
                    entry.getInt("StartZ"), entry.getString("Faction"), entry.getString("Name"),
                    entry.getInt("X"), entry.getInt("Z"));
            if (entry.contains("HeartY")) {
                settlement.setHeart(entry.getInt("X"), entry.getInt("HeartY"), entry.getInt("Z"));
            }
            settlement.setState(SettlementState.fromSerializedName(entry.getString("State")).orElseGet(() -> {
                FolkholdWorld.LOGGER.warn("Unknown state '{}' for the settlement {}: the settlement is active",
                        entry.getString("State"), settlement.id());
                return SettlementState.ACTIVE;
            }));
            if (entry.contains("Level")) {
                settlement.setProgress(Math.max(1, entry.getInt("Level")), Math.max(0, entry.getInt("Experience")));
            } else {
                settlement.setProgress(Settlements.startLevel(settlement.id()), 0);
            }
            settlement.setBiome(entry.getString("Biome"));
            int[] gates = entry.getIntArray("Gates");
            List<Settlement.Gate> loadedGates = new ArrayList<>();
            for (int g = 0; g + 3 < gates.length; g += 4) {
                loadedGates.add(new Settlement.Gate(gates[g], gates[g + 1], gates[g + 2], gates[g + 3]));
            }
            settlement.setGates(loadedGates);
            settlement.setBeds(entry.contains("Beds") ? entry.getInt("Beds") : VillagePlaces.UNKNOWN);
            loadEconomy(settlement.economy(), entry.getCompound("Economy"));
            if (data.registry.byId(settlement.id()).isEmpty()) {
                data.registry.add(settlement);
            }
        }
        CompoundTag treasuryTag = tag.getCompound("Treasuries");
        for (String key : treasuryTag.getAllKeys()) {
            data.treasuries.put(key, treasuryTag.getLong(key));
        }
        data.tickNumber = tag.getLong("TickNumber");
        data.nextTickAt = tag.contains("NextTickAt") ? tag.getLong("NextTickAt") : -1;
        for (long key : tag.getLongArray("CheckedRegions")) {
            data.checkedRegions.add(key);
        }
        return data;
    }

    private static CompoundTag saveEconomy(EconomyState economy) {
        CompoundTag tag = new CompoundTag();
        tag.putBoolean("Initialized", economy.isInitialized());
        tag.putInt("Population", economy.population());
        ListTag jobs = new ListTag();
        economy.jobs().forEach(job -> jobs.add(StringTag.valueOf(job)));
        tag.put("Jobs", jobs);
        CompoundTag stock = new CompoundTag();
        economy.stock().asMap().forEach(stock::putInt);
        tag.put("Stock", stock);
        tag.putDouble("FoodCovered", economy.foodCovered());
        tag.putDouble("JobsWorking", economy.jobsWorking());
        tag.putInt("Cycles", economy.cycles());
        return tag;
    }

    private static void loadEconomy(EconomyState economy, CompoundTag tag) {
        economy.setInitialized(tag.getBoolean("Initialized"));
        economy.setPopulation(tag.getInt("Population"));
        ListTag jobs = tag.getList("Jobs", Tag.TAG_STRING);
        List<String> jobIds = new ArrayList<>();
        for (int i = 0; i < jobs.size(); i++) {
            jobIds.add(jobs.getString(i));
        }
        economy.setJobs(jobIds);
        CompoundTag stock = tag.getCompound("Stock");
        for (String item : stock.getAllKeys()) {
            economy.stock().set(item, stock.getInt(item));
        }
        double food = tag.contains("FoodCovered") ? tag.getDouble("FoodCovered") : 1.0;
        double jobsWorking = tag.contains("JobsWorking") ? tag.getDouble("JobsWorking") : 1.0;
        economy.setLastCycle(food, jobsWorking);
        economy.setCycles(tag.getInt("Cycles"));
    }
}
