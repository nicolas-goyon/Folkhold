package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData.RouteEntry;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * The routes between the settlements.
 *
 * <ul>
 *   <li>The mod calculates a route once (see {@link RouteFinder}) and keeps it in {@link MobileGroupData}. It also
 *       keeps the result "no route".</li>
 *   <li>The heights come from the chunk generator of the Overworld. The calculation does not generate chunks. The
 *       water depth is the difference between the surface height (water included) and the floor height.</li>
 *   <li>One height costs a calculation of the terrain noise, and a search reads thousands of heights. So the
 *       simulation requests the routes ({@link #request}): one worker thread calculates them, and the server thread
 *       puts each result into the data. The simulation uses only the routes that are in the data
 *       ({@link #cached}). The chunk generator calculates the same heights on the world generation threads, so it
 *       accepts calls from the worker thread.</li>
 *   <li>The commands of the operators calculate a route on the server thread ({@link #calculate}).</li>
 *   <li>A route is for the two directions. When the position of a settlement moves by more than one grid step (for
 *       example when its heart loads), the mod calculates the route again.</li>
 *   <li>Two levels of routes:
 *     <ul>
 *       <li>The coarse route ({@link WorldConfig#ROUTE_STEP}, {@link #settings()}) tells if two settlements can
 *           trade. It does not see the cliffs.</li>
 *       <li>The detailed route ({@link WorldConfig#ROUTE_DETAIL_STEP}, {@link #detailSettings()}) is in a corridor
 *           around the coarse route. A cliff is not passable, and each segment has its surface: land, ford or
 *           water (boats). A new group waits at its village until its detailed route is ready ({@link #prepare}),
 *           also after its departure tick. If no detailed route is in the corridor, the group uses the coarse
 *           route.</li>
 *     </ul>
 *   </li>
 * </ul>
 */
public final class CaravanRoutes {
    /** Extra cost for each block of height change. */
    private static final double SLOPE_COST = 2.0;
    /** Maximum water depth of a ford, in blocks. Deeper water needs boats. */
    private static final int FORD_DEPTH = 2;
    /** Extra cost for each block of distance in a ford. */
    private static final double FORD_COST = 3.0;
    /** Extra cost for each block of distance in a boat. */
    private static final double BOAT_COST = 1.0;
    /** Extra cost each time the group gets into or out of the boats. */
    private static final double EMBARK_COST = 64.0;
    /** Weight of the heuristic of the coarse search: fewer examined cells, a route a little longer than the best. */
    private static final double HEURISTIC_WEIGHT = 1.5;
    /** Weight of the heuristic of the detailed search. */
    private static final double DETAIL_HEURISTIC_WEIGHT = 1.2;
    /** A detailed route goes on a fine grid: it can be longer than the maximum length of a coarse route. */
    private static final double DETAIL_LENGTH_FACTOR = 1.5;
    /** Name of the measurements of the routes in the profiler. */
    public static final String PROFILER_NAME = "routes (worker thread)";
    /** Name of the measurements of the detailed routes in the profiler. */
    public static final String DETAIL_PROFILER_NAME = "detailed routes (worker thread)";

    /** The pairs of settlements with a route on the way, as "first|second". */
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
    /** The ids of the groups with a detailed route on the way. */
    private static final Set<String> PENDING_DETAILS = ConcurrentHashMap.newKeySet();
    private static ExecutorService worker;

    /**
     * Result of a calculation.
     *
     * @param route    the route, or an empty value if there is no route
     * @param examined the number of grid cells that the search examined
     * @param nanos    the time of the calculation in nanoseconds
     */
    public record Calculation(Optional<Route> route, int examined, long nanos) {
    }

    private CaravanRoutes() {
    }

    /**
     * The route from one settlement to another, if the mod calculated it with the current positions.
     *
     * @param server the server
     * @param from   the settlement at the start
     * @param to     the settlement at the end
     * @return the route (an empty value means "no route"), or null if the route is not calculated
     */
    public static Optional<Route> cached(MinecraftServer server, Settlement from, Settlement to) {
        RouteEntry entry = MobileGroupData.get(server).routeEntry(from.id(), to.id());
        if (entry == null) {
            return null;
        }
        boolean forward = entry.fromId().equals(from.id());
        Settlement start = forward ? from : to;
        Settlement end = forward ? to : from;
        int step = WorldConfig.ROUTE_STEP.get();
        if (moved(entry.fromX(), entry.fromZ(), start, step) || moved(entry.toX(), entry.toZ(), end, step)) {
            return null;
        }
        return forward ? entry.route() : entry.route().map(Route::reversed);
    }

    /**
     * Gives a group with a new journey its detailed route. If the detailed route between the two settlements is in
     * the data, the group gets it at once. Else the group waits until the worker thread calculates it.
     *
     * @param server the server
     * @param group  the group, with its coarse route
     */
    public static void prepare(MinecraftServer server, MobileGroup group) {
        Route.Point start = endpoint(server, group.originId(), group.route().points().get(0));
        Route.Point end = endpoint(server, group.destinationId(),
                group.route().points().get(group.route().points().size() - 1));
        Optional<Route> cached = cachedDetail(server, group.originId(), group.destinationId(), start, end);
        if (cached != null) {
            cached.ifPresent(group::setRoute);
            group.setRouteReady(true);
            return;
        }
        group.setRouteReady(false);
        requestDetail(server, group);
    }

    /**
     * The detailed route between two settlements, if the mod calculated it with the current positions.
     *
     * @return the route (an empty value means "no detailed route"), or null if the route is not calculated
     */
    private static Optional<Route> cachedDetail(MinecraftServer server, String fromId, String toId, Route.Point start,
            Route.Point end) {
        RouteEntry entry = MobileGroupData.get(server).detailedRouteEntry(fromId, toId);
        if (entry == null) {
            return null;
        }
        boolean forward = entry.fromId().equals(fromId);
        Route.Point entryStart = forward ? new Route.Point(entry.fromX(), entry.fromZ())
                : new Route.Point(entry.toX(), entry.toZ());
        Route.Point entryEnd = forward ? new Route.Point(entry.toX(), entry.toZ())
                : new Route.Point(entry.fromX(), entry.fromZ());
        int tolerance = WorldConfig.ROUTE_DETAIL_STEP.get() * 2;
        if (far(entryStart, start, tolerance) || far(entryEnd, end, tolerance)) {
            return null;
        }
        return forward ? entry.route() : entry.route().map(Route::reversed);
    }

    /**
     * Requests the detailed route of a waiting group on the worker thread. The search uses a corridor around the
     * route of the group. On the server thread, the result goes into the data, and the group gets it if it still
     * waits for it. With no detailed route, the group keeps its route.
     *
     * @param server the server
     * @param group  the group
     * @return true if the route is on the way (new or earlier request)
     */
    public static boolean requestDetail(MinecraftServer server, MobileGroup group) {
        String groupId = group.id();
        if (!PENDING_DETAILS.add(groupId)) {
            return true;
        }
        String originId = group.originId();
        String destinationId = group.destinationId();
        Route coarse = group.route();
        Route.Point start = endpoint(server, originId, coarse.points().get(0));
        Route.Point end = endpoint(server, destinationId, coarse.points().get(coarse.points().size() - 1));
        RouteFinder.HeightSource heights = heights(server.overworld());
        RouteFinder.Settings settings = detailSettings();
        double width = WorldConfig.ROUTE_CORRIDOR.get();
        try {
            worker().execute(() -> {
                long startTime = System.nanoTime();
                RouteFinder.Result result;
                try {
                    result = RouteFinder.refine(start.x(), start.z(), end.x(), end.z(), coarse, width,
                            memoized(heights), settings);
                } catch (RuntimeException e) {
                    FolkholdWorld.LOGGER.error("Detailed route calculation failed: {} -> {}", originId,
                            destinationId, e);
                    result = new RouteFinder.Result(Optional.empty(), 0);
                }
                long nanos = System.nanoTime() - startTime;
                Optional<Route> detail = result.route();
                server.execute(() -> {
                    if (!server.isSameThread() || !PENDING_DETAILS.remove(groupId)) {
                        return;
                    }
                    SimulationProfiler.record(DETAIL_PROFILER_NAME, nanos);
                    MobileGroupData data = MobileGroupData.get(server);
                    data.putDetailedRoute(new RouteEntry(originId, destinationId, start.x(), start.z(), end.x(),
                            end.z(), detail));
                    data.groups().byId(groupId).ifPresent(waiting ->
                            applyDetail(waiting, originId, destinationId, detail));
                    data.setDirty();
                });
            });
            return true;
        } catch (RejectedExecutionException e) {
            PENDING_DETAILS.remove(groupId);
            return false;
        }
    }

    /** The number of detailed routes on the way. */
    public static int pendingDetails() {
        return PENDING_DETAILS.size();
    }

    /** True if the detailed route of a group is on the way. */
    public static boolean isDetailPending(MobileGroup group) {
        return PENDING_DETAILS.contains(group.id());
    }

    /**
     * Forgets the routes on the way that use an id of settlement: a result that comes back with the old id is
     * dropped. The simulation asks for the routes again with the new id.
     *
     * @param server the server
     * @param oldId  the id that the settlement had
     */
    public static void forgetPending(MinecraftServer server, String oldId) {
        PENDING.removeIf(key -> key.startsWith(oldId + "|") || key.endsWith("|" + oldId));
        for (MobileGroup group : MobileGroupData.get(server).groups().all()) {
            if (group.originId().equals(oldId) || group.destinationId().equals(oldId)) {
                PENDING_DETAILS.remove(group.id());
            }
        }
    }

    /** Gives a detailed route to a group that still waits for it, on the same journey. */
    private static void applyDetail(MobileGroup group, String originId, String destinationId, Optional<Route> detail) {
        if (group.isRouteReady() || !group.originId().equals(originId)
                || !group.destinationId().equals(destinationId)) {
            return;
        }
        detail.ifPresent(group::setRoute);
        group.setRouteReady(true);
    }

    /** The position of a settlement (its heart if known), or a default point if the settlement is not known. */
    private static Route.Point endpoint(MinecraftServer server, String settlementId, Route.Point fallback) {
        return SettlementData.get(server).registry().byId(settlementId)
                .map(settlement -> new Route.Point(settlement.x(), settlement.z()))
                .orElse(fallback);
    }

    /** The heights with a memory: the two corridors of a refinement read the same cells. */
    private static RouteFinder.HeightSource memoized(RouteFinder.HeightSource heights) {
        Map<Long, RouteFinder.Column> memory = new HashMap<>();
        return (x, z) -> memory.computeIfAbsent(((long) x << 32) | (z & 0xFFFFFFFFL), key -> heights.column(x, z));
    }

    /**
     * The route from one settlement to another. The mod calculates the route on the server thread if it is not in
     * the data. Only for the commands of the operators: the simulation uses {@link #request}.
     *
     * @param server the server
     * @param from   the settlement at the start
     * @param to     the settlement at the end
     * @return the route, or an empty value if there is no route
     */
    public static Optional<Route> route(MinecraftServer server, Settlement from, Settlement to) {
        Optional<Route> cached = cached(server, from, to);
        return cached != null ? cached : calculate(server, from, to).route();
    }

    /**
     * Requests the calculation of a route on the worker thread. The result goes into the data on the server thread,
     * some ticks later.
     *
     * @param server the server
     * @param from   the settlement at the start
     * @param to     the settlement at the end
     * @return true if the route is on the way (new or earlier request)
     */
    public static boolean request(MinecraftServer server, Settlement from, Settlement to) {
        return request(server, from, to, heights(server.overworld()));
    }

    /**
     * Requests the calculation of a route on the worker thread, with the given heights. The result goes into the
     * data on the server thread, some ticks later. When the search fails, the data gets "no route" for the pair,
     * so the simulation does not ask for the same route again. The result is dropped when the server stops before
     * the worker thread finishes.
     *
     * @param server  the server
     * @param from    the settlement at the start
     * @param to      the settlement at the end
     * @param heights the heights of the terrain
     * @return true if the route is on the way (new or earlier request)
     */
    public static boolean request(MinecraftServer server, Settlement from, Settlement to,
            RouteFinder.HeightSource heights) {
        String key = key(from.id(), to.id());
        if (!PENDING.add(key)) {
            return true;
        }
        String fromId = from.id();
        String toId = to.id();
        int fromX = from.x();
        int fromZ = from.z();
        int toX = to.x();
        int toZ = to.z();
        RouteFinder.Settings settings = settings();
        try {
            worker().execute(() -> {
                long start = System.nanoTime();
                Optional<Route> route = find(fromId, toId, fromX, fromZ, toX, toZ, heights, settings);
                long nanos = System.nanoTime() - start;
                server.execute(() -> {
                    if (!server.isSameThread() || !PENDING.remove(key)) {
                        return;
                    }
                    MobileGroupData.get(server).putRoute(new RouteEntry(fromId, toId, fromX, fromZ, toX, toZ, route));
                    SimulationProfiler.record(PROFILER_NAME, nanos);
                });
            });
            return true;
        } catch (RejectedExecutionException e) {
            PENDING.remove(key);
            return false;
        }
    }

    /** The route of a search, or "no route" when the search fails. */
    private static Optional<Route> find(String fromId, String toId, int fromX, int fromZ, int toX, int toZ,
            RouteFinder.HeightSource heights, RouteFinder.Settings settings) {
        try {
            return RouteFinder.find(fromX, fromZ, toX, toZ, heights, settings).route();
        } catch (RuntimeException e) {
            FolkholdWorld.LOGGER.error("Route calculation failed: {} -> {}. The pair gets no route.", fromId, toId, e);
            return Optional.empty();
        }
    }

    /** The number of routes on the way. */
    public static int pending() {
        return PENDING.size();
    }

    /**
     * Calculates the route from one settlement to another on the server thread and keeps it in the data. Only for
     * the commands of the operators.
     *
     * @param server the server
     * @param from   the settlement at the start
     * @param to     the settlement at the end
     * @return the calculation
     */
    public static Calculation calculate(MinecraftServer server, Settlement from, Settlement to) {
        long start = System.nanoTime();
        RouteFinder.Result result = RouteFinder.find(from.x(), from.z(), to.x(), to.z(), heights(server.overworld()),
                settings());
        MobileGroupData.get(server).putRoute(new RouteEntry(from.id(), to.id(), from.x(), from.z(), to.x(), to.z(),
                result.route()));
        return new Calculation(result.route(), result.examined(), System.nanoTime() - start);
    }

    /** The settings of the coarse search from the server configuration. No height limit: the cells are large. */
    public static RouteFinder.Settings settings() {
        return new RouteFinder.Settings(WorldConfig.ROUTE_STEP.get(), FORD_DEPTH, RouteFinder.NO_RISE_LIMIT,
                SLOPE_COST, FORD_COST, WorldConfig.ROUTE_BOATS.get(), BOAT_COST, EMBARK_COST,
                WorldConfig.ROUTE_MAX_LENGTH.get(), WorldConfig.ROUTE_MAX_NODES.get(), HEURISTIC_WEIGHT);
    }

    /** The settings of the detailed search from the server configuration. */
    public static RouteFinder.Settings detailSettings() {
        return new RouteFinder.Settings(WorldConfig.ROUTE_DETAIL_STEP.get(), FORD_DEPTH,
                WorldConfig.ROUTE_MAX_RISE.get(), SLOPE_COST, FORD_COST, WorldConfig.ROUTE_BOATS.get(), BOAT_COST,
                EMBARK_COST, WorldConfig.ROUTE_MAX_LENGTH.get() * DETAIL_LENGTH_FACTOR,
                WorldConfig.ROUTE_DETAIL_MAX_NODES.get(), DETAIL_HEURISTIC_WEIGHT);
    }

    /**
     * Before the server saves the world: stops the worker thread and forgets the routes on the way. A result that
     * the worker thread hands back after this point is dropped (see {@link #request}).
     */
    public static synchronized void onServerStopping(ServerStoppingEvent event) {
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        PENDING.clear();
        PENDING_DETAILS.clear();
    }

    /** The heights of the Overworld from its chunk generator. The calculation generates no chunks. */
    private static RouteFinder.HeightSource heights(ServerLevel level) {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        return (x, z) -> {
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            return new RouteFinder.Column(floor - 1, surface - floor);
        };
    }

    private static synchronized ExecutorService worker() {
        if (worker == null) {
            worker = Executors.newSingleThreadExecutor(runnable -> {
                Thread thread = new Thread(runnable, "Folkhold routes");
                thread.setDaemon(true);
                return thread;
            });
        }
        return worker;
    }

    private static String key(String firstId, String secondId) {
        return firstId.compareTo(secondId) <= 0 ? firstId + "|" + secondId : secondId + "|" + firstId;
    }

    private static boolean far(Route.Point a, Route.Point b, int tolerance) {
        return Math.abs(a.x() - b.x()) > tolerance || Math.abs(a.z() - b.z()) > tolerance;
    }

    private static boolean moved(int x, int z, Settlement settlement, int step) {
        return Math.abs(settlement.x() - x) > step || Math.abs(settlement.z() - z) > step;
    }
}
