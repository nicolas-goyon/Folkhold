package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import org.junit.jupiter.api.Test;

class MapTerritoriesTest {
    /** A claim touches the area of a map when its disc, with a margin of one chunk, can reach the area. */
    @Test
    void claimTouchesTheArea() {
        MapTerritories.Area area = new MapTerritories.Area(0, 0, 127, 127);
        assertTrue(area.touches(new Claimant("in", 64, 64, 1, 4)));
        // The disc of radius 4 chunks reaches 80 blocks, with the margin: from 200 to 120.
        assertTrue(area.touches(new Claimant("near", 200, 64, 1, 4)));
        assertFalse(area.touches(new Claimant("far", 220, 64, 1, 4)));
        assertFalse(area.touches(new Claimant("diagonal", -300, -300, 1, 4)));
    }
}
