package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroups;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The mobile groups of a world (caravans, traveling merchants) and the routes between the settlements. Saved in
 * the Overworld data ({@code folkhold_groups.dat}).
 */
public final class MobileGroupData extends SavedData {
    private static final String FILE_ID = "folkhold_groups";

    private final MobileGroups groups = new MobileGroups();
    private final Map<String, RouteEntry> routes = new HashMap<>();

    /**
     * A calculated route between two settlements, or the result "no route".
     *
     * @param fromId the id of the settlement at the start of the route
     * @param toId   the id of the settlement at the end of the route
     * @param fromX  block X of the start settlement at the time of the calculation
     * @param fromZ  block Z of the start settlement at the time of the calculation
     * @param toX    block X of the end settlement at the time of the calculation
     * @param toZ    block Z of the end settlement at the time of the calculation
     * @param route  the route, or an empty value if there is no route
     */
    public record RouteEntry(String fromId, String toId, int fromX, int fromZ, int toX, int toZ,
            Optional<Route> route) {
    }

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

    /**
     * The calculated route between two settlements, in the two directions.
     *
     * @param firstId  the id of one settlement
     * @param secondId the id of the other settlement
     * @return the entry, or null if the route was not calculated
     */
    public RouteEntry routeEntry(String firstId, String secondId) {
        return routes.get(routeKey(firstId, secondId));
    }

    /**
     * Records a calculated route.
     *
     * @param entry the entry
     */
    public void putRoute(RouteEntry entry) {
        routes.put(routeKey(entry.fromId(), entry.toId()), entry);
        setDirty();
    }

    /** The number of calculated routes, with the results "no route". */
    public int routeCount() {
        return routes.size();
    }

    private static String routeKey(String firstId, String secondId) {
        return firstId.compareTo(secondId) <= 0 ? firstId + "|" + secondId : secondId + "|" + firstId;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (MobileGroup group : groups.all()) {
            list.add(saveGroup(group));
        }
        tag.put("Groups", list);
        ListTag routeList = new ListTag();
        for (RouteEntry entry : routes.values()) {
            CompoundTag routeTag = new CompoundTag();
            routeTag.putString("From", entry.fromId());
            routeTag.putString("To", entry.toId());
            routeTag.putIntArray("Ends", new int[] {entry.fromX(), entry.fromZ(), entry.toX(), entry.toZ()});
            entry.route().ifPresent(route -> routeTag.putIntArray("Points", savePoints(route)));
            routeList.add(routeTag);
        }
        tag.put("Routes", routeList);
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
        ListTag routeList = tag.getList("Routes", Tag.TAG_COMPOUND);
        for (int i = 0; i < routeList.size(); i++) {
            CompoundTag routeTag = routeList.getCompound(i);
            int[] ends = routeTag.getIntArray("Ends");
            if (ends.length != 4) {
                continue;
            }
            Optional<Route> route = routeTag.contains("Points")
                    ? loadPoints(routeTag.getIntArray("Points")) : Optional.empty();
            RouteEntry entry = new RouteEntry(routeTag.getString("From"), routeTag.getString("To"), ends[0], ends[1],
                    ends[2], ends[3], route);
            data.routes.put(routeKey(entry.fromId(), entry.toId()), entry);
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
        tag.putIntArray("Route", savePoints(group.route()));
        tag.putDouble("Progress", group.progress());
        tag.putString("State", group.state().serializedName());
        tag.putLong("DepartureTick", group.departureTick());
        tag.putLong("Payment", group.payment());
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

    /** The points of a route as X, Z pairs. */
    private static int[] savePoints(Route route) {
        List<Route.Point> points = route.points();
        int[] coordinates = new int[points.size() * 2];
        for (int i = 0; i < points.size(); i++) {
            coordinates[i * 2] = points.get(i).x();
            coordinates[i * 2 + 1] = points.get(i).z();
        }
        return coordinates;
    }

    /** Reads a route from X, Z pairs. Fewer than 2 points give an empty value. */
    private static Optional<Route> loadPoints(int[] coordinates) {
        List<Route.Point> points = new ArrayList<>();
        for (int i = 0; i + 1 < coordinates.length; i += 2) {
            points.add(new Route.Point(coordinates[i], coordinates[i + 1]));
        }
        return points.size() < 2 ? Optional.empty() : Optional.of(new Route(points));
    }

    /** Reads one group. A group with an invalid route gives null. */
    private static MobileGroup loadGroup(CompoundTag tag) {
        Optional<Route> route = loadPoints(tag.getIntArray("Route"));
        if (route.isEmpty()) {
            return null;
        }
        MobileGroup group = new MobileGroup(tag.getString("Id"), GroupKind.fromSerializedName(tag.getString("Kind")),
                tag.getString("Faction"), tag.getString("Origin"), tag.getString("Destination"), route.get());
        group.setProgress(tag.getDouble("Progress"));
        group.setState(GroupState.fromSerializedName(tag.getString("State")));
        group.setDepartureTick(tag.getLong("DepartureTick"));
        group.setPayment(tag.getLong("Payment"));
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
