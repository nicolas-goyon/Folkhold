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
    void marketDayAddsOffersUpToTheCap() {
        assertEquals(3, JobTrades.offers(7, 2, 1, 0), "A day that is not a market day gives the share");
        assertEquals(5, JobTrades.offers(7, 2, 1, 2), "A market day adds the extra offers to the share");
        assertEquals(JobTrades.MAX_OFFERS, JobTrades.offers(7, 1, 0, 64), "The extra offers stop at the cap");
        assertEquals(JobTrades.MAX_OFFERS, JobTrades.offers(22, 1, 0, 2));
        assertEquals(30, JobTrades.offers(30, 1, 0, 2), "The cap does not remove offers of the share");
        assertEquals(0, JobTrades.offers(7, 0, 0, 2), "A village with no traders has no offers");
        assertEquals(0, JobTrades.offers(0, 3, 0, 2), "A level with no offers has no offers");
    }

    @Test
    void surplusIsTheStockThatNoWorkerSellsAboveTheReserve() {
        Map<String, Integer> stock = Map.of("sword", 2, "helmet", 9, "iron", 40, "coal", 40, "bread", 50,
                "emerald", 9);

        // Population 10: the target of iron and coal is 32, the target of bread is 32.
        assertEquals(Map.of("helmet", 9, "sword", 2, "iron", 8, "coal", 8, "bread", 18), JobTrades.surplus(List.of(),
                List.of(SMITH), stock, CATALOG, 10, JobTradesTest::known));
        assertEquals(List.of("helmet", "bread", "iron", "sword", "coal"), List.copyOf(JobTrades.surplus(List.of(),
                List.of(SMITH), stock, CATALOG, 10, JobTradesTest::known).keySet()), "The most valuable first");
        assertEquals(Map.of("iron", 8, "coal", 8, "bread", 18), JobTrades.surplus(List.of(SMITH), List.of(SMITH),
                stock, CATALOG, 10, JobTradesTest::known), "No output of a worker");
        assertEquals(Map.of("coal", 40, "iron", 40), JobTrades.surplus(List.of(), List.of(), Map.of("iron", 40,
                "coal", 40, "bread", 20), CATALOG, 10, JobTradesTest::known), "No input, and the bread reserve");
    }
}
