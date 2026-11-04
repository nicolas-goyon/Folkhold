package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import org.junit.jupiter.api.Test;

class TerritoryMapsTest {
    private static final Claimant NORTHAM = new Claimant("northam", 0, 0, 2, 3);

    /** Two claims touch when their discs can have a chunk in common, with a margin of one chunk. */
    @Test
    void claimsTouchNearEachOther() {
        assertTrue(TerritoryMaps.touches(NORTHAM, new Claimant("near", 16 * 7, 0, 1, 3)));
        assertTrue(TerritoryMaps.touches(NORTHAM, new Claimant("diagonal", 80, 80, 1, 3)));
        assertFalse(TerritoryMaps.touches(NORTHAM, new Claimant("far", 16 * 9, 0, 1, 3)));
    }
}
