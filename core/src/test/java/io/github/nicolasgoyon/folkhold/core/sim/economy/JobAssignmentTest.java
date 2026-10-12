package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class JobAssignmentTest {
    private static final ItemCatalog CATALOG = new ItemCatalog(Map.of(
            "bread", new ItemInfo("bread", 1.0, 5, 32), "ore", new ItemInfo("ore", 1.0, 0, 32)));
    private static final Job BAKER = new Job("baker", new Recipe(List.of(), List.of(new Recipe.Output("bread", 1, 1.0))),
            1, 1, List.of());
    private static final Job MINER = new Job("miner", new Recipe(List.of(), List.of(new Recipe.Output("ore", 1, 1.0))),
            1, 10, List.of());

    @Test
    void assignmentFillsTheSlotsWithAShareOfFoodJobs() {
        List<String> jobs = JobAssignment.assign(List.of(), List.of(BAKER, MINER), 5, CATALOG, 42);

        assertEquals(5, jobs.size());
        assertTrue(jobs.stream().filter("baker"::equals).count() >= 2, "Expected 2 food jobs or more: " + jobs);
    }

    @Test
    void currentValidJobsStay() {
        List<String> jobs = JobAssignment.assign(List.of("miner", "unknown", "miner"), List.of(BAKER, MINER), 4,
                CATALOG, 1);

        assertEquals(List.of("miner", "miner"), jobs.subList(0, 2));
        assertEquals(4, jobs.size());
    }

    @Test
    void sameSeedGivesTheSameJobs() {
        assertEquals(JobAssignment.assign(List.of(), List.of(BAKER, MINER), 8, CATALOG, 9),
                JobAssignment.assign(List.of(), List.of(BAKER, MINER), 8, CATALOG, 9));
    }

    @Test
    void noEligibleJobGivesOnlyTheCurrentValidJobs() {
        assertEquals(List.of(), JobAssignment.assign(List.of("miner"), List.of(), 3, CATALOG, 1));
    }
}
