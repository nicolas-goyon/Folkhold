package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import org.junit.jupiter.api.Test;

class VillagePlacesTest {
    private static final int STAFF = 3;

    /** Each bed gives a place, and the hall gives a place to each member of the staff. */
    @Test
    void bedsAndStaffGiveThePlaces() {
        assertEquals(7, VillagePlaces.places(4, STAFF));
        assertEquals(STAFF, VillagePlaces.places(0, STAFF));
        assertEquals(VillagePlaces.UNKNOWN, VillagePlaces.places(VillagePlaces.UNKNOWN, STAFF));
    }

    /** The places are the maximum of the inhabitants. A village with more places keeps the target. */
    @Test
    void placesLimitTheInhabitants() {
        assertEquals(7, VillagePlaces.inhabitants(20, 4, STAFF));
        assertEquals(5, VillagePlaces.inhabitants(5, 4, STAFF));
        assertEquals(0, VillagePlaces.inhabitants(0, 4, STAFF));
    }

    /** A village with no bed keeps its staff. */
    @Test
    void villageWithNoBedKeepsItsStaff() {
        assertEquals(STAFF, VillagePlaces.inhabitants(20, 0, STAFF));
    }

    /** Before the first count of the beds, the places give no limit. */
    @Test
    void unknownBedsGiveNoLimit() {
        assertEquals(20, VillagePlaces.inhabitants(20, VillagePlaces.UNKNOWN, STAFF));
    }

    /** A settlement starts with an unknown number of beds, and tells when the number changes. */
    @Test
    void settlementKeepsItsBeds() {
        Settlement settlement = new Settlement("id", 0, 0, "faction", "name", 0, 0);

        assertEquals(VillagePlaces.UNKNOWN, settlement.beds());
        assertTrue(settlement.setBeds(4));
        assertFalse(settlement.setBeds(4));
        assertEquals(4, settlement.beds());
    }
}
