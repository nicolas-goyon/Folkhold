package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The construction sites of the builders in one level (see {@link BuilderSite}), from the oldest to the newest. Saved
 * in the data of the level ({@code folkhold_builder_sites.dat}).
 */
public final class BuilderSiteData extends SavedData {
    private static final String FILE_ID = "folkhold_builder_sites";
    private static final SavedData.Factory<BuilderSiteData> FACTORY =
            new SavedData.Factory<>(BuilderSiteData::new, BuilderSiteData::load, null);

    private final List<BuilderSite> sites = new ArrayList<>();
    private int nextId = 1;

    private BuilderSiteData() {
    }

    /** The sites of a level. The method makes the data when the level has no sites. */
    static BuilderSiteData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** The sites of a level, or null when the level never had sites. */
    @Nullable
    static BuilderSiteData find(ServerLevel level) {
        return level.getDataStorage().get(FACTORY, FILE_ID);
    }

    /** The sites, from the oldest to the newest. */
    List<BuilderSite> sites() {
        return Collections.unmodifiableList(sites);
    }

    /** Adds a site after the other sites. */
    BuilderSite add(ResourceLocation template, BlockPos origin, Rotation rotation, Mirror mirror, BlockPos heart,
            @Nullable UUID operator) {
        BuilderSite site = new BuilderSite(nextId++, template, origin, rotation, mirror, heart, operator);
        sites.add(site);
        setDirty();
        return site;
    }

    /** Adds a repair site after the other sites (see {@link BuilderSite#repair}). */
    BuilderSite addRepair(BlockPos heart, VillageReference reference, Set<BlockPos> positions) {
        BuilderSite site = BuilderSite.repair(nextId++, heart, reference, positions);
        sites.add(site);
        setDirty();
        return site;
    }

    void remove(BuilderSite site) {
        if (sites.remove(site)) {
            setDirty();
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (BuilderSite site : sites) {
            list.add(site.save());
        }
        tag.put("Sites", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    private static BuilderSiteData load(CompoundTag tag, HolderLookup.Provider registries) {
        BuilderSiteData data = new BuilderSiteData();
        ListTag list = tag.getList("Sites", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            BuilderSite.load(list.getCompound(i)).ifPresent(data.sites::add);
        }
        int maxId = data.sites.stream().mapToInt(BuilderSite::id).max().orElse(0);
        data.nextId = Math.max(tag.getInt("NextId"), maxId + 1);
        return data;
    }
}
