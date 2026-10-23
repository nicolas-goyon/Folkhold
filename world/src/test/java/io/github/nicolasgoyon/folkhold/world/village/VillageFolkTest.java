package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class VillageFolkTest {
    /** The inhabitants show the jobs of the village in proportion, the most frequent job first. */
    @Test
    void inhabitantsShowTheJobsInProportion() {
        List<String> jobs = List.of("folkhold_world:miner", "folkhold_world:farmer", "folkhold_world:farmer",
                "folkhold_world:farmer");

        assertEquals(List.of("folkhold_world:farmer", "folkhold_world:miner"), VillageFolk.pickJobs(jobs, 2));
        assertEquals(List.of("folkhold_world:farmer", "folkhold_world:farmer", "folkhold_world:farmer",
                "folkhold_world:miner"), VillageFolk.pickJobs(jobs, 4));
        assertTrue(VillageFolk.pickJobs(List.of(), 3).isEmpty(), "No workers must give no jobs");
    }

    /**
     * The workers of a settlement, as in the economy: {@code workersPerJob} inhabitants for each active job. The job
     * slots and the population limit the active jobs.
     */
    @Test
    void workersFollowTheActiveJobs() {
        assertEquals(6, VillageFolk.workerCount(3, 40, 2), "3 job slots: 3 active jobs");
        assertEquals(10, VillageFolk.workerCount(21, 11, 2), "11 inhabitants: 5 active jobs");
        assertEquals(0, VillageFolk.workerCount(0, 40, 2), "No job slots: no workers");
        assertEquals(0, VillageFolk.workerCount(3, 1, 2), "1 inhabitant: no active job");
        assertEquals(3, VillageFolk.workerCount(3, 40, 1), "1 worker for each job");
    }
}
