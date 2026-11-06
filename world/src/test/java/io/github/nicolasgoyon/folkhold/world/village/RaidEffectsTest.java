package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** The formulas of the effects of the end of a raid at a faction village ({@link RaidEffects}). */
class RaidEffectsTest {
    private static final ItemCatalog CATALOG = new ItemCatalog(Map.of(
            "minecraft:bread", new ItemInfo("minecraft:bread", 0.17, 5, 32),
            "minecraft:cod", new ItemInfo("minecraft:cod", 0.1, 2, 32)));

    @Test
    void villageThatHoldsKeepsItsProgress() {
        assertFalse(RaidEffects.paysFallPenalty(true, true, false, true));
        assertFalse(RaidEffects.paysFallPenalty(true, true, false, false));
    }

    @Test
    void lossRemovesTheProgress() {
        assertTrue(RaidEffects.paysFallPenalty(false, false, false, true));
        assertTrue(RaidEffects.paysFallPenalty(false, false, true, true));
        assertTrue(RaidEffects.paysFallPenalty(false, false, false, false));
    }

    @Test
    void victoryAfterAFallKeepsTheProgressWhenTheHeartStandsAndAPlayerHelps() {
        assertFalse(RaidEffects.paysFallPenalty(false, true, false, true));
        assertTrue(RaidEffects.paysFallPenalty(false, true, true, true));
    }

    @Test
    void victoryAfterAFallWithNoHelperRemovesTheProgress() {
        assertTrue(RaidEffects.paysFallPenalty(false, true, false, false));
    }

    @Test
    void experienceLossIsAPercentOfTheLevel() {
        assertEquals(18, RaidEffects.experienceLoss(35, 50));
        assertEquals(0, RaidEffects.experienceLoss(35, 0));
        assertEquals(35, RaidEffects.experienceLoss(35, 100));
        assertEquals(35, RaidEffects.experienceLoss(35, 150));
        assertEquals(0, RaidEffects.experienceLoss(-5, 50));
    }

    @Test
    void stockLossIsRoundedDownForEachItem() {
        Stock stock = new Stock();
        stock.set("minecraft:bread", 40);
        stock.set("minecraft:diamond", 3);
        assertEquals(10, RaidEffects.removeStocks(stock, 25, CATALOG, 0));
        assertEquals(30, stock.count("minecraft:bread"));
        assertEquals(3, stock.count("minecraft:diamond"));
        assertEquals(33, RaidEffects.removeStocks(stock, 100, CATALOG, 0));
        assertEquals(0, stock.total());
    }

    @Test
    void foodFloorIsTheNeedOfThePopulationForSomeDays() {
        assertEquals(60, RaidEffects.foodFloor(5, 4, 3));
        assertEquals(0, RaidEffects.foodFloor(5, 4, 0));
        assertEquals(0, RaidEffects.foodFloor(0, 4, 3));
        assertEquals(0, RaidEffects.foodFloor(-2, 4, 3));
        assertEquals(Integer.MAX_VALUE, RaidEffects.foodFloor(Integer.MAX_VALUE, 100, 100));
    }

    @Test
    void foodAboveTheFloorLosesTheFullPart() {
        Stock stock = new Stock();
        stock.set("minecraft:bread", 40);
        stock.set("minecraft:diamond", 40);
        // 200 food points, 50 lost: 150 stay, above the floor of 100.
        assertEquals(20, RaidEffects.removeStocks(stock, 25, CATALOG, 100));
        assertEquals(30, stock.count("minecraft:bread"));
        assertEquals(30, stock.count("minecraft:diamond"));
    }

    @Test
    void foodLossStopsAtTheFloor() {
        Stock stock = new Stock();
        stock.set("minecraft:bread", 40);
        stock.set("minecraft:diamond", 40);
        // 200 food points, the floor is 190: the bread loses 2 (10 points), the diamonds lose the full part.
        assertEquals(12, RaidEffects.removeStocks(stock, 25, CATALOG, 190));
        assertEquals(38, stock.count("minecraft:bread"));
        assertEquals(30, stock.count("minecraft:diamond"));
    }

    @Test
    void foodAtTheFloorKeepsAllItsFood() {
        Stock stock = new Stock();
        stock.set("minecraft:bread", 12);
        stock.set("minecraft:cod", 20);
        stock.set("minecraft:diamond", 40);
        assertEquals(10, RaidEffects.removeStocks(stock, 25, CATALOG, 12 * 5 + 20 * 2));
        assertEquals(12, stock.count("minecraft:bread"));
        assertEquals(20, stock.count("minecraft:cod"));
        assertEquals(30, stock.count("minecraft:diamond"));
    }

    @Test
    void foodLossBelowTheFloorIsSharedAndNeverPassesTheFloor() {
        Stock stock = new Stock();
        stock.set("minecraft:bread", 40);
        stock.set("minecraft:cod", 40);
        // 280 food points, a full loss of 70 points. Only 30 points can go: each food item loses 3/7 of its part.
        RaidEffects.removeStocks(stock, 25, CATALOG, 250);
        assertEquals(36, stock.count("minecraft:bread"));
        assertEquals(36, stock.count("minecraft:cod"));
        assertTrue(stock.count("minecraft:bread") * 5 + stock.count("minecraft:cod") * 2 >= 250);
    }

    @Test
    void cooldownBlocksANewPenaltyForItsDuration() {
        assertFalse(RaidEffects.inFallCooldown(1000, Settlement.NO_FALL_PENALTY, 24000));
        assertTrue(RaidEffects.inFallCooldown(1000, 1000, 24000));
        assertTrue(RaidEffects.inFallCooldown(24999, 1000, 24000));
        assertFalse(RaidEffects.inFallCooldown(25000, 1000, 24000));
        assertFalse(RaidEffects.inFallCooldown(1000, 1000, 0));
        assertFalse(RaidEffects.inFallCooldown(500, 1000, 24000));
    }

    @Test
    void helperGetsTheChangeOfTheResult() {
        assertEquals(50, RaidEffects.reputationChange(true, 50, 15));
        assertEquals(15, RaidEffects.reputationChange(false, 50, 15));
    }
}
