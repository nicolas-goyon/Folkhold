package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings.Requirement;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VillageBuildingsTest {
    private static final List<Requirement> REQUIREMENTS = List.of(new Requirement("storehouse", 2),
            new Requirement("forge", 5), new Requirement("barracks", 9), new Requirement("storehouse", 9));

    /** A requirement of a higher level does not apply. */
    @Test
    void lowLevelNeedsNothing() {
        assertEquals(Optional.empty(), VillageBuildings.missing(REQUIREMENTS, 1, Map.of()));
        assertEquals(Optional.of("storehouse"), VillageBuildings.missing(REQUIREMENTS, 2, Map.of()));
    }

    /** The first requirement with a missing building comes first. */
    @Test
    void firstMissingBuildingComesFirst() {
        assertEquals(Optional.of("forge"), VillageBuildings.missing(REQUIREMENTS, 9, Map.of("storehouse", 2)));
        assertEquals(Optional.of("storehouse"),
                VillageBuildings.missing(REQUIREMENTS, 9, Map.of("forge", 1, "barracks", 1)));
    }

    /** A kind in two requirements needs two buildings. */
    @Test
    void repeatedKindNeedsMoreBuildings() {
        assertEquals(Optional.of("storehouse"), VillageBuildings.missing(REQUIREMENTS, 9,
                Map.of("storehouse", 1, "forge", 1, "barracks", 1)));
        assertEquals(Optional.empty(), VillageBuildings.missing(REQUIREMENTS, 9,
                Map.of("storehouse", 2, "forge", 1, "barracks", 1)));
    }

    /** More buildings than the requirements is not a problem. */
    @Test
    void extraBuildingsAreAccepted() {
        assertEquals(Optional.empty(), VillageBuildings.missing(REQUIREMENTS, 5, Map.of("storehouse", 3, "forge", 2)));
    }

    @Test
    void minimumLevelIsOneOrMore() {
        assertThrows(IllegalArgumentException.class, () -> new Requirement("forge", 0));
    }
}
