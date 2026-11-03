package io.github.nicolasgoyon.folkhold.balance.enchantment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class EnchantmentFilterTest {

    @Test
    void defaultListBlocksMendingOnly() {
        EnchantmentFilter filter = EnchantmentFilter.parse(BlockedEnchantments.DEFAULT);
        assertEquals(Set.of("minecraft:mending"), filter.ids());
        assertTrue(filter.tags().isEmpty());
    }

    @Test
    void entryWithHashIsATag() {
        EnchantmentFilter filter = EnchantmentFilter.parse(List.of("#minecraft:treasure", "mymod:soulbound"));
        assertEquals(Set.of("minecraft:treasure"), filter.tags());
        assertEquals(Set.of("mymod:soulbound"), filter.ids());
    }

    @Test
    void idWithNoNamespaceIsInMinecraft() {
        EnchantmentFilter filter = EnchantmentFilter.parse(List.of("mending"));
        assertEquals(Set.of("minecraft:mending"), filter.ids());
    }

    @Test
    void invalidEntriesAreRefused() {
        assertTrue(EnchantmentFilter.isValidEntry("minecraft:mending"));
        assertTrue(EnchantmentFilter.isValidEntry("#c:overpowered"));
        assertFalse(EnchantmentFilter.isValidEntry(""));
        assertFalse(EnchantmentFilter.isValidEntry("#"));
        assertFalse(EnchantmentFilter.isValidEntry("Minecraft:Mending"));
        assertFalse(EnchantmentFilter.isValidEntry("minecraft:mending:1"));
        assertFalse(EnchantmentFilter.isValidEntry(42));
        assertFalse(EnchantmentFilter.isValidEntry(null));
    }

    @Test
    void parseIgnoresInvalidEntries() {
        EnchantmentFilter filter = EnchantmentFilter.parse(List.of("Bad Entry", "minecraft:mending", ""));
        assertEquals(Set.of("minecraft:mending"), filter.ids());
    }

    @Test
    void emptyListGivesAnEmptyFilter() {
        assertTrue(EnchantmentFilter.parse(List.of()).isEmpty());
        assertFalse(EnchantmentFilter.parse(BlockedEnchantments.DEFAULT).isEmpty());
    }
}
