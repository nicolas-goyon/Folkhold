package io.github.nicolasgoyon.folkhold.world.journal;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.network.ModNetwork;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Status;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Map;
import java.util.Optional;
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
 *       thread calculates it, and the answer is {@link Status#PENDING}. The journal asks again some seconds later.
 *       The calculated route stays in the data, so the next caravan between the two villages uses it.</li>
 * </ul>
 */
public final class RouteViews {
    private RouteViews() {
    }

    /** Sends the route between two villages to a player. */
    public static void request(ServerPlayer player, String fromId, String toId) {
        ModNetwork.send(player, answer(player, fromId, toId));
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
        Optional<Route> coarse = CaravanRoutes.cached(server, from.get(), to.get());
        if (coarse == null) {
            CaravanRoutes.request(server, from.get(), to.get());
            return RoutePayload.empty(fromId, toId, Status.PENDING);
        }
        if (coarse.isEmpty()) {
            return RoutePayload.empty(fromId, toId, Status.NONE);
        }
        Optional<Route> detail = CaravanRoutes.cachedDetail(server, from.get(), to.get());
        if (detail == null) {
            CaravanRoutes.requestDetail(server, from.get(), to.get(), coarse.get());
            return RoutePayload.of(fromId, toId, Status.PENDING, false, coarse.get());
        }
        return detail.map(route -> RoutePayload.of(fromId, toId, Status.READY, true, route))
                .orElseGet(() -> RoutePayload.of(fromId, toId, Status.READY, false, coarse.get()));
    }
}
