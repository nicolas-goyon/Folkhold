package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.construction.WorkZone.Column;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class WorkZoneTest {
    /** A box of 3 × 3 × 3 blocks from 0 0 0, with a band of 2 blocks: the ring goes from -3 to 5. */
    private static final WorkZone ZONE = new WorkZone(0, 0, 0, 2, 2, 2, 2);

    /** The ring is the edge of the box with the band: each column once, in order around the zone. */
    @Test
    void ringIsTheEdgeOfTheBand() {
        List<Column> ring = ZONE.ring();

        assertEquals(32, ring.size(), "A ring of 9 × 9 columns has 32 columns");
        assertEquals(32, new HashSet<>(ring).size(), "Each column of the ring must be in the list once");
        for (Column column : ring) {
            boolean edge = column.x() == -3 || column.x() == 5 || column.z() == -3 || column.z() == 5;
            assertTrue(edge, "A column of the ring must be on the edge: " + column);
        }
        for (int i = 0; i < ring.size(); i++) {
            Column a = ring.get(i);
            Column b = ring.get((i + 1) % ring.size());
            assertEquals(1, Math.abs(a.x() - b.x()) + Math.abs(a.z() - b.z()),
                    "Two columns side by side in the list must touch: " + a + " and " + b);
        }
    }

    /** The opening is the column of the ring nearest to the village, and never a corner. */
    @Test
    void openingFacesTheVillage() {
        assertEquals(new Column(1, -3), ZONE.opening(1, -40), "A village to the north: an opening on the north side");
        assertEquals(new Column(5, 2), ZONE.opening(30, 2), "A village to the east: an opening on the east side");
        Column diagonal = ZONE.opening(40, 40);
        boolean corner = (diagonal.x() == -3 || diagonal.x() == 5) && (diagonal.z() == -3 || diagonal.z() == 5);
        assertFalse(corner, "A corner is never the opening: " + diagonal);
        assertTrue(diagonal.equals(new Column(4, 5)) || diagonal.equals(new Column(5, 4)),
                "A village at the south-east corner: an opening next to this corner, not " + diagonal);
    }

    /**
     * The fences go on all the columns of the ring except the opening. The work starts next to the opening and goes on
     * the two sides in turn, to the far side of the ring.
     */
    @Test
    void fencesStartAtTheOpeningOnTheTwoSides() {
        Column opening = ZONE.opening(1, -40);
        List<Column> fences = ZONE.fenceColumns(1, -40);

        assertEquals(31, fences.size(), "All the columns of the ring except the opening");
        assertFalse(fences.contains(opening), "The opening has no fence");
        Set<Column> unique = new HashSet<>(fences);
        assertEquals(31, unique.size(), "Each column gets one fence");
        assertEquals(new Column(2, -3), fences.get(0), "The first fence is next to the opening");
        assertEquals(new Column(0, -3), fences.get(1), "The second fence is on the other side of the opening");
        Column last = fences.get(fences.size() - 1);
        assertEquals(5, last.z(), "The last fence is on the far side of the ring: " + last);
    }

    /** The zone has the ring and the inside of the ring, with some layers below and above the box. */
    @Test
    void zoneHasTheRingAndSomeLayersAboveAndBelow() {
        assertTrue(ZONE.contains(1.5, 1.0, 1.5), "The middle of the box is in the zone");
        assertTrue(ZONE.contains(-2.5, 0.0, 4.9), "The band is in the zone");
        assertTrue(ZONE.contains(-3.0, 0.0, 5.99), "The ring is in the zone");
        assertFalse(ZONE.contains(-3.01, 0.0, 0.0), "Outside the ring is not in the zone");
        assertFalse(ZONE.contains(6.0, 0.0, 0.0), "Outside the ring is not in the zone");
        assertTrue(ZONE.contains(1.0, -WorkZone.BELOW, 1.0), "Some layers below the box are in the zone");
        assertFalse(ZONE.contains(1.0, -WorkZone.BELOW - 0.01, 1.0), "Lower is not in the zone");
        assertTrue(ZONE.contains(1.0, 2 + WorkZone.ABOVE + 0.99, 1.0), "Some layers above the box are in the zone");
        assertFalse(ZONE.contains(1.0, 2 + WorkZone.ABOVE + 1.0, 1.0), "Higher is not in the zone");
    }

    @Test
    void boxAndMarginMustBeValid() {
        assertThrows(IllegalArgumentException.class, () -> new WorkZone(2, 0, 0, 1, 0, 0, 2));
        assertThrows(IllegalArgumentException.class, () -> new WorkZone(0, 0, 0, 1, 1, 1, -1));
    }
}
