package io.github.nicolasgoyon.folkhold.world.village;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The detachments of the vanilla villages, by position of the bell (see {@link VillageDetachments}). Saved in the
 * Overworld data ({@code folkhold_detachments.dat}).
 */
public final class DetachmentData extends SavedData {
    private static final String FILE_ID = "folkhold_detachments";

    private final Map<BlockPos, Detachment> detachments = new HashMap<>();

    /** The guards of a faction in one vanilla village. */
    public static final class Detachment {
        private final BlockPos bell;
        private String factionId;
        private final Garrison garrison = new Garrison();

        Detachment(BlockPos bell, String factionId) {
            this.bell = bell.immutable();
            this.factionId = factionId;
        }

        /** The position of the bell of the village: the center of the patrol of the guards. */
        public BlockPos bell() {
            return bell;
        }

        /** The id of the faction of the guards. */
        public String factionId() {
            return factionId;
        }

        void setFactionId(String factionId) {
            this.factionId = factionId;
        }

        /** The guards, with the same rules as the garrison of a faction village. */
        public Garrison garrison() {
            return garrison;
        }

        /** True if a unit is a guard of this detachment, alive or not. */
        public boolean contains(UUID unitId) {
            return garrison.entries().stream().anyMatch(entry -> entry.unitId().equals(unitId));
        }
    }

    private DetachmentData() {
    }

    public static DetachmentData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(DetachmentData::new, DetachmentData::load, null), FILE_ID);
    }

    /** The detachment of the village of a bell, if it has one. */
    public Optional<Detachment> get(BlockPos bell) {
        return Optional.ofNullable(detachments.get(bell));
    }

    /** All the detachments. */
    public Collection<Detachment> all() {
        return detachments.values();
    }

    /** Adds a detachment with no guards. Call {@link #setDirty()} after the change. */
    Detachment create(BlockPos bell, String factionId) {
        Detachment detachment = new Detachment(bell, factionId);
        detachments.put(detachment.bell(), detachment);
        return detachment;
    }

    /** Removes the detachment of a bell. Call {@link #setDirty()} after the change. */
    void remove(BlockPos bell) {
        detachments.remove(bell);
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Detachment detachment : detachments.values()) {
            CompoundTag entry = new CompoundTag();
            entry.put("Bell", NbtUtils.writeBlockPos(detachment.bell()));
            entry.putString("Faction", detachment.factionId());
            entry.put("Guards", detachment.garrison().save());
            list.add(entry);
        }
        tag.put("Detachments", list);
        return tag;
    }

    private static DetachmentData load(CompoundTag tag, HolderLookup.Provider registries) {
        DetachmentData data = new DetachmentData();
        ListTag list = tag.getList("Detachments", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            Optional<BlockPos> bell = NbtUtils.readBlockPos(entry, "Bell");
            if (bell.isEmpty() || !entry.contains("Faction", Tag.TAG_STRING)) {
                continue;
            }
            Detachment detachment = data.create(bell.get(), entry.getString("Faction"));
            detachment.garrison().load(entry.getList("Guards", Tag.TAG_COMPOUND));
        }
        return data;
    }
}
