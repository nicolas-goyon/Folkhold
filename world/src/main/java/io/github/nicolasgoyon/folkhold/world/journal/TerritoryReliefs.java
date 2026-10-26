package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.network.MapReliefPayload;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Relief;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

/**
 * The relief of the journal map: the floor height and the water of a grid of cells around the discovered territories
 * of a player.
 *
 * <ul>
 *   <li>The area contains the claims of the discovered villages, and a margin of {@link #MARGIN} blocks. Its sides
 *       are on multiples of {@link #SNAP} blocks: a small change of the territories keeps the same area.</li>
 *   <li>The grid has {@link #CELLS} cells or less on its long side. A cell has {@link #MIN_CELL} blocks or more.</li>
 *   <li>The heights come from the chunk generator, as for the map of a route (see {@link RouteReliefs}). The worker
 *       thread of the routes calculates them.</li>
 *   <li>The server keeps the last {@link #KEPT} reliefs in memory. When a calculation ends, the server sends the
 *       relief to the players that asked for it. While the calculation runs, the journal asks again from time to
 *       time.</li>
 * </ul>
 * All the methods run on the server thread.
 */
public final class TerritoryReliefs {
    /** The maximum number of cells on the long side of the grid. */
    static final int CELLS = 96;
    /** The smallest size of a cell, in blocks. */
    static final int MIN_CELL = 8;
    /** The sides of an area are on multiples of this number of blocks. */
    static final int SNAP = 256;
    /** The smallest margin around the claims, in blocks. */
    static final int MARGIN = 64;
    /** The number of reliefs that the server keeps in memory. */
    static final int KEPT = 8;

    /** The last calculated reliefs, the least recently used first. */
    private static final Map<Area, Relief> RELIEFS = new LinkedHashMap<>(16, 0.75F, true);
    /** The players that wait for the relief of an area that the worker thread calculates. */
    private static final Map<Area, Set<UUID>> WAITING = new HashMap<>();

    private TerritoryReliefs() {
    }

    /**
     * An area of the map, in blocks. The sides are on multiples of {@link #SNAP} blocks.
     *
     * @param minX the lowest block X
     * @param minZ the lowest block Z
     * @param maxX the block X after the area
     * @param maxZ the block Z after the area
     */
    record Area(int minX, int minZ, int maxX, int maxZ) {

        /** The size of a cell of the grid of the area, in blocks. */
        int cell() {
            int span = Math.max(maxX - minX, maxZ - minZ);
            return Math.max(MIN_CELL, Math.ceilDiv(span, CELLS));
        }

        /** The number of columns of the grid. */
        int width() {
            return Math.ceilDiv(maxX - minX, cell());
        }

        /** The number of rows of the grid. */
        int height() {
            return Math.ceilDiv(maxZ - minZ, cell());
        }
    }

    /**
     * Sends the relief of the map of the journal to a player. If the server does not have the relief, it sends
     * {@link MapReliefPayload#PENDING} and starts the calculation. The server sends the relief when the calculation
     * ends.
     *
     * @param player the player
     */
    public static void request(ServerPlayer player) {
        Optional<Area> found = area(TerritoryMaps.payload(player).villages());
        if (found.isEmpty()) {
            ModNetwork.send(player, MapReliefPayload.EMPTY);
            return;
        }
        Area area = found.get();
        Relief known = RELIEFS.get(area);
        if (known != null) {
            ModNetwork.send(player, MapReliefPayload.ready(known));
            return;
        }
        ModNetwork.send(player, MapReliefPayload.PENDING);
        Set<UUID> waiting = WAITING.get(area);
        if (waiting != null) {
            waiting.add(player.getUUID());
            return;
        }
        waiting = new HashSet<>();
        waiting.add(player.getUUID());
        WAITING.put(area, waiting);
        MinecraftServer server = player.server;
        RouteFinder.HeightSource terrain = CaravanRoutes.terrain(server.overworld());
        boolean started = CaravanRoutes.runOnWorker(server, () -> calculateSafely(terrain, area),
                relief -> finish(server, area, relief));
        if (!started) {
            // The journal asks again later.
            WAITING.remove(area);
        }
    }

    /**
     * The relief of the map of a player, if the server keeps it.
     *
     * @param player the player
     * @return the relief of the area of the discovered territories of the player, or empty
     */
    public static Optional<Relief> known(ServerPlayer player) {
        return area(TerritoryMaps.payload(player).villages()).map(RELIEFS::get);
    }

    /** Forgets the reliefs and the players that wait. */
    public static void onServerStopping(ServerStoppingEvent event) {
        RELIEFS.clear();
        WAITING.clear();
    }

    /**
     * Keeps a calculated relief, and sends it to the players that wait for it. After an error, the next request of
     * the journal starts a new calculation.
     */
    private static void finish(MinecraftServer server, Area area, Optional<Relief> result) {
        Set<UUID> waiting = WAITING.remove(area);
        if (result.isEmpty()) {
            return;
        }
        Relief relief = result.get();
        RELIEFS.put(area, relief);
        while (RELIEFS.size() > KEPT) {
            RELIEFS.remove(RELIEFS.keySet().iterator().next());
        }
        if (waiting == null) {
            return;
        }
        for (UUID id : waiting) {
            ServerPlayer player = server.getPlayerList().getPlayer(id);
            if (player != null) {
                ModNetwork.send(player, MapReliefPayload.ready(relief));
            }
        }
    }

    /**
     * The area of the map: the claims of the villages and a margin, with the sides on multiples of {@link #SNAP}
     * blocks.
     *
     * @param villages the discovered villages with a territory
     * @return the area, or empty for no villages
     */
    static Optional<Area> area(List<TerritoryMapPayload.Village> villages) {
        if (villages.isEmpty()) {
            return Optional.empty();
        }
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (TerritoryMapPayload.Village village : villages) {
            Claimant claim = village.claimant();
            int reach = 16 * (claim.radius() + 1);
            minX = Math.min(minX, claim.x() - reach);
            minZ = Math.min(minZ, claim.z() - reach);
            maxX = Math.max(maxX, claim.x() + reach);
            maxZ = Math.max(maxZ, claim.z() + reach);
        }
        return Optional.of(new Area(Math.floorDiv(minX - MARGIN, SNAP) * SNAP,
                Math.floorDiv(minZ - MARGIN, SNAP) * SNAP, -Math.floorDiv(-(maxX + MARGIN), SNAP) * SNAP,
                -Math.floorDiv(-(maxZ + MARGIN), SNAP) * SNAP));
    }

    /** Calculates the relief of an area. An exception gives an empty value. On the worker thread. */
    private static Optional<Relief> calculateSafely(RouteFinder.HeightSource terrain, Area area) {
        try {
            return Optional.of(calculate(terrain, area));
        } catch (RuntimeException e) {
            FolkholdWorld.LOGGER.error("The calculation of the relief of the journal map failed", e);
            return Optional.empty();
        }
    }

    /** Calculates the relief of an area: one column of the terrain at the center of each cell. On the worker thread. */
    static Relief calculate(RouteFinder.HeightSource terrain, Area area) {
        int cell = area.cell();
        return RouteReliefs.sample(terrain, area.minX() + cell / 2, area.minZ() + cell / 2, cell, area.width(),
                area.height());
    }
}
