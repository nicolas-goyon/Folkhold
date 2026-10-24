package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.RouteRequestPayload;
import java.util.Objects;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The route of the journal on the client: the two villages that the player selects in the tab "Routes" of the
 * journal, and the route that the server sends for them (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.RouteViews}).
 *
 * <ul>
 *   <li>When the two villages change, the client asks the server for the route.</li>
 *   <li>While the server calculates the route or its relief, the client asks again every {@link #POLL_TICKS} ticks,
 *       also when the journal is closed.</li>
 *   <li>The player can show the route in the world ({@link ClientRouteOverlay}).</li>
 * </ul>
 */
public final class ClientRouteView {
    /** The interval between two requests while the server calculates the route, in ticks. */
    static final int POLL_TICKS = 20;

    @Nullable
    private static String fromId;
    @Nullable
    private static String toId;
    @Nullable
    private static RoutePayload route;
    /** The last route of the two villages: the server can calculate the route again after a change of the roads. */
    private static Optional<Route> shown = Optional.empty();
    private static boolean shownDetailed;
    @Nullable
    private static RoutePayload.Relief shownRelief;
    private static boolean inWorld;
    private static int pollTimer;
    /** Increases each time the route changes. */
    private static int version;

    private ClientRouteView() {
    }

    /** The settlement id of the start, or null. */
    @Nullable
    public static String fromId() {
        return fromId;
    }

    /** The settlement id of the end, or null. */
    @Nullable
    public static String toId() {
        return toId;
    }

    /** The last route from the server for the two villages, or null before the first answer. */
    @Nullable
    public static RoutePayload route() {
        return route;
    }

    /**
     * The route to show for the two villages. While the server calculates the route again, it is the last route
     * that the server sent.
     */
    public static Optional<Route> shownRoute() {
        return shown;
    }

    /** The relief of the map of the route, or null before the server sends it. */
    @Nullable
    public static RoutePayload.Relief relief() {
        return shownRelief;
    }

    /** True if the route to show is the detailed route, false for the coarse route. */
    public static boolean isDetailed() {
        return shownDetailed;
    }

    /** True if the route shows in the world. */
    public static boolean inWorld() {
        return inWorld;
    }

    public static void setInWorld(boolean show) {
        inWorld = show;
    }

    /** A number that changes each time the route changes. */
    static int version() {
        return version;
    }

    /**
     * Selects the two villages. If the two villages change, the client forgets the route and asks the server for
     * the new route.
     *
     * @param from the settlement id of the start, or null
     * @param to   the settlement id of the end, or null
     */
    public static void select(@Nullable String from, @Nullable String to) {
        if (Objects.equals(from, fromId) && Objects.equals(to, toId)) {
            return;
        }
        fromId = from;
        toId = to;
        route = null;
        shown = Optional.empty();
        shownDetailed = false;
        shownRelief = null;
        version++;
        refresh();
    }

    /** Asks the server for the route of the two villages again, if the two villages are set. */
    public static void refresh() {
        if (fromId == null || toId == null || fromId.equals(toId) || Minecraft.getInstance().getConnection() == null) {
            return;
        }
        pollTimer = POLL_TICKS;
        PacketDistributor.sendToServer(new RouteRequestPayload(fromId, toId));
    }

    /** Receives a route from the server. A route for other villages is dropped. Called by the network handler. */
    public static void set(RoutePayload payload) {
        if (!payload.fromId().equals(fromId) || !payload.toId().equals(toId)) {
            return;
        }
        route = payload;
        if (payload.relief() != null) {
            shownRelief = payload.relief();
        }
        Optional<Route> received = payload.route();
        if (received.isPresent()) {
            shown = received;
            shownDetailed = payload.detailed();
        } else if (payload.status() != RoutePayload.Status.PENDING) {
            shown = Optional.empty();
            shownDetailed = false;
        }
        version++;
        JournalScreen.onRoute();
    }

    /** While the server calculates the route or its relief, asks for it again at an interval. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (route == null || !route.more()) {
            return;
        }
        pollTimer--;
        if (pollTimer <= 0) {
            refresh();
        }
    }

    /** Forgets the route. Called when the player leaves a world or a server. */
    public static void clear() {
        fromId = null;
        toId = null;
        route = null;
        shown = Optional.empty();
        shownDetailed = false;
        shownRelief = null;
        inWorld = false;
        pollTimer = 0;
        version++;
    }
}
