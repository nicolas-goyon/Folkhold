package io.github.nicolasgoyon.folkhold.balance.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class KeyedValuesTest {

    @Test
    void idsAndTagsAreSeparate() {
        KeyedValues values = KeyedValues.parse(List.of("minecraft:diamond=4", "#minecraft:planks=1"));
        assertEquals(Map.of("minecraft:diamond", 4), values.ids());
        assertEquals(Map.of("minecraft:planks", 1), values.tags());
    }

    @Test
    void idWithNoNamespaceIsInMinecraft() {
        assertEquals(Map.of("minecraft:fortune", 5), KeyedValues.parse(List.of("fortune=5")).ids());
    }

    @Test
    void lastEntryForAKeyWins() {
        assertEquals(Map.of("minecraft:fortune", 3),
                KeyedValues.parse(List.of("minecraft:fortune=5", "minecraft:fortune=3")).ids());
    }

    @Test
    void tagsKeepTheOrderOfTheEntries() {
        KeyedValues values = KeyedValues.parse(List.of("#b:x=1", "#a:y=2", "#c:z=3"));
        assertEquals(List.of("b:x", "a:y", "c:z"), List.copyOf(values.tags().keySet()));
    }

    @Test
    void invalidEntriesAreRefused() {
        assertTrue(KeyedValues.isValidEntry("minecraft:efficiency=1"));
        assertTrue(KeyedValues.isValidEntry("#minecraft:planks=0"));
        assertFalse(KeyedValues.isValidEntry("minecraft:efficiency"));
        assertFalse(KeyedValues.isValidEntry("minecraft:efficiency=-1"));
        assertFalse(KeyedValues.isValidEntry("minecraft:efficiency=1.5"));
        assertFalse(KeyedValues.isValidEntry("Minecraft:Efficiency=1"));
        assertFalse(KeyedValues.isValidEntry("=1"));
        assertFalse(KeyedValues.isValidEntry("minecraft:efficiency=1234567890"));
        assertFalse(KeyedValues.isValidEntry(3));
        assertTrue(KeyedValues.parse(List.of("bad", 3, "x=")).ids().isEmpty());
    }
}
