package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings.Requirement;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings.Step;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageBuildings.StepType;
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

    private static final List<Requirement> WITH_MARKET = List.of(new Requirement("storehouse", 3),
            new Requirement("barracks", 9), new Requirement(VillageBuildings.MARKET, 5));
    private static final Step MARKET = new Step(StepType.MARKET, VillageBuildings.MARKET);
    private static final Step HOME = new Step(StepType.HOME, "");

    /** From the level 5, the market comes before the home and before the other buildings of the level. */
    @Test
    void marketComesFirst() {
        assertEquals(List.of(MARKET, HOME), VillageBuildings.steps(WITH_MARKET, 5, Map.of(), true, true));
        assertEquals(List.of(MARKET, new Step(StepType.BUILDING, "storehouse")),
                VillageBuildings.steps(WITH_MARKET, 5, Map.of(), true, false));
    }

    /** A hamlet (below the level 5) needs no market: it trades at the plaza. */
    @Test
    void hamletNeedsNoMarket() {
        assertEquals(List.of(HOME), VillageBuildings.steps(WITH_MARKET, 4, Map.of(), true, true));
        assertFalse(VillageBuildings.needsMarket(WITH_MARKET, 4, Map.of()));
        assertTrue(VillageBuildings.needsMarket(WITH_MARKET, 5, Map.of()));
    }

    /** A village keeps one market: with a market, the next step is the home or the next building. */
    @Test
    void villageKeepsOneMarket() {
        Map<String, Integer> counts = Map.of(VillageBuildings.MARKET, 1, "storehouse", 1);
        assertEquals(List.of(HOME), VillageBuildings.steps(WITH_MARKET, 17, counts, true, true));
        assertEquals(List.of(new Step(StepType.BUILDING, "barracks")),
                VillageBuildings.steps(WITH_MARKET, 17, counts, true, false));
        assertFalse(VillageBuildings.needsMarket(WITH_MARKET, 17, counts));
    }

    /** A village with no scan has no counts: it builds only homes. */
    @Test
    void noScanGivesOnlyTheHome() {
        assertEquals(List.of(HOME), VillageBuildings.steps(WITH_MARKET, 9, Map.of(), false, true));
        assertEquals(List.of(), VillageBuildings.steps(WITH_MARKET, 9, Map.of(), false, false));
    }

    @Test
    void minimumLevelIsOneOrMore() {
        assertThrows(IllegalArgumentException.class, () -> new Requirement("forge", 0));
    }
}
