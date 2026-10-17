package io.github.nicolasgoyon.folkhold.world.group;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import org.junit.jupiter.api.Test;

class CaravansTest {
    /** The number of guards comes from the level of the origin: 2, 3 from level 8, 4 from level 16. */
    @Test
    void guardsFollowTheLevel() {
        assertEquals(2, Caravans.guards(1));
        assertEquals(2, Caravans.guards(7));
        assertEquals(3, Caravans.guards(8));
        assertEquals(4, Caravans.guards(16));
        assertEquals(4, Caravans.guards(20));
    }

    /** The number of mules comes from the cargo: 1 to 3. */
    @Test
    void mulesFollowTheCargo() {
        assertEquals(1, Caravans.mules(Map.of("minecraft:bread", 64)));
        assertEquals(2, Caravans.mules(Map.of("minecraft:bread", 64 * 16)));
        assertEquals(3, Caravans.mules(Map.of("minecraft:bread", 64 * 100)));
    }
}
