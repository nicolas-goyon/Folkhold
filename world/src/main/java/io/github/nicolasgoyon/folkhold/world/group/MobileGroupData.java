package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroups;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The mobile groups of a world (caravans, traveling merchants). Saved in the Overworld data
 * ({@code folkhold_groups.dat}).
 */
public final class MobileGroupData extends SavedData {
    private static final String FILE_ID = "folkhold_groups";

    private final MobileGroups groups = new MobileGroups();

    private MobileGroupData() {
    }

    public static MobileGroupData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(MobileGroupData::new, MobileGroupData::load, null), FILE_ID);
    }

    /**
     * The groups. Call {@link #setDirty()} after a change.
     *
     * @return the registry
     */
    public MobileGroups groups() {
        return groups;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MobileGroup group : groups.all()) {
            list.add(saveGroup(group));
        }
        tag.put("Groups", list);
        return tag;
    }

    /**
     * Reads the data from a tag. {@link #save(CompoundTag, HolderLookup.Provider)} writes this tag.
     *
     * @param tag        the tag
     * @param registries the registries
     * @return the data
     */
    public static MobileGroupData load(CompoundTag tag, HolderLookup.Provider registries) {
        MobileGroupData data = new MobileGroupData();
        ListTag list = tag.getList("Groups", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            MobileGroup group = loadGroup(list.getCompound(i));
            if (group != null && data.groups.byId(group.id()).isEmpty()) {
                data.groups.add(group);
            }
        }
        return data;
    }

    private static CompoundTag saveGroup(MobileGroup group) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Id", group.id());
        tag.putString("Kind", group.kind().serializedName());
        tag.putString("Faction", group.factionId());
        tag.putString("Origin", group.originId());
        tag.putString("Destination", group.destinationId());
        List<Route.Point> points = group.route().points();
        int[] coordinates = new int[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            coordinates[i * 2] = points.get(i).x();
            coordinates[i * 2 + 1] = points.get(i).z();
        }
        tag.putIntArray("Route", coordinates);
        tag.putDouble("Progress", group.progress());
        tag.putString("State", group.state().serializedName());
        tag.putLong("DepartureTick", group.departureTick());
        CompoundTag cargo = new CompoundTag();
        group.cargo().asMap().forEach(cargo::putInt);
        tag.put("Cargo", cargo);
        ListTag members = new ListTag();
        for (GroupMember member : group.members()) {
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", member.id());
            entry.putString("Role", member.role().serializedName());
            entry.putFloat("Health", member.health());
            members.add(entry);
        }
        tag.put("Members", members);
        return tag;
    }

    /** Reads one group. A group with an invalid route gives null. */
    private static MobileGroup loadGroup(CompoundTag tag) {
        int[] coordinates = tag.getIntArray("Route");
        List<Route.Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            points.add(new Route.Point(coordinates[i], coordinates[i + 1]));
        }
        if (points.size() < 2) {
            return null;
        }
        MobileGroup group = new MobileGroup(tag.getString("Id"), GroupKind.fromSerializedName(tag.getString("Kind")),
                tag.getString("Faction"), tag.getString("Origin"), tag.getString("Destination"), new Route(points));
        group.setProgress(tag.getDouble("Progress"));
        group.setState(GroupState.fromSerializedName(tag.getString("State")));
        group.setDepartureTick(tag.getLong("DepartureTick"));
        CompoundTag cargo = tag.getCompound("Cargo");
        for (String item : cargo.getAllKeys()) {
            group.cargo().set(item, cargo.getInt(item));
        }
        ListTag members = tag.getList("Members", Tag.TAG_COMPOUND);
        for (int i = 0; i < members.size(); i++) {
            CompoundTag entry = members.getCompound(i);
            if (entry.hasUUID("Id") && group.member(entry.getUUID("Id")).isEmpty()) {
                group.addMember(new GroupMember(entry.getUUID("Id"),
                        MemberRole.fromSerializedName(entry.getString("Role")), entry.getFloat("Health")));
            }
        }
        return group;
    }
}
