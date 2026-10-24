package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Relief;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * The relief of the map of a route in the journal: the floor height and the water of a grid of cells around the
 * route.
 *
 * <ul>
 *   <li>The heights come from the chunk generator, as for the routes (see {@link CaravanRoutes#terrain}). The worker
 *       thread of the routes calculates them, after the routes on the way.</li>
 *   <li>The grid has {@link #CELLS} cells or less on its long side. Its area is the area of the route, with a
 *       margin.</li>
 *   <li>The server keeps the relief of each pair of villages while it runs. A relief that covers the area of a new
 *       route stays valid.</li>
 * </ul>
 */
public final class RouteReliefs {
    /** The maximum number of cells on the long side of the grid. */
    static final int CELLS = 48;
    /** The smallest size of a cell, in blocks. */
    private static final int MIN_CELL = 4;
    /** The smallest margin around the area of the route, in blocks. */
    private static final int MIN_MARGIN = 32;

    private static final Map<String, Relief> RELIEFS = new ConcurrentHashMap<>();
    private static final Set<String> PENDING = ConcurrentHashMap.newKeySet();

    private RouteReliefs() {
    }

    /**
     * The relief of the map of a route.
     *
     * @param server the server
     * @param key    the key of the pair of villages
     * @param route  the route
     * @return the relief, or an empty value while the worker thread calculates it
     */
    public static Optional<Relief> relief(MinecraftServer server, String key, Route route) {
        int[] area = area(route);
        Relief known = RELIEFS.get(key);
        if (known != null && covers(known, area)) {
            return Optional.of(known);
        }
        if (PENDING.add(key)) {
            ServerLevel level = server.overworld();
            RouteFinder.HeightSource terrain = CaravanRoutes.terrain(level);
            boolean started = CaravanRoutes.runOnWorker(server, () -> calculate(terrain, area), relief -> {
                PENDING.remove(key);
                RELIEFS.put(key, relief);
            });
            if (!started) {
                PENDING.remove(key);
            }
        }
        return Optional.empty();
    }

    /** True if the server calculates the relief of a pair of villages. */
    public static boolean isPending(String key) {
        return PENDING.contains(key);
    }

    /** Forgets the reliefs. */
    public static void onServerStopping(ServerStoppingEvent event) {
        RELIEFS.clear();
        PENDING.clear();
    }

    /** The area of the route: min X, min Z, max X, max Z, in blocks. */
    private static int[] area(Route route) {
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (Route.Point point : route.points()) {
            minX = Math.min(minX, point.x());
            minZ = Math.min(minZ, point.z());
            maxX = Math.max(maxX, point.x());
            maxZ = Math.max(maxZ, point.z());
        }
        return new int[] {minX, minZ, maxX, maxZ};
    }

    private static boolean covers(Relief relief, int[] area) {
        return relief.minX() <= area[0] && relief.minZ() <= area[1]
                && relief.minX() + (relief.width() - 1) * relief.cell() >= area[2]
                && relief.minZ() + (relief.height() - 1) * relief.cell() >= area[3];
    }

    /** Calculates the relief of an area with a margin. On the worker thread. */
    static Relief calculate(RouteFinder.HeightSource terrain, int[] area) {
        int spanX = area[2] - area[0];
        int spanZ = area[3] - area[1];
        int margin = Math.max(MIN_MARGIN, Math.max(spanX, spanZ) * 15 / 100);
        int minX = area[0] - margin;
        int minZ = area[1] - margin;
        int span = Math.max(spanX, spanZ) + 2 * margin;
        int cell = Math.max(MIN_CELL, (span + CELLS - 2) / (CELLS - 1));
        int width = (spanX + 2 * margin) / cell + 2;
        int height = (spanZ + 2 * margin) / cell + 2;
        short[] floor = new short[width * height];
        byte[] water = new byte[width * height];
        for (int j = 0; j < height; j++) {
            for (int i = 0; i < width; i++) {
                RouteFinder.Column column = terrain.column(minX + i * cell, minZ + j * cell);
                floor[j * width + i] = (short) column.floor();
                water[j * width + i] = (byte) Math.min(Byte.MAX_VALUE, Math.max(0, column.water()));
            }
        }
        return new Relief(minX, minZ, cell, width, height, floor, water);
    }
}
