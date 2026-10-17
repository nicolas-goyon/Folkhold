package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StockTest {
    @Test
    void addStopsAtTheCapacity() {
        Stock stock = new Stock();
        assertEquals(10, stock.add("bread", 10, 10));
        assertEquals(0, stock.add("bread", 5, 10));
        assertEquals(10, stock.count("bread"));
    }

    @Test
    void addAboveTheCapacityAcceptsThePart() {
        Stock stock = new Stock();
        assertEquals(8, stock.add("bread", 20, 8));
        assertEquals(8, stock.count("bread"));
        assertEquals(8, stock.total());
    }

    @Test
    void removeMoreThanPresentRemovesWhatIsPresent() {
        Stock stock = new Stock();
        stock.set("bread", 3);
        assertEquals(3, stock.remove("bread", 10));
        assertEquals(0, stock.count("bread"));
        assertEquals(0, stock.remove("bread", 1));
        assertFalse(stock.asMap().containsKey("bread"), "An item at 0 leaves the map");
    }

    @Test
    void setZeroOrLessRemovesTheItem() {
        Stock stock = new Stock();
        stock.set("bread", 4);
        stock.set("bread", 0);
        assertFalse(stock.asMap().containsKey("bread"));
        stock.set("bread", -2);
        assertFalse(stock.asMap().containsKey("bread"));
        assertEquals(0, stock.count("bread"));
        assertTrue(stock.asMap().isEmpty());
    }
}
