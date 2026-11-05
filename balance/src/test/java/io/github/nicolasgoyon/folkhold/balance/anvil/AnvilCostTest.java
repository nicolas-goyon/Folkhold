package io.github.nicolasgoyon.folkhold.balance.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The cost tables of the anvil, with the vanilla merge rule. Weights (vanilla anvil_cost): Efficiency, Sharpness and
 * Protection 1, Unbreaking 2, Fortune, Looting and Mending 4. Operation cost 1. The default factor k is 0.5; the
 * table of the owner (issue #600) uses k = 1. Repair: r = 1, bases iron 3, diamond 4.
 */
class AnvilCostTest {
    private static final int EFFICIENCY = 1;
    private static final int PROTECTION = 1;
    private static final int UNBREAKING = 2;
    private static final int FORTUNE = 4;
    private static final int MENDING = 4;
    private static final int IRON = 3;
    private static final int DIAMOND = 4;
    private static final double DEFAULT_K = 0.5;

    /** The cost of one enchantment operation that raises one enchantment. */
    private static long enchant(double factor, int weight, int from, int to) {
        return AnvilCost.enchantmentCost(1, factor, AnvilCost.levelIncrease(weight, from, to));
    }

    /** The cost of one operation with the default factor. */
    private static long enchant(int weight, int from, int to) {
        return enchant(DEFAULT_K, weight, from, to);
    }

    /** The cost of a sequence of operations of +1 level each. */
    private static long steps(double factor, int weight, int from, int to) {
        long cost = 0;
        for (int level = from; level < to; level++) {
            cost += enchant(factor, weight, level, level + 1);
        }
        return cost;
    }

    @Test
    void tableOfTheOwnerWithFactorOne() {
        assertEquals(33, enchant(1.0, EFFICIENCY, 0, 5));
        assertEquals(17, enchant(1.0, EFFICIENCY, 4, 5));
        assertEquals(129, enchant(1.0, EFFICIENCY, 0, 7));
        assertEquals(65, enchant(1.0, EFFICIENCY, 6, 7));
        assertEquals(257, enchant(1.0, EFFICIENCY, 0, 8));
        assertEquals(129, enchant(1.0, EFFICIENCY, 7, 8));
        assertEquals(1025, enchant(1.0, EFFICIENCY, 0, 10));
        assertEquals(513, enchant(1.0, EFFICIENCY, 9, 10));
        assertEquals(16385, enchant(1.0, EFFICIENCY, 14, 15));
        // 32,769 levels: above the limit.
        assertEquals(AnvilCost.MAX_COST + 1, enchant(1.0, EFFICIENCY, 0, 15));
    }

    @Test
    void vanillaMaxLevelsWithTheDefaultFactor() {
        // A book on an item with no enchantments.
        assertEquals(17, enchant(EFFICIENCY, 0, 5));
        assertEquals(9, enchant(PROTECTION, 0, 4));
        assertEquals(9, enchant(UNBREAKING, 0, 3));
        assertEquals(17, enchant(FORTUNE, 0, 3));
        assertEquals(5, enchant(MENDING, 0, 1));
        // Two books of the same level.
        assertEquals(9, enchant(EFFICIENCY, 4, 5));
        assertEquals(5, enchant(UNBREAKING, 2, 3));
        assertEquals(5, enchant(PROTECTION, 3, 4));
    }

    @Test
    void fullSetOfVanillaMaxLevelsInOneOperation() {
        // A book with Efficiency V, Unbreaking III, Fortune III and Mending on a pickaxe with no enchantments.
        double pickaxe = AnvilCost.levelIncrease(EFFICIENCY, 0, 5) + AnvilCost.levelIncrease(UNBREAKING, 0, 3)
                + AnvilCost.levelIncrease(FORTUNE, 0, 3) + AnvilCost.levelIncrease(MENDING, 0, 1);
        assertEquals(45, AnvilCost.enchantmentCost(1, DEFAULT_K, pickaxe));
        // Protection IV, Unbreaking III and Mending on a chestplate.
        double chestplate = AnvilCost.levelIncrease(PROTECTION, 0, 4) + AnvilCost.levelIncrease(UNBREAKING, 0, 3)
                + AnvilCost.levelIncrease(MENDING, 0, 1);
        assertEquals(21, AnvilCost.enchantmentCost(1, DEFAULT_K, chestplate));
    }

    @Test
    void levelsAboveTheVanillaMaxWithTheDefaultFactor() {
        assertEquals(6, MergeRule.VANILLA.merge(5, 5));
        assertEquals(17, enchant(EFFICIENCY, 5, 6));
        assertEquals(33, enchant(EFFICIENCY, 6, 7));
        assertEquals(65, enchant(EFFICIENCY, 7, 8));
        assertEquals(257, enchant(EFFICIENCY, 9, 10));
        assertEquals(10, MergeRule.VANILLA.merge(5, 10));
        assertEquals(497, enchant(EFFICIENCY, 5, 10));
        assertEquals(8193, enchant(EFFICIENCY, 14, 15));
        assertEquals(16385, enchant(EFFICIENCY, 0, 15));
        assertEquals(AnvilCost.MAX_COST + 1, enchant(EFFICIENCY, 0, 16));
        assertEquals(9, enchant(UNBREAKING, 3, 4));
        assertEquals(25, enchant(UNBREAKING, 3, 5));
    }

    @Test
    void efficiencyTwoToThreeAndTwoToFive() {
        // A book Efficiency III or V on a pickaxe with Efficiency II: the higher level wins.
        assertEquals(3, MergeRule.VANILLA.merge(2, 3));
        assertEquals(3, enchant(EFFICIENCY, 2, 3));
        assertEquals(15, enchant(EFFICIENCY, 2, 5));
    }

    @Test
    void pathIsNeverCheaperInSteps() {
        for (double factor : new double[] {1.0, 0.5, 0.3, 0.25}) {
            for (int weight = 1; weight <= 8; weight++) {
                for (int from = 0; from < 30; from++) {
                    for (int to = from + 1; to <= 40; to++) {
                        assertTrue(steps(factor, weight, from, to) >= enchant(factor, weight, from, to));
                    }
                }
            }
        }
        // With k = 1, only the fixed cost of each operation separates the two paths, below the limit.
        for (int weight = 1; weight <= 8; weight++) {
            for (int from = 0; from < 10; from++) {
                for (int to = from + 1; to <= 11; to++) {
                    assertEquals(enchant(1.0, weight, from, to) + (to - from - 1), steps(1.0, weight, from, to));
                }
            }
        }
    }

    @Test
    void fullRepairOfADiamondPickaxe() {
        long effTwoUnbThree = 2L * EFFICIENCY + 3L * UNBREAKING;
        assertEquals(16, AnvilCost.fullRepairCost(DIAMOND, 1.0, effTwoUnbThree + MENDING));
        assertEquals(12, AnvilCost.fullRepairCost(DIAMOND, 1.0, effTwoUnbThree));
        assertEquals(15, AnvilCost.fullRepairCost(DIAMOND, 1.0, 5L * EFFICIENCY + 3L * UNBREAKING));
        assertEquals(20, AnvilCost.fullRepairCost(DIAMOND, 1.0, 10L * EFFICIENCY + 3L * UNBREAKING));
        assertEquals(30, AnvilCost.fullRepairCost(DIAMOND, 1.0, 20L * EFFICIENCY + 3L * UNBREAKING));
    }

    @Test
    void fullRepairOfAnIronPickaxe() {
        assertEquals(5, AnvilCost.fullRepairCost(IRON, 1.0, 2L * EFFICIENCY));
        assertEquals(11, AnvilCost.fullRepairCost(IRON, 1.0, 2L * EFFICIENCY + 3L * UNBREAKING));
    }

    @Test
    void partialRepairIsAQuarterOfTheFullCostForEachUnitRoundedUp() {
        assertEquals(3, AnvilCost.repairCost(12, 1));
        assertEquals(6, AnvilCost.repairCost(12, 2));
        assertEquals(4, AnvilCost.repairCost(15, 1));
        assertEquals(12, AnvilCost.repairCost(15, 3));
        assertEquals(15, AnvilCost.repairCost(15, 4));
        assertEquals(15, AnvilCost.repairCost(15, 9));
    }

    @Test
    void factorsScaleTheVariableParts() {
        assertEquals(1 + 32, AnvilCost.enchantmentCost(1, 2.0, AnvilCost.levelIncrease(UNBREAKING, 0, 3)));
        assertEquals(1 + 5, AnvilCost.enchantmentCost(1, 0.5, 9));
        assertEquals(4 + 4, AnvilCost.fullRepairCost(DIAMOND, 0.5, 8));
    }

    @Test
    void levelThatDoesNotIncreaseCostsNothing() {
        assertEquals(0.0, AnvilCost.levelIncrease(EFFICIENCY, 5, 5));
        assertEquals(0.0, AnvilCost.levelIncrease(EFFICIENCY, 5, 3));
    }

    @Test
    void levelZeroHasTheValueZero() {
        assertEquals(0.0, AnvilCost.levelValue(0));
        assertEquals(2.0, AnvilCost.levelValue(1));
        assertEquals(32.0, AnvilCost.levelIncrease(EFFICIENCY, 0, 5));
        assertEquals(16.0, AnvilCost.levelIncrease(EFFICIENCY, 4, 5));
    }

    @Test
    void largeLevelsDoNotOverflow() {
        assertEquals(Math.scalb(1000.0, 255), AnvilCost.levelIncrease(1000, 0, 255));
        assertTrue(AnvilCost.levelIncrease(1, 254, 255) > 0);
        assertEquals(AnvilCost.MAX_COST + 1, AnvilCost.enchantmentCost(1, 1.0, AnvilCost.levelIncrease(1, 0, 63)));
        assertEquals(AnvilCost.MAX_COST + 1, AnvilCost.enchantmentCost(1, 0.5, AnvilCost.levelIncrease(1, 0, 255)));
        assertEquals(AnvilCost.MAX_COST + 1,
                AnvilCost.enchantmentCost(1000, 1000.0, AnvilCost.levelIncrease(Integer.MAX_VALUE, 0, 255)));
        // k = 0 removes the variable part, also for the highest level.
        assertEquals(1, AnvilCost.enchantmentCost(1, 0.0, AnvilCost.levelIncrease(1, 0, 255)));
        assertEquals(AnvilCost.MAX_COST, AnvilCost.enchantmentCost(AnvilCost.MAX_COST, 0.0, 0.0));
    }

    @Test
    void materialUnitsFollowTheVanillaLoop() {
        // Diamond pickaxe: 1561 durability, each unit repairs 390.
        // A damage equal to the maximum leaves 1 point after 4 units: the loop uses a fifth unit, as vanilla.
        assertEquals(5, AnvilCost.materialUnits(1561, 1561, 64));
        assertEquals(2, AnvilCost.materialUnits(1561, 1561, 2));
        assertEquals(1, AnvilCost.materialUnits(390, 1561, 64));
        assertEquals(2, AnvilCost.materialUnits(391, 1561, 64));
        assertEquals(1, AnvilCost.materialUnits(1, 1561, 64));
        assertEquals(0, AnvilCost.materialUnits(0, 1561, 64));
        // 1560 damage: 4 units of 390.
        assertEquals(4, AnvilCost.materialUnits(1560, 1561, 64));
    }

    @Test
    void equivalentUnitsOfARepairWithASecondItem() {
        assertEquals(1, AnvilCost.equivalentUnits(1, 1561));
        assertEquals(1, AnvilCost.equivalentUnits(390, 1561));
        assertEquals(2, AnvilCost.equivalentUnits(391, 1561));
        assertEquals(4, AnvilCost.equivalentUnits(1561, 1561));
        assertEquals(4, AnvilCost.equivalentUnits(5000, 1561));
    }
}
