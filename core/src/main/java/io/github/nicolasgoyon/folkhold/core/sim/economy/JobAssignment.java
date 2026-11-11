package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Chooses the jobs of a settlement.
 *
 * <ul>
 *   <li>The settlement keeps its current jobs that are still valid, up to the number of slots.</li>
 *   <li>A share of the slots ({@link #FOOD_SHARE}) goes to jobs that produce food.</li>
 *   <li>The other slots get random jobs. The weight of a job is its chance. Two slots can get the same job.</li>
 *   <li>The same seed and the same input give the same jobs.</li>
 * </ul>
 *
 * <p>The job of a new job post comes from {@link #next}, and a post with a job that is not valid gets a new job from
 * {@link #renew}.
 */
public final class JobAssignment {
    /** Minimum share of the slots for the jobs that produce food. */
    public static final double FOOD_SHARE = 0.4;

    private JobAssignment() {
    }

    /**
     * Chooses the jobs.
     *
     * @param current  the current job ids of the settlement
     * @param eligible the jobs that the settlement can have (level and biome)
     * @param slots    the number of job slots
     * @param catalog  the item values (to find the food jobs)
     * @param seed     the seed, for example from the settlement id
     * @return the job ids
     */
    public static List<String> assign(List<String> current, List<Job> eligible, int slots, ItemCatalog catalog,
            long seed) {
        Map<String, Job> byId = eligible.stream()
                .collect(Collectors.toMap(Job::id, Function.identity(), (a, b) -> a));
        List<String> result = new ArrayList<>();
        for (String id : current) {
            if (result.size() < slots && byId.containsKey(id)) {
                result.add(id);
            }
        }
        if (eligible.isEmpty()) {
            return result;
        }
        SplittableRandom random = new SplittableRandom(seed ^ ((long) slots * 0x9E3779B97F4A7C15L));
        List<Job> foodJobs = eligible.stream().filter(job -> job.producesFood(catalog)).toList();
        int foodNeeded = (int) Math.ceil(slots * FOOD_SHARE);
        int food = (int) result.stream().filter(id -> byId.get(id).producesFood(catalog)).count();
        while (result.size() < slots && food < foodNeeded && !foodJobs.isEmpty()) {
            result.add(pick(foodJobs, random).id());
            food++;
        }
        while (result.size() < slots) {
            result.add(pick(eligible, random).id());
        }
        return result;
    }

    /**
     * Chooses the job of a new job post (see {@link JobPosts}). The first step with a candidate job gives the job:
     *
     * <ol>
     *   <li>No post with a food job: a food job that trades.</li>
     *   <li>No post with a job that trades: a job that trades.</li>
     *   <li>Fewer food posts than {@link #FOOD_SHARE} of the posts: a food job.</li>
     *   <li>Else: a job of {@code eligible}.</li>
     * </ol>
     *
     * <p>The chance of a candidate job is its weight divided by (1 + the number of posts with this job). Thus a village
     * gets different jobs. The same input and the same seed give the same job.
     *
     * @param posts    the job ids of the valid posts of the village
     * @param eligible the jobs that the village can have (level and biome)
     * @param catalog  the item values (to find the food jobs)
     * @param trades   true for a job id with workers that trade
     * @param seed     the seed of the post (see {@link #postSeed})
     * @return the job id, or empty when {@code eligible} is empty
     */
    public static Optional<String> next(List<String> posts, List<Job> eligible, ItemCatalog catalog,
            Predicate<String> trades, long seed) {
        if (eligible.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Job> byId = eligible.stream()
                .collect(Collectors.toMap(Job::id, Function.identity(), (a, b) -> a));
        Predicate<String> food = id -> byId.containsKey(id) && byId.get(id).producesFood(catalog);
        long foodPosts = posts.stream().filter(food).count();
        List<Job> sorted = byId.values().stream().sorted(Comparator.comparing(Job::id)).toList();
        List<Predicate<Job>> steps = new ArrayList<>();
        if (foodPosts == 0) {
            steps.add(job -> food.test(job.id()) && trades.test(job.id()));
        }
        if (posts.stream().noneMatch(trades)) {
            steps.add(job -> trades.test(job.id()));
        }
        if (foodPosts == 0 || foodPosts < posts.size() * FOOD_SHARE) {
            steps.add(job -> food.test(job.id()));
        }
        steps.add(job -> true);
        Map<String, Long> counts = posts.stream().collect(Collectors.groupingBy(Function.identity(),
                Collectors.counting()));
        SplittableRandom random = new SplittableRandom(seed);
        for (Predicate<Job> step : steps) {
            List<Job> candidates = sorted.stream().filter(step).toList();
            if (!candidates.isEmpty()) {
                return Optional.of(pickByNeed(candidates, counts, random).id());
            }
        }
        return Optional.empty();
    }

    /**
     * Gives a job to each post that needs a job (see {@link #next}). The other posts keep their job, also a post with a
     * job that is not valid for some time (for example a minimum level above the level of the village). The posts get
     * their job in their order. A new job counts for the next posts only when {@code write} accepts it.
     *
     * @param posts    the posts of the village, in their order
     * @param needsJob true for a job id that must change (for example a sign with no job)
     * @param eligible the jobs that the village can have (level and biome)
     * @param catalog  the item values (to find the food jobs)
     * @param trades   true for a job id with workers that trade
     * @param seed     the seed of the settlement
     * @param write    writes the new job of a post (for example on its job sign), and returns false when it cannot
     * @return the posts, in the same order. A post keeps its job when {@code eligible} is empty or when {@code write}
     *         returns false.
     */
    public static List<JobPosts.Post> renew(List<JobPosts.Post> posts, Predicate<String> needsJob,
            List<Job> eligible, ItemCatalog catalog, Predicate<String> trades, long seed,
            Predicate<JobPosts.Post> write) {
        List<String> jobs = new ArrayList<>(posts.stream().map(JobPosts.Post::jobId).filter(needsJob.negate())
                .toList());
        List<JobPosts.Post> result = new ArrayList<>(posts);
        for (int i = 0; i < posts.size(); i++) {
            JobPosts.Post post = posts.get(i);
            if (!needsJob.test(post.jobId())) {
                continue;
            }
            Optional<String> job = next(jobs, eligible, catalog, trades, postSeed(seed, i));
            if (job.isEmpty()) {
                continue;
            }
            JobPosts.Post renewed = new JobPosts.Post(job.get(), post.x(), post.y(), post.z(), post.homeBeds());
            if (write.test(renewed)) {
                jobs.add(job.get());
                result.set(i, renewed);
            }
        }
        return result;
    }

    /**
     * The seed of a job post: the seed of the settlement and the index of the post. The same village gets the same
     * jobs.
     *
     * @param seed  the seed of the settlement
     * @param index the index of the post in the posts of the village
     * @return the seed of the post
     */
    public static long postSeed(long seed, int index) {
        return seed ^ ((index + 1L) * 0x9E3779B97F4A7C15L);
    }

    private static Job pickByNeed(List<Job> jobs, Map<String, Long> counts, SplittableRandom random) {
        double sum = 0;
        for (Job job : jobs) {
            sum += job.weight() / (1.0 + counts.getOrDefault(job.id(), 0L));
        }
        double roll = random.nextDouble() * sum;
        for (Job job : jobs) {
            roll -= job.weight() / (1.0 + counts.getOrDefault(job.id(), 0L));
            if (roll < 0) {
                return job;
            }
        }
        return jobs.get(jobs.size() - 1);
    }

    private static Job pick(List<Job> jobs, SplittableRandom random) {
        long sum = 0;
        for (Job job : jobs) {
            sum += job.weight();
        }
        int roll = random.nextInt(Math.toIntExact(sum));
        for (Job job : jobs) {
            if (roll < job.weight()) {
                return job;
            }
            roll -= job.weight();
        }
        return jobs.get(jobs.size() - 1);
    }
}
