package io.github.nicolasgoyon.folkhold.world.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.cartofold.api.client.MapFrame;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.JournalPayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.RouteRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import java.util.Optional;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;
import org.lwjgl.glfw.GLFW;

class JournalMapViewsTest {
    private static final MapFrame.View VIEW = new MapFrame.View(100, 200, 2);

    private static JournalPayload.Village village(String id, int x, int z) {
        return new JournalPayload.Village(id, id, "ash", Component.literal("Ash"), 1, Component.literal("Hamlet"),
                true, x, z, 0, ReputationTier.NEUTRAL, 0, 0, 5, 2);
    }

    /** The arrow keys move the view by the same distance on the screen at each scale. */
    @Test
    void arrowKeysMoveTheView() {
        assertEquals(new MapFrame.View(84, 200, 2), JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_LEFT).orElseThrow());
        assertEquals(new MapFrame.View(116, 200, 2), JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_RIGHT).orElseThrow());
        assertEquals(new MapFrame.View(100, 184, 2), JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_UP).orElseThrow());
        assertEquals(new MapFrame.View(100, 216, 2), JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_DOWN).orElseThrow());
        assertEquals(Optional.empty(), JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_A));
    }

    /** The keys + and - zoom around the center, between the limits of the frames. */
    @Test
    void plusAndMinusZoom() {
        MapFrame.View in = JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_EQUAL).orElseThrow();
        assertEquals(2 * Math.sqrt(2), in.scale(), 1e-9);
        assertEquals(100, in.centerX());
        assertEquals(2 / Math.sqrt(2), JournalMapViews.onKey(VIEW, GLFW.GLFW_KEY_KP_SUBTRACT).orElseThrow().scale(),
                1e-9);
        assertEquals(JournalMapViews.MAX_SCALE, JournalMapViews.zoom(VIEW, 20).scale());
        assertEquals(JournalMapViews.MIN_SCALE, JournalMapViews.zoom(VIEW, -20).scale());
    }

    /** "Show all" covers the discs of the territories and the villages with no territory. */
    @Test
    void territoryBoxHasTheTerritoriesAndTheVillages() {
        TerritoryMapPayload.Village territory =
                new TerritoryMapPayload.Village(new Claimant("north", 0, 0, 1, 2), 64, "North", "Ash", 0xAA5500);

        double[] box = JournalMapViews.territoryBox(List.of(territory), List.of(village("far", 500, -10)))
                .orElseThrow();

        assertArrayEquals(new double[] {-48, -48, 501, 48}, box);
        assertTrue(JournalMapViews.territoryBox(List.of(), List.of()).isEmpty());
    }

    /** The box of a route has the two villages and the points of the route, and a side of 64 blocks or more. */
    @Test
    void routeBoxHasTheRoute() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(50, 300)),
                List.of(Route.Surface.LAND));

        double[] box = JournalMapViews.routeBox(village("a", 0, 0), village("b", 10, 200), Optional.of(route), null);

        assertArrayEquals(new double[] {-7, 0, 57, 300}, box);
    }

    /** With no complete pair of villages, the journal asks the server to remove the route from the maps. */
    @Test
    void incompletePairClearsTheRoute() {
        assertSame(ClientRouteView.CLEAR, ClientRouteView.request(null, null, false));
        assertSame(ClientRouteView.CLEAR, ClientRouteView.request("a", null, false));
        assertSame(ClientRouteView.CLEAR, ClientRouteView.request("a", "a", true));
        assertEquals(new RouteRequestPayload("", "", false), ClientRouteView.CLEAR);
        assertEquals(new RouteRequestPayload("a", "b", true), ClientRouteView.request("a", "b", true));
    }

    /**
     * A new answer with only a new progress keeps the state: the tab "Routes" does not build its widgets again, and a
     * drag of the frame continues. A new route or the end of the calculation changes the state.
     */
    @Test
    void progressKeepsTheRouteState() {
        Route route = new Route(List.of(new Route.Point(0, 0), new Route.Point(50, 300)),
                List.of(Route.Surface.LAND));
        RoutePayload pending = RoutePayload.empty("a", "b", RoutePayload.Status.PENDING);
        JournalMapViews.RouteState first = JournalMapViews.routeState("a", "b",
                pending.with(new RoutePayload.Progress(true, 0, 10, 100, 10), null, true), Optional.empty(), null);
        JournalMapViews.RouteState next = JournalMapViews.routeState("a", "b",
                pending.with(new RoutePayload.Progress(true, 0, 50, 100, 50), null, true), Optional.empty(), null);
        assertEquals(first, next);

        RoutePayload ready = RoutePayload.of("a", "b", RoutePayload.Status.READY, true, route);
        JournalMapViews.RouteState done = JournalMapViews.routeState("a", "b", ready, Optional.of(route), null);
        assertNotEquals(next, done);

        Route other = new Route(List.of(new Route.Point(0, 0), new Route.Point(60, 300)),
                List.of(Route.Surface.ROAD));
        JournalMapViews.RouteState busy = JournalMapViews.routeState("a", "b", ready.with(null, null, true),
                Optional.of(route), null);
        assertEquals(done.fit(), busy.fit(), "The end of a calculation keeps the view");
        assertNotEquals(done.fit(), JournalMapViews.routeState("a", "b", ready, Optional.of(other), null).fit(),
                "A new route gives a new view");
    }
}
