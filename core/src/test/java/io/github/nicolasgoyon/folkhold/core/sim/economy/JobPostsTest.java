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
    void householdsFillTheHomesOfTheValidPostsInOrder() {
        Post cottage = new Post("smith", 30, 64, 0, 3);
        List<Post> posts = List.of(FARMER, cottage, MASON);
        int none = JobPosts.NO_POST;
        assertEquals(List.of(0, 0, 2, 2, none), JobPosts.households(posts, job -> !job.equals("smith"), 5));
        assertEquals(List.of(0), JobPosts.households(posts, JobPostsTest::allValid, 1));
        assertEquals(List.of(), JobPosts.households(posts, JobPostsTest::allValid, 0));
        assertEquals(List.of(none, none), JobPosts.households(List.of(), JobPostsTest::allValid, 2));
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
    void postWithAJobThatIsNotValidTakesNoInhabitant() {
        Post hut = new Post("fisher", 30, 64, 0, 2);
        assertEquals(List.of("farmer"), JobPosts.activeJobs(List.of(hut, FARMER, MASON), job -> !job.equals("fisher"),
                2));
    }

    @Test
    void legacySlotsCountOnlyTheActivePosts() {
        List<String> jobs = JobPosts.jobs(Optional.of(List.of(FARMER, MASON)), true, SLOT_JOBS.subList(0, 2), 2,
                JobPostsTest::allValid, 2);
        assertEquals(List.of("farmer", "baker"), jobs);
    }

    @Test
    void foodPostThatTradesComesFirst() {
        Post smithFirst = new Post("smith", 0, 64, 0, 2);
        Post farmer = new Post("farmer", 10, 64, 0, 2);
        Post digger = new Post("digger", 20, 64, 0, 2);
        List<Post> ordered = JobPosts.order(Optional.empty(), List.of(digger, smithFirst, farmer),
                job -> job.equals("farmer"), job -> !job.equals("digger"));
        assertEquals(List.of(farmer, smithFirst, digger), ordered);
        // A small village (2 inhabitants of the homes) has its food post active.
        assertEquals(List.of("farmer"), JobPosts.activeJobs(ordered, JobPostsTest::allValid, 2));
    }

    @Test
    void laterScanKeepsTheOrderInEachGroupAndAddsTheNewPosts() {
        Post mason = new Post("mason", 10, 64, 0, 3);
        List<Post> ordered = JobPosts.order(Optional.of(List.of(MASON, SMITH)), List.of(FARMER, SMITH, mason),
                JobPostsTest::none, JobPostsTest::allValid);
        assertEquals(List.of(mason, SMITH, FARMER), ordered);
        List<Post> withFood = JobPosts.order(Optional.of(List.of(MASON, SMITH)), List.of(FARMER, SMITH, mason),
                job -> job.equals("farmer"), JobPostsTest::allValid);
        assertEquals(List.of(FARMER, mason, SMITH), withFood);
        assertEquals(List.of(SMITH), JobPosts.order(Optional.of(List.of(MASON, SMITH)), List.of(SMITH),
                JobPostsTest::none, JobPostsTest::allValid));
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

    private static boolean none(String job) {
        return false;
    }
}
