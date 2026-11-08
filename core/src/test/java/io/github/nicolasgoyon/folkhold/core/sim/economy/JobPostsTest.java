package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts.Post;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class JobPostsTest {
    private static final Post FARMER = new Post("farmer", 0, 64, 0, 2);
    private static final Post MASON = new Post("mason", 10, 64, 0, 2);
    private static final Post SMITH = new Post("smith", 20, 64, 0, 2);
    private static final List<String> SLOT_JOBS = List.of("baker", "miner", "baker", "fisher", "miner", "baker",
            "miner", "fisher");

    @Test
    void postWithNoInhabitantIsNotActive() {
        List<Post> posts = List.of(FARMER, MASON);
        assertEquals(List.of(), JobPosts.activeJobs(posts, JobPostsTest::allValid, 0));
        assertEquals(List.of("farmer"), JobPosts.activeJobs(posts, JobPostsTest::allValid, 2));
        assertEquals(List.of("farmer", "mason"), JobPosts.activeJobs(posts, JobPostsTest::allValid, 3));
    }

    @Test
    void postWithAJobThatIsNotValidIsNotActive() {
        List<Post> posts = List.of(FARMER, SMITH, MASON);
        assertEquals(List.of("farmer", "mason"), JobPosts.activeJobs(posts, job -> !job.equals("smith"), 10));
    }

    @Test
    void legacyVillageWithOnePostKeepsItsJobSlots() {
        List<String> jobs = JobPosts.jobs(Optional.of(List.of(FARMER)), true, SLOT_JOBS.subList(0, 4), 4,
                JobPostsTest::allValid, 10);
        assertEquals(List.of("farmer", "baker", "miner", "baker"), jobs);
    }

    @Test
    void newVillageHasTheJobsOfItsPosts() {
        List<String> jobs = JobPosts.jobs(Optional.of(List.of(FARMER, MASON, SMITH)), false, SLOT_JOBS, 8,
                JobPostsTest::allValid, 10);
        assertEquals(List.of("farmer", "mason", "smith"), jobs);
    }

    @Test
    void villageWithNoScanHasItsJobSlots() {
        assertEquals(SLOT_JOBS, JobPosts.jobs(Optional.empty(), false, SLOT_JOBS, 8, JobPostsTest::allValid, 0));
        assertEquals(SLOT_JOBS, JobPosts.jobs(Optional.empty(), true, SLOT_JOBS, 8, JobPostsTest::allValid, 0));
    }

    @Test
    void legacySlotsAreNeverBelowZero() {
        assertEquals(3, JobPosts.legacySlots(4, 1));
        assertEquals(0, JobPosts.legacySlots(2, 5));
        List<String> jobs = JobPosts.jobs(Optional.of(List.of(FARMER, MASON, SMITH)), true, SLOT_JOBS.subList(0, 2),
                2, JobPostsTest::allValid, 10);
        assertEquals(List.of("farmer", "mason", "smith"), jobs);
    }

    @Test
    void firstScanPutsThePostsThatTradeFirst() {
        List<Post> ordered = JobPosts.order(Optional.empty(), List.of(SMITH, FARMER, MASON),
                job -> !job.equals("smith"));
        assertEquals(List.of(FARMER, MASON, SMITH), ordered);
    }

    @Test
    void laterScanKeepsTheOrderAndAddsTheNewPostsAtTheEnd() {
        Post mason = new Post("mason", 10, 64, 0, 3);
        List<Post> ordered = JobPosts.order(Optional.of(List.of(MASON, FARMER)), List.of(SMITH, FARMER, mason),
                JobPostsTest::allValid);
        assertEquals(List.of(mason, FARMER, SMITH), ordered);
        assertEquals(List.of(FARMER), JobPosts.order(Optional.of(List.of(MASON, FARMER)), List.of(FARMER),
                JobPostsTest::allValid));
    }

    @Test
    void signJobGetsTheDefaultNamespace() {
        assertEquals("mod:blacksmith", JobPosts.jobId("blacksmith", "mod"));
        assertEquals("other:smith", JobPosts.jobId("other:smith", "mod"));
        assertEquals("", JobPosts.jobId(" ", "mod"));
    }

    private static boolean allValid(String job) {
        return true;
    }
}
