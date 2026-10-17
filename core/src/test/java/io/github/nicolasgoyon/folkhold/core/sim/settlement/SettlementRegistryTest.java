package io.github.nicolasgoyon.folkhold.core.sim.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SettlementRegistryTest {

    private static Settlement settlement(String id, int chunkX, int chunkZ) {
        return new Settlement(id, chunkX, chunkZ, "faction", "Name", chunkX * 16 + 8, chunkZ * 16 + 8);
    }

    @Test
    void findsBySettlementIdAndByStartChunk() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement a = settlement("a", 3, -7);
        registry.add(a);

        assertSame(a, registry.byId("a").orElseThrow());
        assertEquals(List.of(a), registry.byStart(3, -7));
        assertTrue(registry.byStart(-7, 3).isEmpty());
        assertEquals(1, registry.size());
    }

    @Test
    void chunkKeysOfNegativeChunksDoNotCollide() {
        assertNotEquals(SettlementRegistry.chunkKey(-1, 0), SettlementRegistry.chunkKey(0, -1));
        assertNotEquals(SettlementRegistry.chunkKey(-1, -1), SettlementRegistry.chunkKey(1, 1));
        assertNotEquals(SettlementRegistry.chunkKey(0, -1), SettlementRegistry.chunkKey(-1, -1));
    }

    @Test
    void duplicateIdIsRejected() {
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(settlement("a", 0, 0));
        assertThrows(IllegalArgumentException.class, () -> registry.add(settlement("a", 5, 5)));
    }

    @Test
    void sameStartChunkKeepsAllSettlementsInOrder() {
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(settlement("a", 0, 0));
        registry.add(settlement("b", 0, 0));

        assertEquals(List.of("a", "b"), registry.byStart(0, 0).stream().map(Settlement::id).toList());
        assertEquals(2, registry.size());
    }

    @Test
    void withinGivesTheNearestFirst() {
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(settlement("far", 10, 0));
        registry.add(settlement("near", 1, 0));
        registry.add(settlement("out", 100, 0));

        List<Settlement> found = registry.within(0, 0, 200);

        assertEquals(List.of("near", "far"), found.stream().map(Settlement::id).toList());
    }

    @Test
    void changeIdKeepsTheSettlementAndItsStartChunk() {
        SettlementRegistry registry = new SettlementRegistry();
        Settlement a = settlement("a", 2, 2);
        registry.add(settlement("first", 0, 0));
        registry.add(a);

        registry.changeId("a", "b");

        assertEquals("b", a.id());
        assertTrue(registry.byId("a").isEmpty());
        assertSame(a, registry.byId("b").orElseThrow());
        assertEquals(List.of(a), registry.byStart(2, 2));
        assertEquals(List.of("first", "b"), registry.all().stream().map(Settlement::id).toList());
    }

    @Test
    void changeIdToAnExistingIdIsRejected() {
        SettlementRegistry registry = new SettlementRegistry();
        registry.add(settlement("a", 0, 0));
        registry.add(settlement("b", 1, 1));
        assertThrows(IllegalArgumentException.class, () -> registry.changeId("a", "b"));
        assertThrows(IllegalArgumentException.class, () -> registry.changeId("missing", "c"));
    }

    @Test
    void heartPositionReplacesTheApproximatePosition() {
        Settlement a = settlement("a", 0, 0);
        assertFalse(a.hasHeart());

        a.setHeart(20, 64, -3);

        assertTrue(a.hasHeart());
        assertEquals(20, a.x());
        assertEquals(64, a.heartY());
        assertEquals(-3, a.z());
    }

    @Test
    void stateHasAStableSavedName() {
        assertEquals("ruins", SettlementState.RUINS.serializedName());
        assertEquals(Optional.of(SettlementState.RUINS), SettlementState.fromSerializedName("ruins"));
        assertEquals(Optional.empty(), SettlementState.fromSerializedName("unknown"));
    }
}
