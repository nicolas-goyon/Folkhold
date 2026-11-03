package io.github.nicolasgoyon.folkhold.balance.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class LootScopeTest {

    @Test
    void fishingTableAndItsSubTablesAreFishing() {
        assertEquals(LootScope.FISHING, LootScope.of("minecraft", "gameplay/fishing"));
        assertEquals(LootScope.FISHING,
                LootScope.of("minecraft", "gameplay/fishing/treasure"));
    }

    @Test
    void chestTablesOfAllNamespacesAreChests() {
        assertEquals(LootScope.CHEST, LootScope.of("minecraft", "chests/stronghold_library"));
        assertEquals(LootScope.CHEST, LootScope.of("mymod", "chests/tower"));
    }

    @Test
    void otherTablesAreOther() {
        assertEquals(LootScope.OTHER, LootScope.of("minecraft", "blocks/stone"));
        assertEquals(LootScope.OTHER, LootScope.of("minecraft", "gameplay/fishing_extra"));
        assertEquals(LootScope.OTHER, LootScope.of("mymod", "gameplay/fishing"));
        assertEquals(LootScope.OTHER, LootScope.of("minecraft", "entities/chests"));
    }
}
