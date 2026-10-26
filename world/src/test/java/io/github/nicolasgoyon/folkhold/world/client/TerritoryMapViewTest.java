package io.github.nicolasgoyon.folkhold.world.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerritoryMapViewTest {
    /** A discovered village at the block 8 8 (chunk 0 0), with a claim of 3 chunks. */
    private static final TerritoryMapPayload.Village NORTHAM =
            new TerritoryMapPayload.Village(new Claimant("northam", 8, 8, 2, 3), 70, "Northam", "Ash", 0xAA5500);
    /** A settlement that the player did not discover, 4 chunks to the east of Northam. */
    private static final Claimant HIDDEN = new Claimant("hidden", 8 + 4 * 16, 8, 2, 3);

    private static TerritoryMapView view() {
        return new TerritoryMapView(new TerritoryMapPayload(List.of(NORTHAM), List.of(HIDDEN)), 1);
    }

    /**
     * The chunks of a discovered village show its territory. A chunk of the other settlement has no village: the maps
     * do not show it. The nearest settlement owns a chunk that the two settlements claim.
     */
    @Test
    void discoveredVillageOwnsItsChunks() {
        TerritoryMapView view = view();

        assertEquals(Optional.of(NORTHAM), view.villageAt(0, 0));
        assertEquals(Optional.of(NORTHAM), view.villageAt(1, 0));
        assertEquals(Optional.of("hidden"), view.ownerOf(3, 0));
        assertEquals(Optional.empty(), view.villageAt(3, 0));
        assertEquals(Optional.empty(), view.ownerOf(0, 10));
    }

    /**
     * A side of a chunk is a border when the next chunk has another owner, or no owner. The center of the chunk 2 is
     * nearer to Northam: the border is between the chunks 2 and 3.
     */
    @Test
    void bordersAreBetweenOwners() {
        TerritoryMapView view = view();

        assertEquals(Optional.of(NORTHAM), view.villageAt(2, 0));
        assertFalse(view.isBorder(1, 0, 1, 0));
        assertTrue(view.isBorder(2, 0, 1, 0), "Northam and the other settlement");
        assertTrue(view.isBorder(0, 3, 0, 1), "Northam and no owner");
    }

    /** A region of 32 × 32 chunks has a discovered territory when the box of a discovered claim touches it. */
    @Test
    void regionsWithTerritories() {
        TerritoryMapView view = view();

        assertTrue(view.hasTerritoryIn(0, 0));
        assertTrue(view.hasTerritoryIn(-1, -1), "The claim goes to the chunks -3 -3");
        assertFalse(view.hasTerritoryIn(1, 0));
        assertFalse(TerritoryMapView.EMPTY.hasTerritoryIn(0, 0));
    }
}
