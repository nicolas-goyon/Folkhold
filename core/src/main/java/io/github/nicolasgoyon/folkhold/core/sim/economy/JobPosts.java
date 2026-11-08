package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The job posts of a village, as the workstations of the vanilla villages.
 *
 * <ul>
 *   <li>A post is a job sign in a home. A home is a building with 1 home bed or more. A home has 1 post at most. A job
 *       sign in a building with no home bed is not a post.</li>
 *   <li>A home with no job sign (a cottage) has no post.</li>
 *   <li>The posts have an order, in 3 groups: first the posts with a food job that trades, then the posts with an
 *       other job that trades, then the other posts. In a group, the known posts keep their order, and the new posts
 *       come after them in the scan order. Each scan applies this order (see {@link #order}).</li>
 *   <li>The inhabitants of the homes fill the homes of the valid posts in the order of the posts. A post is valid when
 *       its job is valid (job file, minimum level, biome). A post with a job that is not valid takes no inhabitant.
 *       A post is active when it is valid and its home has 1 inhabitant or more. The economy runs one job slot for
 *       each active post.</li>
 *   <li>A village with no list of posts (no scan of the heart) has the job slots of its level (see
 *       {@link JobAssignment}).</li>
 *   <li>A legacy village (a save with no list of posts) has its active posts and also
 *       {@code max(0, job_slots - active posts)} legacy job slots. Thus it never has fewer jobs than
 *       {@code job_slots}.</li>
 * </ul>
 */
public final class JobPosts {
    private JobPosts() {
    }

    /**
     * A job post.
     *
     * @param jobId    the job id of the job sign, for example {@code folkhold_world:farmer}
     * @param x        block X of the job sign
     * @param y        block Y of the job sign
     * @param z        block Z of the job sign
     * @param homeBeds the number of home beds of the home of the post (1 or more)
     */
    public record Post(String jobId, int x, int y, int z, int homeBeds) {
        /** True if the two posts have the same job sign position. */
        public boolean samePlace(Post other) {
            return x == other.x && y == other.y && z == other.z;
        }
    }

    /**
     * The job id of the job of a job sign: the sign value, with a default namespace if it has no namespace.
     *
     * @param signJob   the job of the sign, for example {@code blacksmith}
     * @param namespace the default namespace, for example {@code folkhold_world}
     * @return the job id, for example {@code folkhold_world:blacksmith}, or an empty text for an empty sign
     */
    public static String jobId(String signJob, String namespace) {
        String job = signJob.trim();
        if (job.isEmpty() || job.contains(":")) {
            return job;
        }
        return namespace + ":" + job;
    }

    /**
     * The posts of a scan, in the order of the village.
     *
     * @param known   the posts that the village has, or empty before the first scan
     * @param scanned the posts of the scan, in the scan order
     * @param food    true for a job id that produces food
     * @param trades  true for a job id with workers that trade
     * @return the posts of the scan (with the job and the beds of the scan), in 3 groups: food jobs that trade, other
     *         jobs that trade, other jobs. In a group, the known posts in their order, then the new posts.
     */
    public static List<Post> order(Optional<List<Post>> known, List<Post> scanned, Predicate<String> food,
            Predicate<String> trades) {
        List<Post> byAge = new ArrayList<>(scanned.size());
        for (Post old : known.orElse(List.of())) {
            scanned.stream().filter(old::samePlace).findFirst().ifPresent(byAge::add);
        }
        for (Post post : scanned) {
            if (byAge.stream().noneMatch(post::samePlace)) {
                byAge.add(post);
            }
        }
        List<Post> result = new ArrayList<>(byAge.size());
        byAge.stream().filter(post -> trades.test(post.jobId()) && food.test(post.jobId())).forEach(result::add);
        byAge.stream().filter(post -> trades.test(post.jobId()) && !food.test(post.jobId())).forEach(result::add);
        byAge.stream().filter(post -> !trades.test(post.jobId())).forEach(result::add);
        return result;
    }

    /**
     * The jobs of the active posts, in the order of the posts.
     *
     * @param posts       the posts, in their order
     * @param valid       true for a valid job id (job file, minimum level, biome)
     * @param inhabitants the inhabitants of the homes: they fill the homes of the valid posts in the order of the posts
     * @return the job ids
     */
    public static List<String> activeJobs(List<Post> posts, Predicate<String> valid, int inhabitants) {
        List<String> jobs = new ArrayList<>();
        int before = 0;
        for (Post post : posts) {
            if (!valid.test(post.jobId())) {
                continue;
            }
            if (before < inhabitants) {
                jobs.add(post.jobId());
            }
            before += Math.max(1, post.homeBeds());
        }
        return jobs;
    }

    /**
     * The number of legacy job slots of a legacy village: each active post takes one job slot of the level.
     *
     * @param jobSlots    the job slots of the level
     * @param activePosts the number of active posts
     * @return {@code max(0, jobSlots - activePosts)}
     */
    public static int legacySlots(int jobSlots, int activePosts) {
        return Math.max(0, jobSlots - activePosts);
    }

    /**
     * The jobs of a village: one job for each active job slot.
     *
     * @param posts       the posts, or empty when the heart did not scan the village
     * @param legacy      true for a legacy village
     * @param slotJobs    the jobs of the job slots of the level (see {@link JobAssignment}), in their order
     * @param jobSlots    the job slots of the level
     * @param valid       true for a valid job id
     * @param inhabitants the inhabitants of the homes
     * @return with no posts, {@code slotJobs}. Else the jobs of the active posts, then for a legacy village the first
     *         {@link #legacySlots} jobs of {@code slotJobs}.
     */
    public static List<String> jobs(Optional<List<Post>> posts, boolean legacy, List<String> slotJobs, int jobSlots,
            Predicate<String> valid, int inhabitants) {
        if (posts.isEmpty()) {
            return List.copyOf(slotJobs);
        }
        List<String> jobs = new ArrayList<>(activeJobs(posts.get(), valid, inhabitants));
        if (legacy) {
            int legacySlots = Math.min(slotJobs.size(), legacySlots(jobSlots, jobs.size()));
            jobs.addAll(slotJobs.subList(0, legacySlots));
        }
        return List.copyOf(jobs);
    }
}
