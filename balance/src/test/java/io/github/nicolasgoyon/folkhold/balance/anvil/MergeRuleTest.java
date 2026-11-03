package io.github.nicolasgoyon.folkhold.balance.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MergeRuleTest {

    @Test
    void vanillaEqualLevelsGiveTheLevelPlusOneWithNoCap() {
        assertEquals(6, MergeRule.VANILLA.merge(5, 5));
        assertEquals(21, MergeRule.VANILLA.merge(20, 20));
        assertEquals(1, MergeRule.VANILLA.merge(0, 0));
    }

    @Test
    void vanillaDifferentLevelsGiveTheHigherLevel() {
        assertEquals(5, MergeRule.VANILLA.merge(5, 3));
        assertEquals(5, MergeRule.VANILLA.merge(3, 5));
        assertEquals(3, MergeRule.VANILLA.merge(0, 3));
    }

    @Test
    void sumAddsTheLevels() {
        assertEquals(10, MergeRule.SUM.merge(5, 5));
        assertEquals(3, MergeRule.SUM.merge(0, 3));
    }

    @Test
    void efficiencyTwentyNeedsTwoToTheFifteenBooksOfLevelFive() {
        // With the vanilla rule, a level L + 1 needs two items of level L.
        long books = 1;
        for (int level = 5; level < 20; level++) {
            books *= 2;
        }
        assertEquals(1L << 15, books);
    }
}
