package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Relief;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * The last route and the relief that the journal showed for each pair of villages (see {@link RouteViews}). The
 * journal shows them again with no new search, until a player asks for a new calculation. Saved in the Overworld.
 */
public final class RouteViewData extends SavedData {
    private static final String FILE_ID = "folkhold_world_route_views";
    private static final SavedData.Factory<RouteViewData> FACTORY =
            new SavedData.Factory<>(RouteViewData::new, RouteViewData::load, null);

    /** The routes by pair key ({@link Route#pairKey}). */
    private final Map<String, Snapshot> routes = new HashMap<>();
    /** The reliefs by pair key. */
    private final Map<String, Relief> reliefs = new HashMap<>();

    /**
     * A route that the journal showed.
     *
     * @param fromId   the settlement id of the start of the route
     * @param toId     the settlement id of the end of the route
     * @param detailed true for a detailed route, false for a coarse route
     * @param route    the route, from the start to the end
     */
    public record Snapshot(String fromId, String toId, boolean detailed, Route route) {
        /**
         * The route from one settlement to the other.
         *
         * @param startId the settlement id of the start
         * @return the route in this direction
         */
        public Route from(String startId) {
            return startId.equals(fromId) ? route : route.reversed();
        }
    }

    private RouteViewData() {
    }

    /** The data of the server. Makes it when the world has none. */
    public static RouteViewData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, FILE_ID);
    }

    /** The last route of a pair of villages. */
    public Optional<Snapshot> route(String firstId, String secondId) {
        return Optional.ofNullable(routes.get(Route.pairKey(firstId, secondId)));
    }

    /** Keeps the route of a pair of villages. */
    public void putRoute(Snapshot snapshot) {
        Snapshot old = routes.put(Route.pairKey(snapshot.fromId(), snapshot.toId()), snapshot);
        if (!snapshot.equals(old)) {
            setDirty();
        }
    }

    /** The relief of a pair of villages, or null. */
    @Nullable
    public Relief relief(String key) {
        return reliefs.get(key);
    }

    /** Keeps the relief of a pair of villages. */
    public void putRelief(String key, Relief relief) {
        reliefs.put(key, relief);
        setDirty();
    }

    /** Forgets the route and the relief of a pair of villages. */
    public void forget(String firstId, String secondId) {
        String key = Route.pairKey(firstId, secondId);
        boolean route = routes.remove(key) != null;
        boolean relief = reliefs.remove(key) != null;
        if (route || relief) {
            setDirty();
        }
    }

    /**
     * Replaces the id of a settlement in the routes and the reliefs.
     *
     * @param oldId the id that the settlement had
     * @param newId the new id
     */
    public void renameSettlement(String oldId, String newId) {
        for (String key : List.copyOf(routes.keySet())) {
            Snapshot snapshot = routes.get(key);
            if (snapshot.fromId().equals(oldId) || snapshot.toId().equals(oldId)) {
                routes.remove(key);
                reliefs.remove(key);
                String from = snapshot.fromId().equals(oldId) ? newId : snapshot.fromId();
                String to = snapshot.toId().equals(oldId) ? newId : snapshot.toId();
                routes.put(Route.pairKey(from, to), new Snapshot(from, to, snapshot.detailed(), snapshot.route()));
                setDirty();
            }
        }
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag list = new ListTag();
        for (Map.Entry<String, Snapshot> entry : routes.entrySet()) {
            Snapshot snapshot = entry.getValue();
            CompoundTag route = new CompoundTag();
            route.putString("From", snapshot.fromId());
            route.putString("To", snapshot.toId());
            route.putBoolean("Detailed", snapshot.detailed());
            List<Route.Point> points = snapshot.route().points();
            int[] coordinates = new int[points.size() * 2];
            for (int i = 0; i < points.size(); i++) {
                coordinates[2 * i] = points.get(i).x();
                coordinates[2 * i + 1] = points.get(i).z();
            }
            route.putIntArray("Points", coordinates);
            byte[] surfaces = new byte[snapshot.route().surfaces().size()];
            for (int i = 0; i < surfaces.length; i++) {
                surfaces[i] = (byte) snapshot.route().surfaces().get(i).code();
            }
            route.putByteArray("Surfaces", surfaces);
            Relief relief = reliefs.get(entry.getKey());
            if (relief != null) {
                route.put("Relief", saveRelief(relief));
            }
            list.add(route);
        }
        tag.put("Routes", list);
        return tag;
    }

    private static CompoundTag saveRelief(Relief relief) {
        CompoundTag tag = new CompoundTag();
        tag.putInt("MinX", relief.minX());
        tag.putInt("MinZ", relief.minZ());
        tag.putInt("Cell", relief.cell());
        tag.putInt("Width", relief.width());
        tag.putInt("Height", relief.height());
        int[] floor = new int[relief.width() * relief.height()];
        byte[] water = new byte[floor.length];
        for (int j = 0; j < relief.height(); j++) {
            for (int i = 0; i < relief.width(); i++) {
                floor[j * relief.width() + i] = relief.floor(i, j);
                water[j * relief.width() + i] = (byte) relief.water(i, j);
            }
        }
        tag.putIntArray("Floor", floor);
        tag.putByteArray("Water", water);
        return tag;
    }

    @Nullable
    private static Relief loadRelief(CompoundTag tag) {
        int width = tag.getInt("Width");
        int height = tag.getInt("Height");
        int[] floor = tag.getIntArray("Floor");
        byte[] water = tag.getByteArray("Water");
        if (width < 1 || height < 1 || floor.length != width * height || water.length != width * height) {
            return null;
        }
        short[] floors = new short[floor.length];
        for (int k = 0; k < floor.length; k++) {
            floors[k] = (short) floor[k];
        }
        return new Relief(tag.getInt("MinX"), tag.getInt("MinZ"), Math.max(1, tag.getInt("Cell")), width, height,
                floors, water);
    }

    private static RouteViewData load(CompoundTag tag, HolderLookup.Provider registries) {
        RouteViewData data = new RouteViewData();
        for (Tag element : tag.getList("Routes", Tag.TAG_COMPOUND)) {
            CompoundTag route = (CompoundTag) element;
            int[] coordinates = route.getIntArray("Points");
            byte[] codes = route.getByteArray("Surfaces");
            if (coordinates.length < 4 || coordinates.length % 2 != 0 || codes.length != coordinates.length / 2 - 1) {
                continue;
            }
            List<Route.Point> points = new ArrayList<>();
            for (int i = 0; i < coordinates.length; i += 2) {
                points.add(new Route.Point(coordinates[i], coordinates[i + 1]));
            }
            List<Route.Surface> surfaces = new ArrayList<>();
            for (byte code : codes) {
                surfaces.add(Route.Surface.fromCode(code));
            }
            Snapshot snapshot = new Snapshot(route.getString("From"), route.getString("To"),
                    route.getBoolean("Detailed"), new Route(points, surfaces));
            String key = Route.pairKey(snapshot.fromId(), snapshot.toId());
            data.routes.put(key, snapshot);
            if (route.contains("Relief")) {
                Relief relief = loadRelief(route.getCompound("Relief"));
                if (relief != null) {
                    data.reliefs.put(key, relief);
                }
            }
        }
        return data;
    }
}
