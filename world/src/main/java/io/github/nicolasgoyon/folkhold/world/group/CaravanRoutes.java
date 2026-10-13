package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData.RouteEntry;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
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
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

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
 * </ul>
 */
public final class CaravanRoutes {
    /** Extra cost for each block of height change. */
    private static final double SLOPE_COST = 2.0;
    /** Extra cost for each block of distance in water. */
    private static final double WATER_COST = 3.0;
    /** Weight of the heuristic of the search: fewer examined cells, a route a little longer than the best route. */
    private static final double HEURISTIC_WEIGHT = 1.5;
    /** Name of the measurements of the routes in the profiler. */
    public static final String PROFILER_NAME = "routes (worker thread)";

    /** The pairs of settlements with a route on the way, as "first|second". */
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();
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
        return forward ? entry.route() : entry.route().map(CaravanRoutes::reverse);
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
        RouteFinder.HeightSource heights = heights(server.overworld());
        RouteFinder.Settings settings = settings();
        try {
            worker().execute(() -> {
                long start = System.nanoTime();
                RouteFinder.Result result;
                try {
                    result = RouteFinder.find(fromX, fromZ, toX, toZ, heights, settings);
                } catch (RuntimeException e) {
                    FolkholdWorld.LOGGER.error("Route calculation failed: {} -> {}", fromId, toId, e);
                    PENDING.remove(key);
                    return;
                }
                long nanos = System.nanoTime() - start;
                server.execute(() -> {
                    if (PENDING.remove(key)) {
                        MobileGroupData.get(server).putRoute(new RouteEntry(fromId, toId, fromX, fromZ, toX, toZ,
                                result.route()));
                        SimulationProfiler.record(PROFILER_NAME, nanos);
                    }
                });
            });
            return true;
        } catch (RejectedExecutionException e) {
            PENDING.remove(key);
            return false;
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

    /** The settings of the search from the server configuration. */
    public static RouteFinder.Settings settings() {
        return new RouteFinder.Settings(WorldConfig.ROUTE_STEP.get(), WorldConfig.ROUTE_MAX_WATER_DEPTH.get(),
                SLOPE_COST, WATER_COST, WorldConfig.ROUTE_MAX_LENGTH.get(), WorldConfig.ROUTE_MAX_NODES.get(),
                HEURISTIC_WEIGHT);
    }

    /** The same route in the other direction. */
    public static Route reverse(Route route) {
        List<Route.Point> points = new ArrayList<>(route.points());
        Collections.reverse(points);
        return new Route(points);
    }

    /** Stops the worker thread and forgets the routes on the way. */
    public static void onServerStopped(ServerStoppedEvent event) {
        if (worker != null) {
            worker.shutdownNow();
            worker = null;
        }
        PENDING.clear();
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
                thread.setPriority(Thread.MIN_PRIORITY);
                return thread;
            });
        }
        return worker;
    }

    private static String key(String firstId, String secondId) {
        return firstId.compareTo(secondId) <= 0 ? firstId + "|" + secondId : secondId + "|" + firstId;
    }

    private static boolean moved(int x, int z, Settlement settlement, int step) {
        return Math.abs(settlement.x() - x) > step || Math.abs(settlement.z() - z) > step;
    }
}
