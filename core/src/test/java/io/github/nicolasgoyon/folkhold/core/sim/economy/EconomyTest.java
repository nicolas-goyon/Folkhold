package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import org.junit.jupiter.api.Test;

class EconomyTest {
    private static final ItemCatalog CATALOG = new ItemCatalog(Map.of(
            "wheat", new ItemInfo("wheat", 0.2, 0, 64),
            "bread", new ItemInfo("bread", 1.0, 5, 32),
            "carrot", new ItemInfo("carrot", 0.3, 3, 32),
            "ore", new ItemInfo("ore", 1.0, 0, 32),
            "coal", new ItemInfo("coal", 0.5, 0, 32),
            "ingot", new ItemInfo("ingot", 2.0, 0, 32)));
    /** No decay, so the tests can count the items. */
    private static final EconomySettings SETTINGS = new EconomySettings(2, 4, 0.0, 0.1, 0.5, 0.5, 3.0, 1.15, 0.85);

    private static Job job(String id, List<Recipe.Input> inputs, List<Recipe.Output> outputs) {
        return new Job(id, new Recipe(inputs, outputs), 1, 1, List.of());
    }

    private static final Job FARMER = job("farmer", List.of(),
            List.of(new Recipe.Output("wheat", 12, 1.0), new Recipe.Output("carrot", 6, 1.0)));
    private static final Job BAKER = job("baker", List.of(new Recipe.Input("wheat", 6)),
            List.of(new Recipe.Output("bread", 4, 1.0)));
    private static final Job SMELTER = job("smelter",
            List.of(new Recipe.Input("ore", 6), new Recipe.Input("coal", 3)), List.of(new Recipe.Output("ingot", 6, 1.0)));

    private static EconomyState state(int population, String... jobs) {
        EconomyState state = new EconomyState();
        state.setPopulation(population);
        state.setJobs(List.of(jobs));
        return state;
    }

    @Test
    void jobWithItsInputsConsumesAndProduces() {
        EconomyState state = state(2, "smelter");
        state.stock().add("ore", 10, 100);
        state.stock().add("coal", 5, 100);
        state.stock().add("bread", 100, 1000);

        CycleReport report = Economy.runCycle(state, List.of(SMELTER), 2, 100, CATALOG, SETTINGS,
                new SplittableRandom(1));

        assertEquals(4, state.stock().count("ore"));
        assertEquals(2, state.stock().count("coal"));
        assertEquals(6, state.stock().count("ingot"));
        assertEquals(1, report.workingJobs());
        assertTrue(report.needs().isEmpty());
    }

    @Test
    void jobWithAMissingInputProducesNothingAndRecordsTheNeed() {
        EconomyState state = state(2, "smelter");
        state.stock().add("ore", 10, 100);

        CycleReport report = Economy.runCycle(state, List.of(SMELTER), 2, 100, CATALOG, SETTINGS,
                new SplittableRandom(1));

        assertEquals(10, state.stock().count("ore"));
        assertEquals(0, state.stock().count("ingot"));
        assertEquals(List.of("coal"), report.needs());
        assertEquals(0.0, report.jobsWorking());
    }

    @Test
    void populationLimitsTheActiveJobs() {
        EconomyState state = state(3, "farmer", "farmer", "farmer");

        CycleReport report = Economy.runCycle(state, List.of(FARMER, FARMER, FARMER), 3, 1000, CATALOG, SETTINGS,
                new SplittableRandom(1));

        assertEquals(1, report.activeJobs());
    }

    @Test
    void stockCapacityLimitsTheProduction() {
        EconomyState state = state(2, "farmer");

        Economy.runCycle(state, List.of(FARMER), 2, 5, CATALOG, SETTINGS, new SplittableRandom(1));

        assertEquals(5, state.stock().count("wheat"));
    }

    @Test
    void populationEatsTheLargestFoodStockFirst() {
        EconomyState state = state(5, "farmer");
        state.setJobs(List.of());
        state.stock().add("bread", 10, 100);
        state.stock().add("carrot", 2, 100);

        CycleReport report = Economy.runCycle(state, List.of(), 5, 100, CATALOG, SETTINGS, new SplittableRandom(1));

        // Need 5 × 4 = 20 points: 4 bread.
        assertEquals(6, state.stock().count("bread"));
        assertEquals(2, state.stock().count("carrot"));
        assertEquals(1.0, report.foodCovered());
    }

    @Test
    void fedPopulationGrowsAndHungryPopulationShrinks() {
        EconomyState fed = state(10);
        fed.stock().add("bread", 100, 1000);
        Economy.runCycle(fed, List.of(), 20, 1000, CATALOG, SETTINGS, new SplittableRandom(1));
        assertEquals(11, fed.population());

        EconomyState hungry = state(10);
        Economy.runCycle(hungry, List.of(), 20, 1000, CATALOG, SETTINGS, new SplittableRandom(1));
        assertEquals(9, hungry.population());
        assertEquals(0.0, hungry.foodCovered());
    }

    @Test
    void decayRemovesTwoPercentRounded() {
        Stock stock = new Stock();
        stock.add("ore", 100, 1000);
        stock.add("coal", 10, 1000);

        Economy.decay(stock, 0.02);

        assertEquals(98, stock.count("ore"));
        assertEquals(10, stock.count("coal"));
    }

    @Test
    void hamletWithAFarmerAndABakerDoesNotStarve() {
        EconomyState state = state(6, "farmer", "baker");
        List<Job> jobs = List.of(FARMER, BAKER);
        Economy.seedStock(state, jobs, 3, 512, CATALOG, EconomySettings.DEFAULT);
        SplittableRandom random = new SplittableRandom(7);
        for (int cycle = 0; cycle < 60; cycle++) {
            Economy.runCycle(state, jobs, 9, 512, CATALOG, EconomySettings.DEFAULT, random);
            assertTrue(state.foodCovered() >= 0.9, "The hamlet starves at the cycle " + cycle);
        }
    }

    @Test
    void outputChanceGivesTheExpectedMean() {
        Recipe.Output output = new Recipe.Output("diamond", 100, 0.05);
        SplittableRandom random = new SplittableRandom(3);
        int total = 0;
        for (int i = 0; i < 200; i++) {
            total += Economy.roll(output, random);
        }
        // Mean 5 for each roll.
        assertTrue(total > 800 && total < 1200, "Total: " + total);
    }

    @Test
    void toolIsNeededAndWearsWithItsChance() {
        Job miner = job("miner", List.of(new Recipe.Input("pickaxe", 1, 0.1)), List.of(new Recipe.Output("ore", 1, 1.0)));
        EconomyState without = state(2, "miner");
        CycleReport noTool = Economy.runCycle(without, List.of(miner), 2, 100, CATALOG, SETTINGS,
                new SplittableRandom(1));
        assertEquals(List.of("pickaxe"), noTool.needs());

        EconomyState with = state(2, "miner");
        with.stock().add("pickaxe", 1, 10);
        // Food for the 2 inhabitants, so the job keeps its workers.
        with.stock().add("bread", 2000, 2000);
        SplittableRandom random = new SplittableRandom(2);
        int cycles = 0;
        while (with.stock().count("pickaxe") == 1 && cycles < 1000) {
            Economy.runCycle(with, List.of(miner), 2, 1000, CATALOG, SETTINGS, random);
            cycles++;
        }
        assertTrue(cycles > 1 && cycles < 1000, "The tool must wear after some cycles, not " + cycles);
        assertEquals(cycles, with.stock().count("ore"));
    }

    /**
     * With no population, no job works and nothing is eaten. The need of food is covered, so the village gets its
     * first inhabitant.
     */
    @Test
    void emptyVillageRunsACycle() {
        EconomyState state = state(0, "farmer");
        state.stock().add("bread", 10, 100);

        CycleReport report = Economy.runCycle(state, List.of(FARMER), 10, 100, CATALOG, SETTINGS,
                new SplittableRandom(1));

        assertEquals(0, report.activeJobs());
        assertEquals(0, report.workingJobs());
        assertTrue(report.produced().isEmpty(), "No job works with no workers");
        assertEquals(10, state.stock().count("bread"), "Nobody eats");
        assertEquals(1.0, report.foodCovered());
        assertEquals(1, report.population(), "The village gets its first inhabitant");
    }
}
