package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSize.Wall;
import java.util.List;
import org.junit.jupiter.api.Test;

class VillageSizeTableTest {
    private static final VillageSizeTable TABLE = VillageSizeTable.DEFAULT;

    /** The default table has a size for each tier of the default level table, and the start plot targets. */
    @Test
    void defaultTableHasTheStartPlotsOfEachTier() {
        TABLE.requireTiers(LevelTable.DEFAULT.tiers().stream().map(Tier::id).toList());
        List<Integer> plots = TABLE.sizes().stream().map(size -> size.plots(0, 0)).toList();
        assertEquals(List.of(14, 28, 42, 53, 66), plots);
        for (VillageSize size : TABLE.sizes()) {
            assertEquals(0.15, size.spareShare());
        }
        assertEquals(2, TABLE.size("hamlet").orElseThrow().sparePlots(14));
        assertEquals(9, TABLE.size("capital").orElseThrow().sparePlots(66));
    }

    /** The beds rule: the homes hold the population minus the hall places, then the spare plots come in addition. */
    @Test
    void bedsRuleGivesTheHomesAndTheSparePlots() {
        VillageSize size = new VillageSize("town", 0, 3, 0.2, 96, 1, Wall.PALISADE, 22);
        // 64 inhabitants, 4 places in the hall: 20 homes of 3 beds, and 4 spare plots.
        assertEquals(24, size.plots(64, 4));
        // A hall with more places than the population: 1 home at least.
        assertEquals(2, size.plots(3, 4));
    }

    @Test
    void badValuesGiveAClearError() {
        IllegalArgumentException spare = assertThrows(IllegalArgumentException.class,
                () -> new VillageSize("town", 42, 0, 0.3, 96, 1, Wall.PALISADE, 22));
        assertTrue(spare.getMessage().contains("tier town") && spare.getMessage().contains("spare share"),
                spare.getMessage());
        IllegalArgumentException rules = assertThrows(IllegalArgumentException.class,
                () -> new VillageSize("city", 53, 4, 0.15, 128, 2, Wall.PALISADE, 27));
        assertTrue(rules.getMessage().contains("tier city") && rules.getMessage().contains("not the two"),
                rules.getMessage());
        IllegalArgumentException radius = assertThrows(IllegalArgumentException.class,
                () -> new VillageSize("hamlet", 14, 0, 0.15, 200, 0, Wall.PALISADE, 9));
        assertTrue(radius.getMessage().contains("outside its min territory radius"), radius.getMessage());
        VillageSize hamlet = TABLE.size("hamlet").orElseThrow();
        IllegalArgumentException twice = assertThrows(IllegalArgumentException.class,
                () -> new VillageSizeTable(List.of(hamlet, hamlet)));
        assertTrue(twice.getMessage().contains("hamlet twice"), twice.getMessage());
        IllegalArgumentException missing = assertThrows(IllegalArgumentException.class,
                () -> new VillageSizeTable(List.of(hamlet)).requireTiers(List.of("hamlet", "village")));
        assertTrue(missing.getMessage().contains("[village]"), missing.getMessage());
    }
}
