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
}
