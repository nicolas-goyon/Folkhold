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
}
