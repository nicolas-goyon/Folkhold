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
        Map<String, Integer> stock = Map.of("iron", 20, "coal", 1);
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), stock, CATALOG, 10, 6, JobTradesTest::known);

        assertEquals(List.of("coal"), plan.buys(), "The stock of iron is more than half of its target");
    }

    @Test
    void largestNeedComesFirst() {
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), Map.of("iron", 4), CATALOG, 10, 6,
                JobTradesTest::known);

        assertEquals(List.of("coal", "iron"), plan.buys());
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
        JobTrades.Plan plan = JobTrades.plan(List.of(SMITH), Map.of(), CATALOG, 10, 6, JobTradesTest::known);
        assertEquals(4, plan.sellSlots());
        assertEquals(2, plan.buySlots());

        JobTrades.Plan one = JobTrades.plan(List.of(SMITH), Map.of(), CATALOG, 10, 1, JobTradesTest::known);
        assertEquals(1, one.sellSlots());
        assertEquals(0, one.buySlots());

        JobTrades.Plan none = JobTrades.plan(List.of(SMITH), Map.of(), CATALOG, 10, 0, JobTradesTest::known);
        assertEquals(0, none.sellSlots());
        assertEquals(0, none.buySlots());
    }

    @Test
    void offersDivideBetweenTheTraders() {
        assertEquals(7, JobTrades.share(7, 1));
        assertEquals(4, JobTrades.share(7, 2));
        assertEquals(1, JobTrades.share(3, 5));
        assertEquals(0, JobTrades.share(7, 0));
        assertEquals(0, JobTrades.share(0, 3));
    }
}
