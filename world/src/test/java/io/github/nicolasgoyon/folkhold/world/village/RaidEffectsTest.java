package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import org.junit.jupiter.api.Test;

/** The formulas of the effects of the end of a raid at a faction village ({@link RaidEffects}). */
class RaidEffectsTest {
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
        assertEquals(10, RaidEffects.removeStocks(stock, 25));
        assertEquals(30, stock.count("minecraft:bread"));
        assertEquals(3, stock.count("minecraft:diamond"));
        assertEquals(33, RaidEffects.removeStocks(stock, 100));
        assertEquals(0, stock.total());
    }

    @Test
    void helperGetsTheChangeOfTheResult() {
        assertEquals(50, RaidEffects.reputationChange(true, 50, 15));
        assertEquals(15, RaidEffects.reputationChange(false, 50, 15));
    }
}
