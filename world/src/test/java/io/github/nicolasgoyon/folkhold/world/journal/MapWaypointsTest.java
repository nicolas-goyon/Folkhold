package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import org.junit.jupiter.api.Test;

class MapWaypointsTest {
    private static final MapWaypoints.Waypoint ASHFELL = new MapWaypoints.Waypoint(88, 184, "Ashfell");

    /** A first use adds the waypoint to the map, and a second use removes it. The other maps do not change. */
    @Test
    void useTogglesTheWaypoint() {
        MapWaypoints waypoints = new MapWaypoints();
        assertTrue(waypoints.toggle(3, "ashfell", ASHFELL));
        assertTrue(waypoints.toggle(4, "ashfell", ASHFELL));
        assertEquals(Map.of("ashfell", ASHFELL), waypoints.waypoints(3));

        assertFalse(waypoints.toggle(3, "ashfell", ASHFELL));
        assertTrue(waypoints.waypoints(3).isEmpty());
        assertEquals(Map.of("ashfell", ASHFELL), waypoints.waypoints(4));
    }

    /**
     * A heart in the outer row of blocks of a map gets its decoration on the row next to it. The game shows this
     * position. The other positions do not change.
     */
    @Test
    void edgeOfTheMapMovesToTheNextRow() {
        assertEquals(63.0, MapWaypoints.onMap(63.5, 0, 0));
        assertEquals(-63.0, MapWaypoints.onMap(-63.5, 0, 0));
        assertEquals(1024.0 + 252.0, MapWaypoints.onMap(1024.0 + 254.5, 1024, 2));
        assertEquals(10.5, MapWaypoints.onMap(10.5, 0, 0));
        assertEquals(200.5, MapWaypoints.onMap(200.5, 0, 0));
    }
}
