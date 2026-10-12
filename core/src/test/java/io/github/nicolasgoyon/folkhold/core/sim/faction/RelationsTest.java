package io.github.nicolasgoyon.folkhold.core.sim.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RelationsTest {

    private static FactionSpec faction(String id, Attitude attitude) {
        return new FactionSpec(id, "c", id, 0, attitude, Set.of(FactionTrait.MERCHANT), 0, 0);
    }

    @Test
    void initialValuesFollowTheAttitudes() {
        Relations relations = Relations.initial(List.of(
                faction("a1", Attitude.ALLY), faction("a2", Attitude.ALLY),
                faction("n", Attitude.NEUTRAL), faction("h1", Attitude.HOSTILE), faction("h2", Attitude.HOSTILE)));
        assertEquals(60, relations.get("a1", "a2"));
        assertEquals(20, relations.get("h1", "h2"));
        assertEquals(-40, relations.get("a1", "h1"));
        assertEquals(0, relations.get("n", "h2"));
        assertEquals(RelationState.ALLIANCE, relations.state("a2", "a1"));
        assertEquals(RelationState.TENSION, relations.state("h2", "a2"));
    }

    @Test
    void valuesAreSymmetricAndClamped() {
        Relations relations = new Relations();
        relations.set("x", "y", 30);
        assertEquals(30, relations.get("y", "x"));
        relations.change("y", "x", 500);
        assertEquals(Relations.MAX, relations.get("x", "y"));
        relations.change("x", "y", -1000);
        assertEquals(Relations.MIN, relations.get("x", "y"));
        assertEquals(Relations.MAX, relations.get("x", "x"));
    }

    @Test
    void statesHaveTheDocumentedThresholds() {
        assertEquals(RelationState.ALLIANCE, RelationState.of(50));
        assertEquals(RelationState.PEACE, RelationState.of(0));
        assertEquals(RelationState.TENSION, RelationState.of(-50));
        assertEquals(RelationState.WAR, RelationState.of(-51));
    }
}
