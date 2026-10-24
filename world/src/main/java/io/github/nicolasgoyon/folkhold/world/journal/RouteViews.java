package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Status;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.annotation.Nullable;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * The routes of the journal: the route of the caravans between two villages that the player discovered.
 *
 * <ul>
 *   <li>The server shows only the routes between two villages of the journal of the player.</li>
 *   <li>The route is the route that the caravans take (see {@link CaravanRoutes}): the detailed route, or the coarse
 *       route when no detailed route is in the corridor.</li>
 *   <li>The server does not calculate a route on the server thread. When a route is not in the data, the worker
 *       thread calculates it, and the answer is {@link Status#PENDING}, with the progress of the calculation. The
 *       journal asks again some seconds later. The calculated route stays in the data, so the next caravan between
 *       the two villages uses it.</li>
 *   <li>The answer also has the relief of the map ({@link RouteReliefs}) when the server calculated it.</li>
 *   <li>The player can ask for a new calculation of the route ({@link #recalculate}).</li>
 * </ul>
 */
public final class RouteViews {
    /** The minimum time between two new calculations that a player asks for, in ticks. */
    static final long RECALCULATE_TICKS = 20L * 20L;
    /** The game time of the last new calculation that each player asked for. */
    private static final Map<UUID, Long> LAST_RECALCULATION = new ConcurrentHashMap<>();

    private RouteViews() {
    }

    /**
     * Sends the route between two villages to a player.
     *
     * @param player      the player
     * @param fromId      the settlement id of the start
     * @param toId        the settlement id of the end
     * @param recalculate true to calculate the route again first (see {@link #recalculate})
     */
    public static void request(ServerPlayer player, String fromId, String toId, boolean recalculate) {
        if (recalculate) {
            recalculate(player, fromId, toId);
        }
        ModNetwork.send(player, answer(player, fromId, toId));
    }

    /**
     * Forgets the route between two villages of the journal of a player, and its relief: the next answer requests
     * them again. The caravans also use the new route. A player can ask for this once each
     * {@link #RECALCULATE_TICKS} ticks. While the worker thread calculates the route, nothing changes.
     *
     * @param player the player
     * @param fromId the settlement id of the start
     * @param toId   the settlement id of the end
     * @return true if the server forgot the route
     */
    public static boolean recalculate(ServerPlayer player, String fromId, String toId) {
        MinecraftServer server = player.server;
        Map<String, JournalData.Visit> journal = JournalData.get(server).visits(player.getUUID());
        if (fromId.equals(toId) || !journal.containsKey(fromId) || !journal.containsKey(toId)
                || CaravanRoutes.state(CaravanRoutes.coarseKey(fromId, toId)).isPresent()
                || CaravanRoutes.state(CaravanRoutes.detailKey(fromId, toId)).isPresent()) {
            return false;
        }
        long now = server.overworld().getGameTime();
        Long last = LAST_RECALCULATION.get(player.getUUID());
        if (last != null && now - last < RECALCULATE_TICKS && now >= last) {
            return false;
        }
        LAST_RECALCULATION.put(player.getUUID(), now);
        RouteReliefs.forget(Route.pairKey(fromId, toId));
        MobileGroupData.get(server).forgetRoute(fromId, toId);
        return true;
    }

    /**
     * The route between two villages for a player.
     *
     * @param player the player
     * @param fromId the settlement id of the start
     * @param toId   the settlement id of the end
     * @return the payload. It has a route if the status is {@link Status#READY}, and can have the coarse route if the
     *         status is {@link Status#PENDING}.
     */
    public static RoutePayload answer(ServerPlayer player, String fromId, String toId) {
        MinecraftServer server = player.server;
        Map<String, JournalData.Visit> journal = JournalData.get(server).visits(player.getUUID());
        if (fromId.equals(toId) || !journal.containsKey(fromId) || !journal.containsKey(toId)) {
            return RoutePayload.empty(fromId, toId, Status.REFUSED);
        }
        SettlementData settlements = SettlementData.get(server);
        Optional<Settlement> from = settlements.registry().byId(fromId);
        Optional<Settlement> to = settlements.registry().byId(toId);
        if (from.isEmpty() || to.isEmpty()) {
            return RoutePayload.empty(fromId, toId, Status.REFUSED);
        }
        RoutePayload route = route(server, from.get(), to.get());
        Optional<Route> shown = route.route();
        RoutePayload.Relief relief = shown.flatMap(found -> RouteReliefs.relief(server,
                Route.pairKey(fromId, toId), found)).orElse(null);
        boolean reliefPending = shown.isPresent() && relief == null
                && RouteReliefs.isPending(Route.pairKey(fromId, toId));
        RoutePayload.Progress progress = progress(fromId, toId, route);
        return route.with(progress, relief, route.status() == Status.PENDING || reliefPending);
    }

    /** The route between two settlements, with no progress and no relief. */
    private static RoutePayload route(MinecraftServer server, Settlement from, Settlement to) {
        Optional<Route> coarse = CaravanRoutes.cached(server, from, to);
        if (coarse == null) {
            CaravanRoutes.request(server, from, to);
            return RoutePayload.empty(from.id(), to.id(), Status.PENDING);
        }
        if (coarse.isEmpty()) {
            return RoutePayload.empty(from.id(), to.id(), Status.NONE);
        }
        Optional<Route> detail = CaravanRoutes.cachedDetail(server, from, to);
        if (detail == null) {
            CaravanRoutes.requestDetail(server, from, to, coarse.get());
            return RoutePayload.of(from.id(), to.id(), Status.PENDING, false, coarse.get());
        }
        return detail.map(found -> RoutePayload.of(from.id(), to.id(), Status.READY, true, found))
                .orElseGet(() -> RoutePayload.of(from.id(), to.id(), Status.READY, false, coarse.get()));
    }

    /** The progress of the calculation of a pending route, or null. */
    @Nullable
    private static RoutePayload.Progress progress(String fromId, String toId, RoutePayload route) {
        if (route.status() != Status.PENDING) {
            return null;
        }
        boolean detail = route.route().isPresent();
        String key = detail ? CaravanRoutes.detailKey(fromId, toId) : CaravanRoutes.coarseKey(fromId, toId);
        return CaravanRoutes.state(key).map(state -> new RoutePayload.Progress(detail, state.ahead(),
                state.examined(), state.budget(), (int) Math.round(100 * state.fraction()))).orElse(null);
    }
}
