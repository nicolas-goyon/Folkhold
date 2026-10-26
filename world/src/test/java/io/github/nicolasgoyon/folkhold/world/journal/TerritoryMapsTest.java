package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
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

    /** The map has the settlements near the discovered villages, and not the discovered villages or far ones. */
    @Test
    void neighborsOfTheDiscoveredVillages() {
        Claimant near = new Claimant("near", 100, 0, 1, 3);
        Claimant far = new Claimant("far", 2000, 0, 1, 3);
        List<TerritoryMapPayload.Village> villages =
                List.of(new TerritoryMapPayload.Village(NORTHAM, 64, "Northam", "Ash", 0xAA5500));

        assertEquals(List.of(near), TerritoryMaps.neighbors(List.of(NORTHAM, near, far), villages));
        assertEquals(List.of(), TerritoryMaps.neighbors(List.of(NORTHAM, near, far), List.of()));
    }
}
