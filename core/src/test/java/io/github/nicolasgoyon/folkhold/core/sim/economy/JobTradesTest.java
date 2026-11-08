package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobTradesTest {
    private static final Job SMITH = new Job("smith", new Recipe(
            List.of(new Recipe.Input("iron", 2), new Recipe.Input("coal", 1)),
            List.of(new Recipe.Output("sword", 1, 1.0), new Recipe.Output("helmet", 1, 1.0))), 1, 10, List.of());
    private static final ItemCatalog CATALOG = new ItemCatalog(Map.of(
            "sword", new ItemInfo("sword", 8.0, 0, 4),
            "helmet", new ItemInfo("helmet", 5.0, 0, 4),
            "iron", new ItemInfo("iron", 2.0, 0, 32),
            "coal", new ItemInfo("coal", 0.5, 0, 32),
            "bread", new ItemInfo("bread", 1.0, 5, 32)));

    private static boolean known(String item) {
        return !item.equals("emerald");
    }

    @Test
    void workerSellsOnlyTheOutputsOfItsJobs() {
        Map<String, Integer> stock = Map.of("sword", 2, "helmet", 10, "bread", 64);
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), stock, CATALOG, 10, 6, JobTradesTest::known);

        assertEquals(List.of("helmet", "sword"), plan.sells(), "The most valuable stock comes first");
    }

    @Test
    void workerSellsNothingThatTheStockDoesNotHave() {
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), Map.of("bread", 64), CATALOG, 10, 6,
                JobTradesTest::known);

        assertEquals(List.of(), plan.sells());
    }

    @Test
    void workerBuysTheInputsThatTheVillageNeeds() {
        Map<String, Integer> stock = Map.of("iron", 20, "coal", 1, "bread", 64);
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), stock, CATALOG, 10, 6, JobTradesTest::known);

        assertEquals(List.of("coal"), plan.buys(), "The stock of iron is more than half of its target");
    }

    @Test
    void largestNeedComesFirst() {
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), Map.of("iron", 4, "bread", 64), CATALOG, 10, 6,
                JobTradesTest::known);

        assertEquals(List.of("coal", "iron"), plan.buys());
    }

    @Test
    void workerBuysTheFoodThatTheVillageNeedsAfterItsInputs() {
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), Map.of("iron", 4, "bread", 20), CATALOG, 10, 6,
                JobTradesTest::known);
        assertEquals(List.of("coal", "iron", "bread"), plan.buys(), "The bread is below its target");

        Job baker = new Job("baker", new Recipe(List.of(), List.of(new Recipe.Output("bread", 1, 1.0))), 1, 10,
                List.of());
        JobTrades.Plan seller = JobTrades.plan(List.of(baker), Map.of("bread", 20), CATALOG, 10, 6,
                JobTradesTest::known);
        assertEquals(List.of(), seller.buys(), "A worker does not buy the food that it sells");
    }

    @Test
    void itemThatCannotTradeGivesNoOffer() {
        Job miner = new Job("miner", new Recipe(List.of(), List.of(new Recipe.Output("emerald", 1, 1.0))), 1, 10,
                List.of());
        JobTrades.Plan plan = JobTrades.plan(List.of(miner), Map.of("emerald", 10), CATALOG, 10, 6,
                JobTradesTest::known);

        assertEquals(List.of(), plan.sells());
    }

    @Test
    void twoThirdsOfTheOffersSell() {
        Map<String, Integer> stock = Map.of("sword", 5, "helmet", 5, "iron", 0, "coal", 0, "bread", 64);
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), stock, CATALOG, 10, 3, JobTradesTest::known);
        assertEquals(2, plan.sellSlots());
        assertEquals(1, plan.buySlots());
    }

    @Test
    void twoOffersGiveOneSellAndOneBuy() {
        Map<String, Integer> stock = Map.of("sword", 5, "helmet", 5, "bread", 64);
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), stock, CATALOG, 10, 2, JobTradesTest::known);
        assertEquals(1, plan.sellSlots());
        assertEquals(1, plan.buySlots());

        JobTrades.Plan none = JobTrades.plan(List.of(SMITH), stock, CATALOG, 10, 0, JobTradesTest::known);
        assertEquals(0, none.sellSlots());
        assertEquals(0, none.buySlots());
    }

    @Test
    void freeOffersGoToTheOtherKind() {
        JobTrades.Plan noStock = JobTrades.plan(List.of(SMITH), Map.of("bread", 64), CATALOG, 10, 6,
                JobTradesTest::known);
        assertEquals(0, noStock.sellSlots());
        assertEquals(6, noStock.buySlots(), "The sell offers buy when the stock has nothing to sell");

        Map<String, Integer> full = Map.of("sword", 5, "helmet", 5, "iron", 64, "coal", 64, "bread", 64);
        JobTrades.Plan noNeed = JobTrades.plan(List.of(SMITH), full, CATALOG, 10, 2, JobTradesTest::known);
        assertEquals(2, noNeed.sellSlots(), "The buy offers sell when the village needs nothing");
        assertEquals(0, noNeed.buySlots());
    }

    @Test
    void offersDivideBetweenTheTraders() {
        assertEquals(7, JobTrades.share(7, 1, 0));
        assertEquals(4, JobTrades.share(7, 2, 0));
        assertEquals(3, JobTrades.share(7, 2, 1), "The sum of the parts is the offer count of the level");
        assertEquals(2, JobTrades.share(3, 2, 1), "A trader has 2 offers or more");
        assertEquals(1, JobTrades.share(1, 3, 0), "A trader has no more offers than the level");
        assertEquals(0, JobTrades.share(7, 0, 0));
        assertEquals(0, JobTrades.share(0, 3, 0));
    }

    @Test
    void unsoldGoodsAreTheStockThatNoJobMakes() {
        Map<String, Integer> stock = Map.of("sword", 2, "iron", 4, "coal", 40, "bread", 64, "emerald", 9);

        assertEquals(List.of("bread", "coal", "iron"), JobTrades.unsold(List.of(SMITH), stock, CATALOG,
                JobTradesTest::known), "No sword, no currency, the most valuable stock first");
        assertEquals(List.of(), JobTrades.unsold(List.of(), Map.of("bread", 0), CATALOG, JobTradesTest::known));
    }
}
