package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PricesTest {
    private static final ItemInfo BREAD = new ItemInfo("bread", 2.0, 5, 40);
    private static final EconomySettings SETTINGS = EconomySettings.DEFAULT;

    @Test
    void stockAtTheTargetGivesTheBasePrice() {
        assertEquals(2.0, Prices.price(BREAD, 40, 10, SETTINGS), 1e-9);
    }

    @Test
    void surplusLowersThePriceDownToHalf() {
        assertEquals(2.0 * Math.sqrt(40.0 / 80.0), Prices.price(BREAD, 80, 10, SETTINGS), 1e-9);
        assertEquals(1.0, Prices.price(BREAD, 10_000, 10, SETTINGS), 1e-9);
    }

    @Test
    void shortageRaisesThePriceUpToThreeTimes() {
        assertEquals(2.0 * 2.0, Prices.price(BREAD, 10, 10, SETTINGS), 1e-9);
        assertEquals(6.0, Prices.price(BREAD, 0, 10, SETTINGS), 1e-9);
    }

    @Test
    void targetGrowsWithThePopulation() {
        assertEquals(40, Prices.target(BREAD, 10));
        assertEquals(80, Prices.target(BREAD, 20));
        assertEquals(4, Prices.target(BREAD, 0));
    }

    @Test
    void sellPriceIsAboveAndBuyPriceIsBelowThePrice() {
        double price = Prices.price(BREAD, 40, 10, SETTINGS);
        assertEquals(price * 1.15, Prices.sellPrice(BREAD, 40, 10, SETTINGS), 1e-9);
        assertEquals(price * 0.85, Prices.buyPrice(BREAD, 40, 10, SETTINGS), 1e-9);
        assertTrue(Prices.buyPrice(BREAD, 40, 10, SETTINGS) < Prices.sellPrice(BREAD, 40, 10, SETTINGS));
    }
}
