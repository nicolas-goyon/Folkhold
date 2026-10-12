package io.github.nicolasgoyon.folkhold.core.sim.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class VillageNamesTest {
    private static final CultureInfo CULTURE =
            new CultureInfo("a", 0xFF0000, List.of("val", "mor", "ash"), List.of("heim", "dor", "wick"));

    @Test
    void sameSeedGivesSameName() {
        assertEquals(VillageNames.name(CULTURE, 7L), VillageNames.name(CULTURE, 7L));
    }

    @Test
    void nameIsPrefixAndSuffixWithCapital() {
        for (long seed = 0; seed < 50; seed++) {
            String name = VillageNames.name(CULTURE, seed);
            assertTrue(Character.isUpperCase(name.charAt(0)), name);
            String lower = name.toLowerCase();
            assertTrue(CULTURE.prefixes().stream().anyMatch(lower::startsWith), name);
            assertTrue(CULTURE.suffixes().stream().anyMatch(lower::endsWith), name);
        }
    }

    @Test
    void differentSeedsGiveDifferentNames() {
        Set<String> names = new HashSet<>();
        for (long seed = 0; seed < 50; seed++) {
            names.add(VillageNames.name(CULTURE, seed));
        }
        assertTrue(names.size() > 3, "names: " + names);
    }
}
