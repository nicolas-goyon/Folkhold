package io.github.nicolasgoyon.folkhold.core.sim.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable.StartChance;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class LevelTableTest {
    private static final GarrisonTraits GARRISON = new GarrisonTraits(Map.of("guard", 1), "hamlet");

    private static final LevelTable TABLE = LevelTable.DEFAULT;

    @Test
    void defaultTableHasTwentyLevelsAndFiveTiers() {
        assertEquals(20, TABLE.maxLevel());
        assertEquals(List.of("hamlet", "village", "town", "city", "capital"),
                TABLE.tiers().stream().map(Tier::id).toList());
        assertEquals("hamlet", TABLE.tier(4).id());
        assertEquals("village", TABLE.tier(5).id());
        assertEquals("city", TABLE.tier(16).id());
        assertEquals("capital", TABLE.tier(17).id());
    }

    /** The garrison grows with the tier, and its equipment set has the id of the tier. */
    @Test
    void defaultGarrisonGrowsWithTheTier() {
        GarrisonTraits hamlet = TABLE.traits(1).garrison();
        GarrisonTraits capital = TABLE.traits(20).garrison();

        assertEquals(2, hamlet.count("guard"));
        assertEquals(2, hamlet.count("archer"));
        assertEquals(1, hamlet.count("captain"));
        assertEquals(0, hamlet.count("healer"));
        assertEquals("hamlet", hamlet.equipment());
        assertEquals(1, TABLE.traits(5).garrison().count("healer"));
        assertEquals(2, TABLE.traits(17).garrison().count("healer"));
        assertEquals(0, hamlet.count("golem"));
        assertEquals(0, TABLE.traits(12).garrison().count("golem"));
        assertEquals(1, TABLE.traits(13).garrison().count("golem"));
        assertEquals(2, capital.count("golem"));
        assertEquals(6, capital.count("guard"));
        assertEquals(5, capital.count("archer"));
        assertEquals("capital", capital.equipment());
        assertEquals(0, capital.count("unknown"));
        for (int level = 1; level <= TABLE.maxLevel(); level++) {
            assertEquals(TABLE.tier(level).id(), TABLE.traits(level).garrison().equipment(), "Level " + level);
        }
    }

    @Test
    void levelsOutsideTheTableUseTheNearestLevel() {
        assertEquals("hamlet", TABLE.tier(0).id());
        assertEquals("capital", TABLE.tier(99).id());
        assertEquals(TABLE.traits(20), TABLE.traits(25));
    }

    @Test
    void claimRadiusGrowsFromNineChunksForAHamletToThirtyThreeForACapital() {
        assertEquals(9, TABLE.traits(1).claimRadius());
        assertEquals(27, TABLE.traits(13).claimRadius());
        assertEquals(33, TABLE.traits(20).claimRadius());
        for (int level = 2; level <= TABLE.maxLevel(); level++) {
            assertTrue(TABLE.traits(level).claimRadius() >= TABLE.traits(level - 1).claimRadius());
            assertTrue(TABLE.traits(level).experience() > TABLE.traits(level - 1).experience());
        }
    }

    @Test
    void startLevelIsStableForASeedAndNeverACapital() {
        Map<Integer, Integer> counts = new HashMap<>();
        for (long seed = 0; seed < 20_000; seed++) {
            int level = TABLE.startLevel(seed);
            assertTrue(level >= 1 && level <= 16, "Start level out of range: " + level);
            counts.merge(level, 1, Integer::sum);
        }
        assertEquals(TABLE.startLevel(42), TABLE.startLevel(42));
        // 40 % at level 1, with a margin.
        int levelOne = counts.getOrDefault(1, 0);
        assertTrue(levelOne > 7_400 && levelOne < 8_600, "Level 1 count: " + levelOne);
        int cities = 0;
        for (int level = 13; level <= 16; level++) {
            cities += counts.getOrDefault(level, 0);
        }
        assertTrue(cities > 250 && cities < 550, "City count: " + cities);
    }

    @Test
    void tiersWithAGapAreRejected() {
        List<LevelTraits> levels = List.of(new LevelTraits(10, 1, 1, 1, 1, 1, 1, GARRISON), new LevelTraits(10, 1, 1, 1, 1, 1, 1, GARRISON));
        List<StartChance> chances = List.of(new StartChance(1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelTable(levels, List.of(new Tier("a", 1, 1), new Tier("b", 3, 3)), chances));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelTable(levels, List.of(new Tier("a", 1, 1)), chances));
        assertThrows(IllegalArgumentException.class,
                () -> new LevelTable(levels, List.of(new Tier("a", 1, 2)), List.of(new StartChance(1, 3, 1))));
    }
}
