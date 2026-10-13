package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData.RouteEntry;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;

/**
 * The routes between the settlements.
 *
 * <ul>
 *   <li>The mod calculates a route once (see {@link RouteFinder}) and keeps it in {@link MobileGroupData}. It also
 *       keeps the result "no route".</li>
 *   <li>The heights come from the chunk generator of the Overworld. The calculation does not generate chunks. The
 *       water depth is the difference between the surface height (water included) and the floor height.</li>
 *   <li>A route is for the two directions. When the position of a settlement moves by more than one grid step (for
 *       example when its heart loads), the mod calculates the route again.</li>
 * </ul>
 */
public final class CaravanRoutes {
    /** Extra cost for each block of height change. */
    private static final double SLOPE_COST = 2.0;
    /** Extra cost for each block of distance in water. */
    private static final double WATER_COST = 3.0;

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
     * The route from one settlement to another. The mod calculates the route if it is not in the data.
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
     * Calculates the route from one settlement to another and keeps it in the data.
     *
     * @param server the server
     * @param from   the settlement at the start
     * @param to     the settlement at the end
     * @return the calculation
     */
    public static Calculation calculate(MinecraftServer server, Settlement from, Settlement to) {
        long start = System.nanoTime();
        ServerLevel level = server.overworld();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState randomState = level.getChunkSource().randomState();
        RouteFinder.HeightSource heights = (x, z) -> {
            int floor = generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, randomState);
            int surface = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState);
            return new RouteFinder.Column(floor - 1, surface - floor);
        };
        RouteFinder.Result result = RouteFinder.find(from.x(), from.z(), to.x(), to.z(), heights, settings());
        MobileGroupData.get(server).putRoute(new RouteEntry(from.id(), to.id(), from.x(), from.z(), to.x(), to.z(),
                result.route()));
        return new Calculation(result.route(), result.examined(), System.nanoTime() - start);
    }

    /** The settings of the search from the server configuration. */
    public static RouteFinder.Settings settings() {
        return new RouteFinder.Settings(WorldConfig.ROUTE_STEP.get(), WorldConfig.ROUTE_MAX_WATER_DEPTH.get(), SLOPE_COST, WATER_COST, WorldConfig.ROUTE_MAX_LENGTH.get(),
                WorldConfig.ROUTE_MAX_NODES.get());
    }

    /** The same route in the other direction. */
    public static Route reverse(Route route) {
        List<Route.Point> points = new ArrayList<>(route.points());
        Collections.reverse(points);
        return new Route(points);
    }

    private static boolean moved(int x, int z, Settlement settlement, int step) {
        return Math.abs(settlement.x() - x) > step || Math.abs(settlement.z() - z) > step;
    }
}
