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
 *   <li>The household of a home does the job of its post. A home with no job sign (a cottage) has no post.</li>
 *   <li>The posts have an order: the order in which the village gets them. At the first scan of the heart, the posts
 *       with a job that trades come first, in the scan order, then the other posts. A later scan keeps the order of the
 *       known posts and adds the new posts at the end.</li>
 *   <li>The inhabitants fill the homes in the order of the posts. A post is active when its job is valid (job file,
 *       minimum level, biome) and its home has 1 inhabitant or more. The economy runs one job slot for each active
 *       post.</li>
 *   <li>A village with no list of posts (no scan of the heart) has the job slots of its level (see
 *       {@link JobAssignment}).</li>
 *   <li>A legacy village (a save with no list of posts) has its active posts and also
 *       {@code max(0, job_slots - posts)} legacy job slots. Thus it never has fewer jobs than before.</li>
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
     * @param trades  true for a job id with workers that trade
     * @return the posts of the scan: first the known posts in their order (with the job and the beds of the scan),
     *         then the new posts. With no known posts, the posts with a job that trades come first.
     */
    public static List<Post> order(Optional<List<Post>> known, List<Post> scanned, Predicate<String> trades) {
        List<Post> result = new ArrayList<>(scanned.size());
        if (known.isEmpty()) {
            scanned.stream().filter(post -> trades.test(post.jobId())).forEach(result::add);
            scanned.stream().filter(post -> !trades.test(post.jobId())).forEach(result::add);
            return result;
        }
        for (Post old : known.get()) {
            scanned.stream().filter(old::samePlace).findFirst().ifPresent(result::add);
        }
        for (Post post : scanned) {
            if (result.stream().noneMatch(post::samePlace)) {
                result.add(post);
            }
        }
        return result;
    }

    /**
     * The jobs of the active posts, in the order of the posts.
     *
     * @param posts       the posts, in their order
     * @param valid       true for a valid job id (job file, minimum level, biome)
     * @param inhabitants the inhabitants of the homes: they fill the homes in the order of the posts
     * @return the job ids
     */
    public static List<String> activeJobs(List<Post> posts, Predicate<String> valid, int inhabitants) {
        List<String> jobs = new ArrayList<>();
        int before = 0;
        for (Post post : posts) {
            if (before < inhabitants && valid.test(post.jobId())) {
                jobs.add(post.jobId());
            }
            before += Math.max(1, post.homeBeds());
        }
        return jobs;
    }

    /**
     * The number of legacy job slots of a legacy village: each post takes one job slot of the level.
     *
     * @param jobSlots the job slots of the level
     * @param posts    the number of posts
     * @return {@code max(0, jobSlots - posts)}
     */
    public static int legacySlots(int jobSlots, int posts) {
        return Math.max(0, jobSlots - posts);
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
            int legacySlots = Math.min(slotJobs.size(), legacySlots(jobSlots, posts.get().size()));
            jobs.addAll(slotJobs.subList(0, legacySlots));
        }
        return List.copyOf(jobs);
    }
}
