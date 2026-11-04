package io.github.nicolasgoyon.folkhold.balance.anvil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * The cost table of the proposal of issue #108, with the vanilla merge rule. Weights (vanilla anvil_cost):
 * Efficiency 1, Unbreaking 2, Mending 4. k = r = 1, operation cost 1. Repair bases: iron 3, diamond 4.
 */
class AnvilCostTest {
    private static final int EFFICIENCY = 1;
    private static final int UNBREAKING = 2;
    private static final int MENDING = 4;
    private static final int IRON = 3;
    private static final int DIAMOND = 4;

    /** The cost of one enchantment operation that raises one enchantment. */
    private static long enchant(int weight, int from, int to) {
        return AnvilCost.enchantmentCost(1, 1.0, AnvilCost.levelIncrease(weight, from, to));
    }

    /** The cost of a sequence of operations of +1 level each. */
    private static long steps(int weight, int from, int to) {
        long cost = 0;
        for (int level = from; level < to; level++) {
            cost += enchant(weight, level, level + 1);
        }
        return cost;
    }

    @Test
    void addUnbreakingThreeToAPickaxeWithNoEnchantments() {
        assertEquals(19, enchant(UNBREAKING, 0, 3));
    }

    @Test
    void efficiencyTwoToThreeAndTwoToFive() {
        // A book Efficiency III or V on a pickaxe with Efficiency II: the higher level wins.
        assertEquals(3, MergeRule.VANILLA.merge(2, 3));
        assertEquals(6, enchant(EFFICIENCY, 2, 3));
        assertEquals(22, enchant(EFFICIENCY, 2, 5));
    }

    @Test
    void efficiencyFiveToTenInOneOperationOrFiveSteps() {
        // One operation: a book Efficiency X on a pickaxe with Efficiency V.
        assertEquals(10, MergeRule.VANILLA.merge(5, 10));
        assertEquals(76, enchant(EFFICIENCY, 5, 10));
        // Five steps: each step adds a book of the same level, V + V = VI, VI + VI = VII...
        assertEquals(6, MergeRule.VANILLA.merge(5, 5));
        assertEquals(80, steps(EFFICIENCY, 5, 10));
    }

    @Test
    void efficiencyTenToTwentyInOneOperationOrTenSteps() {
        assertEquals(301, enchant(EFFICIENCY, 10, 20));
        assertEquals(310, steps(EFFICIENCY, 10, 20));
    }

    @Test
    void unbreakingThreeToFive() {
        assertEquals(33, enchant(UNBREAKING, 3, 5));
    }

    @Test
    void pathIsNeverCheaperInSteps() {
        for (int weight = 1; weight <= 8; weight++) {
            for (int from = 0; from < 30; from++) {
                for (int to = from + 1; to <= 40; to++) {
                    assertTrue(steps(weight, from, to) >= enchant(weight, from, to));
                    // Only the fixed cost of each operation separates the two paths.
                    assertEquals(enchant(weight, from, to) + (to - from - 1), steps(weight, from, to));
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
        assertEquals(1 + 36, AnvilCost.enchantmentCost(1, 2.0, AnvilCost.levelIncrease(UNBREAKING, 0, 3)));
        assertEquals(1 + 5, AnvilCost.enchantmentCost(1, 0.5, 9));
        assertEquals(4 + 4, AnvilCost.fullRepairCost(DIAMOND, 0.5, 8));
    }

    @Test
    void levelThatDoesNotIncreaseCostsNothing() {
        assertEquals(0, AnvilCost.levelIncrease(EFFICIENCY, 5, 5));
        assertEquals(0, AnvilCost.levelIncrease(EFFICIENCY, 5, 3));
    }

    @Test
    void largeLevelsDoNotOverflow() {
        assertEquals(255L * 255L * 1000L, AnvilCost.levelIncrease(1000, 0, 255));
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
