package io.github.nicolasgoyon.folkhold.core.sim.trade;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher.Order;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher.Participant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TradeMatcherTest {
    private static final ItemCatalog CATALOG = new ItemCatalog(Map.of(
            "bread", new ItemInfo("bread", 1.0, 5, 40),
            "iron", new ItemInfo("iron", 4.0, 0, 20)));
    private static final TradeMatcher.Settings SETTINGS = new TradeMatcher.Settings(10, 256, 500.0);
    private static final TradeMatcher.Pricing UNIT_PRICE = (seller, item) -> CATALOG.info(item).price();

    private static Participant participant(String id, String faction, int x, Map<String, Integer> surpluses,
            Map<String, Integer> needs) {
        return new Participant(id, faction, x, 0, surpluses, needs);
    }

    @Test
    void surplusIsTheQuantityAboveTheTarget() {
        Stock stock = new Stock();
        stock.set("bread", 100);
        stock.set("iron", 25);

        Map<String, Integer> surpluses = TradeMatcher.surpluses(stock, 10, CATALOG);

        assertEquals(Map.of("bread", 60), surpluses, "Iron is below 1.5 × its target: no surplus");
    }

    @Test
    void needIsTheQuantityBelowTheTargetOfAUsedItem() {
        Stock stock = new Stock();
        stock.set("bread", 10);

        Map<String, Integer> needs = TradeMatcher.needs(stock, 10, CATALOG, Set.of("bread"));

        assertEquals(Map.of("bread", 30), needs, "Iron is not used: no need");
        assertTrue(TradeMatcher.needs(stock, 10, CATALOG, Set.of()).isEmpty());
    }

    @Test
    void nearBuyerGetsTheGoodsFirst() {
        Participant seller = participant("seller", "a", 0, Map.of("bread", 50), Map.of());
        Participant far = participant("far", "a", 2000, Map.of(), Map.of("bread", 40));
        Participant near = participant("near", "a", 200, Map.of(), Map.of("bread", 30));

        List<Order> orders = TradeMatcher.match(List.of(seller, far, near), (s, b) -> true, UNIT_PRICE, SETTINGS);

        assertEquals(2, orders.size());
        assertEquals("near", orders.get(0).buyerId());
        assertEquals(Map.of("bread", 30), orders.get(0).items());
        assertEquals("far", orders.get(1).buyerId());
        assertEquals(Map.of("bread", 20), orders.get(1).items(), "The seller has only 20 left");
    }

    @Test
    void itemsFromOneSellerToOneBuyerGoIntoOneOrder() {
        Participant seller = participant("seller", "a", 0, Map.of("bread", 50, "iron", 10), Map.of());
        Participant buyer = participant("buyer", "a", 300, Map.of(), Map.of("bread", 30, "iron", 5));

        List<Order> orders = TradeMatcher.match(List.of(seller, buyer), (s, b) -> true, UNIT_PRICE, SETTINGS);

        assertEquals(1, orders.size());
        assertEquals(Map.of("bread", 30, "iron", 5), orders.get(0).items());
        assertEquals(30 * 1.0 + 5 * 4.0, orders.get(0).value(), 1e-9);
        assertEquals(35, orders.get(0).total());
    }

    @Test
    void caravanCapacityLimitsAnOrder() {
        Participant seller = participant("seller", "a", 0, Map.of("bread", 500), Map.of());
        Participant buyer = participant("buyer", "a", 300, Map.of(), Map.of("bread", 400));
        TradeMatcher.Settings small = new TradeMatcher.Settings(10, 64, 500.0);

        List<Order> orders = TradeMatcher.match(List.of(seller, buyer), (s, b) -> true, UNIT_PRICE, small);

        assertEquals(Map.of("bread", 64), orders.get(0).items());
    }

    @Test
    void filterAndMaximumLimitTheOrders() {
        Participant seller = participant("seller", "a", 0, Map.of("bread", 500), Map.of());
        Participant enemy = participant("enemy", "war", 100, Map.of(), Map.of("bread", 40));
        Participant first = participant("first", "a", 200, Map.of(), Map.of("bread", 40));
        Participant second = participant("second", "a", 300, Map.of(), Map.of("bread", 40));
        TradeMatcher.PairFilter noWar = (s, b) -> !b.factionId().equals("war");

        List<Order> orders = TradeMatcher.match(List.of(seller, enemy, first, second), noWar, UNIT_PRICE,
                new TradeMatcher.Settings(1, 256, 500.0));

        assertEquals(1, orders.size());
        assertEquals("first", orders.get(0).buyerId());
    }

    @Test
    void pricesOfTwoConnectedVillagesConverge() {
        EconomySettings settings = EconomySettings.DEFAULT;
        ItemInfo bread = CATALOG.info("bread");
        Stock rich = new Stock();
        rich.set("bread", 160);
        Stock poor = new Stock();
        poor.set("bread", 2);
        double startGap = Prices.price(bread, poor.count("bread"), 10, settings)
                - Prices.price(bread, rich.count("bread"), 10, settings);

        for (int round = 0; round < 5; round++) {
            Participant a = participant("rich", "a", 0, TradeMatcher.surpluses(rich, 10, CATALOG),
                    TradeMatcher.needs(rich, 10, CATALOG, Set.of("bread")));
            Participant b = participant("poor", "a", 400, TradeMatcher.surpluses(poor, 10, CATALOG),
                    TradeMatcher.needs(poor, 10, CATALOG, Set.of("bread")));
            for (Order order : TradeMatcher.match(List.of(a, b), (s, t) -> true, UNIT_PRICE, SETTINGS)) {
                Stock from = order.sellerId().equals("rich") ? rich : poor;
                Stock to = order.buyerId().equals("rich") ? rich : poor;
                order.items().forEach((item, count) -> to.add(item, from.remove(item, count), 1000));
            }
        }

        double endGap = Prices.price(bread, poor.count("bread"), 10, settings)
                - Prices.price(bread, rich.count("bread"), 10, settings);
        assertTrue(startGap > 1.0, "The start prices are far apart: " + startGap);
        assertTrue(Math.abs(endGap) < startGap / 4, "The prices must converge: " + startGap + " -> " + endGap);
    }
}
