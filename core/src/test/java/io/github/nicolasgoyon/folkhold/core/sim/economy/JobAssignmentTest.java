package io.github.nicolasgoyon.folkhold.core.sim.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.IntStream;
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

    /**
     * The multiplier of a culture changes the weight of a job in the settlements of this culture only. A job with
     * the multiplier of a culture comes more often in these settlements.
     */
    @Test
    void cultureMultipliesTheWeight() {
        Job fisher = new Job("fisher", new Recipe(List.of(), List.of(new Recipe.Output("ore", 1, 1.0))), 1, 10,
                List.of(), Map.of("valeholm", 3));

        assertEquals(30, fisher.forCulture("valeholm").weight());
        assertEquals(10, fisher.forCulture("ashmark").weight());
        assertEquals(10, fisher.forCulture(null).weight());

        long plain = JobAssignment.assign(List.of(), List.of(MINER, fisher), 1000, CATALOG, 5).stream()
                .filter("fisher"::equals).count();
        long north = JobAssignment.assign(List.of(), List.of(MINER, fisher.forCulture("valeholm")), 1000, CATALOG, 5)
                .stream().filter("fisher"::equals).count();
        // Weights 10 and 10: about 50 % of fishers. Weights 10 and 30: about 75 %.
        assertTrue(plain > 450 && plain < 550, "About 50 % of fishers with no culture: " + plain);
        assertTrue(north > 700 && north < 800, "About 75 % of fishers in Valeholm: " + north);
    }

    /** A food job with no trade. */
    private static final Job FORAGER = new Job("forager",
            new Recipe(List.of(), List.of(new Recipe.Output("bread", 1, 1.0))), 1, 10, List.of());
    /** An other job with no trade. */
    private static final Job DIGGER = new Job("digger",
            new Recipe(List.of(), List.of(new Recipe.Output("ore", 1, 1.0))), 1, 10, List.of());
    /** The baker and the miner trade. */
    private static final Predicate<String> TRADES = Set.of("baker", "miner")::contains;
    private static final List<Job> ALL = List.of(BAKER, MINER, FORAGER, DIGGER);

    private static String next(List<String> posts, List<Job> eligible, long seed) {
        return JobAssignment.next(posts, eligible, CATALOG, TRADES, seed).orElseThrow();
    }

    /** Step 1: a village with no food post gets a food job that trades, for all the seeds. */
    @Test
    void villageWithNoFoodPostGetsAFoodJobThatTrades() {
        IntStream.range(0, 50).forEach(seed -> assertEquals("baker", next(List.of("miner", "digger"), ALL, seed)));
    }

    /** Step 2: a village with only food posts that do not trade gets a job that trades. */
    @Test
    void villageWithNoTraderGetsAJobThatTrades() {
        IntStream.range(0, 50).forEach(seed -> assertEquals("miner",
                next(List.of("forager"), List.of(MINER, FORAGER, DIGGER), seed)));
        IntStream.range(0, 50).forEach(seed -> assertTrue(TRADES.test(next(List.of("forager", "digger"), ALL, seed)),
                "Expected a job that trades"));
    }

    /** Step 3: a village with fewer food posts than 40 % of its posts gets a food job. */
    @Test
    void villageWithFewFoodPostsGetsAFoodJob() {
        List<String> posts = List.of("baker", "miner", "digger");
        IntStream.range(0, 50).forEach(seed -> assertTrue(Set.of("baker", "forager").contains(next(posts, ALL, seed)),
                "Expected a food job"));
    }

    /** Step 4: a village with enough food and a trader gets a random job, with fewer repeats of its jobs. */
    @Test
    void villageWithFoodAndATraderGetsDifferentJobs() {
        List<String> posts = List.of("baker", "miner");
        List<String> jobs = IntStream.range(0, 1000).mapToObj(seed -> next(posts, ALL, seed)).toList();
        // Weights 1/2, 10/2, 10, 10: the forager and the digger come about 2 times more often than the miner.
        long miners = jobs.stream().filter("miner"::equals).count();
        long diggers = jobs.stream().filter("digger"::equals).count();
        assertTrue(miners > 150 && miners < 260, "About 20 % of miners: " + miners);
        assertTrue(diggers > 340 && diggers < 460, "About 39 % of diggers: " + diggers);
    }

    @Test
    void sameSeedGivesTheSameJobOfAPost() {
        List<String> posts = List.of("baker", "miner");
        assertEquals(next(posts, ALL, 7), next(posts, List.of(DIGGER, FORAGER, MINER, BAKER), 7));
        assertNotEquals(JobAssignment.postSeed(7, 0), JobAssignment.postSeed(7, 1));
    }

    /** Only the eligible jobs (level and biome) are candidates; with no eligible job there is no job. */
    @Test
    void onlyEligibleJobsAreCandidates() {
        IntStream.range(0, 50).forEach(seed -> assertEquals("digger",
                next(List.of("baker", "miner"), List.of(DIGGER), seed)));
        assertEquals(Optional.empty(), JobAssignment.next(List.of(), List.of(), CATALOG, TRADES, 1));
    }

    /** Step 3 with no post: when no food job trades and no job trades, the first post gets a food job. */
    @Test
    void firstPostGetsAFoodJobWhenNoJobTrades() {
        IntStream.range(0, 50).forEach(seed -> assertEquals("forager",
                next(List.of(), List.of(FORAGER, DIGGER), seed)));
    }

    /**
     * Only the posts that need a job get a new job. A post with a job that is not valid for some time (here the
     * blacksmith, not eligible) keeps its job.
     */
    @Test
    void renewGivesANewJobOnlyToThePostsThatNeedAJob() {
        Set<String> needsJob = Set.of("", "fisher");
        List<JobPosts.Post> posts = List.of(new JobPosts.Post("miner", 0, 0, 0, 2),
                new JobPosts.Post("blacksmith", 5, 0, 0, 2), new JobPosts.Post("fisher", 7, 0, 0, 2),
                new JobPosts.Post("", 9, 0, 0, 3));
        List<Job> eligible = List.of(MINER, DIGGER, FORAGER);

        List<JobPosts.Post> renewed = JobAssignment.renew(posts, needsJob::contains, eligible, CATALOG, TRADES, 3,
                post -> true);

        assertEquals(posts.subList(0, 2), renewed.subList(0, 2));
        // No food post and no food job that trades: the food share gives the forager.
        assertEquals(new JobPosts.Post("forager", 7, 0, 0, 2), renewed.get(2));
        assertTrue(Set.of("miner", "digger", "forager").contains(renewed.get(3).jobId()), "Expected a job: " + renewed);
        assertEquals(renewed, JobAssignment.renew(posts, needsJob::contains, eligible, CATALOG, TRADES, 3,
                post -> true));
        assertEquals(posts, JobAssignment.renew(posts, needsJob::contains, List.of(), CATALOG, TRADES, 3,
                post -> true));
    }

    /** A job that the write does not accept does not change the post and does not count for the next posts. */
    @Test
    void jobThatIsNotWrittenDoesNotCount() {
        List<JobPosts.Post> posts = List.of(new JobPosts.Post("miner", 0, 0, 0, 2),
                new JobPosts.Post("", 1, 0, 0, 2), new JobPosts.Post("", 2, 0, 0, 2));
        // The post 1 is not written. Thus the post 2 has no food post before it: step 3 gives the forager for all
        // the seeds. With the job of the post 1 in the count, the post 2 gets step 4 (a random job).
        IntStream.range(0, 20).forEach(seed -> {
            List<JobPosts.Post> renewed = JobAssignment.renew(posts, String::isEmpty, List.of(MINER, FORAGER, DIGGER),
                    CATALOG, TRADES, seed, post -> post.x() == 2);
            assertEquals(posts.get(1), renewed.get(1));
            assertEquals("forager", renewed.get(2).jobId());
        });
    }
}
