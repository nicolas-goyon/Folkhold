package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobBuildingsTest {
    private static final List<String> JOBS = List.of("smith", "farmer", "smith", "baker");

    private static boolean allNeed(String job) {
        return true;
    }

    @Test
    void jobWithNoBuildingIsInactive() {
        assertEquals(List.of("farmer", "baker"),
                JobBuildings.active(JOBS, Optional.of(Map.of("farmer", 1, "baker", 2)), JobBuildingsTest::allNeed));
        assertEquals(List.of(), JobBuildings.active(JOBS, Optional.of(Map.of()), JobBuildingsTest::allNeed));
    }

    @Test
    void oneBuildingServesAllTheSlotsOfItsJob() {
        assertEquals(List.of("smith", "smith"),
                JobBuildings.active(JOBS, Optional.of(Map.of("smith", 1)), JobBuildingsTest::allNeed));
    }

    @Test
    void countOfZeroIsNoBuilding() {
        assertEquals(List.of("farmer"),
                JobBuildings.active(JOBS, Optional.of(Map.of("smith", 0, "farmer", 1)), JobBuildingsTest::allNeed));
    }

    @Test
    void unknownCountMakesAllTheJobsActive() {
        assertEquals(JOBS, JobBuildings.active(JOBS, Optional.empty(), JobBuildingsTest::allNeed));
    }

    @Test
    void jobThatNeedsNoBuildingIsAlwaysActive() {
        assertEquals(List.of("farmer", "baker"),
                JobBuildings.active(JOBS, Optional.of(Map.of()), job -> job.equals("smith")));
        assertEquals(JOBS, JobBuildings.active(JOBS, Optional.of(Map.of()), job -> false));
    }

    @Test
    void signJobGetsTheDefaultNamespace() {
        assertEquals("folkhold_world:blacksmith", JobBuildings.jobId("blacksmith"));
        assertEquals("other:smith", JobBuildings.jobId("other:smith"));
        assertEquals("", JobBuildings.jobId(" "));
    }
}
