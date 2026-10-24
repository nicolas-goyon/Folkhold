package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.TestConfig;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import java.util.Collections;
import java.util.List;
import net.minecraft.world.entity.npc.VillagerProfession;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class VillageFolkTest {
    @BeforeAll
    static void loadConfig() {
        TestConfig.loadWorldConfig();
    }

    private static long count(List<VillagerProfession> professions, VillagerProfession profession) {
        return professions.stream().filter(profession::equals).count();
    }

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

    /**
     * The builders and the handymen come first, also in a small village. Then come the workers, then the plain
     * inhabitants.
     */
    @Test
    void buildersAndHandymenComeFirst() {
        Settlement settlement = new Settlement("folk-test", 0, 0, "faction", "Test", 0, 0);
        settlement.economy().setPopulation(20);
        settlement.economy().setJobs(Collections.nCopies(2, "folkhold_world:farmer"));
        int builders = WorldConfig.BUILDERS.get();
        int handymen = WorldConfig.HANDYMEN.get();

        List<VillagerProfession> large = VillageFolk.professions(settlement, 18);
        List<VillagerProfession> small = VillageFolk.professions(settlement, builders);

        assertEquals(18, large.size());
        assertEquals(Collections.nCopies(builders, ModProfessions.BUILDER.get()), large.subList(0, builders),
                "The builders come first");
        assertEquals(Collections.nCopies(handymen, ModProfessions.HANDYMAN.get()),
                large.subList(builders, builders + handymen), "The handymen come after the builders");
        assertEquals(builders, count(large, ModProfessions.BUILDER.get()));
        assertEquals(handymen, count(large, ModProfessions.HANDYMAN.get()));
        assertEquals(Collections.nCopies(builders, ModProfessions.BUILDER.get()), small,
                "A small village has its builders");
    }
}
