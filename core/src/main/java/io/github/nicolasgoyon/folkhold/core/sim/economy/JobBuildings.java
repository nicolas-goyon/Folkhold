package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * The buildings of the jobs of a village. A job sign in a building names the job of the building.
 *
 * <ul>
 *   <li>The heart of the village counts the job signs of each job. Before the first count, the counts are unknown
 *       (an empty {@link Optional}).</li>
 *   <li>A job that needs a building is active only when the village has one building of this job or more. One
 *       building serves all the slots of its job.</li>
 *   <li>A job that does not need a building is always active.</li>
 *   <li>Unknown counts make all the jobs active: a village that the heart did not scan (an abstract village) keeps
 *       all its production.</li>
 * </ul>
 *
 * <p>The workers of an inactive job have no job: they are inhabitants.
 */
public final class JobBuildings {
    /** The namespace of a job id with no namespace, for example the job {@code blacksmith} of a job sign. */
    public static final String DEFAULT_NAMESPACE = "folkhold_world";

    private JobBuildings() {
    }

    /**
     * The job id of the job of a job sign: the sign value with the namespace {@link #DEFAULT_NAMESPACE} if it has no
     * namespace.
     *
     * @param signJob the job of the sign, for example {@code blacksmith}
     * @return the job id, for example {@code folkhold_world:blacksmith}, or an empty text for an empty sign
     */
    public static String jobId(String signJob) {
        String job = signJob.trim();
        if (job.isEmpty() || job.contains(":")) {
            return job;
        }
        return DEFAULT_NAMESPACE + ":" + job;
    }

    /**
     * The active jobs of a village.
     *
     * @param jobs          the job ids of the job slots, in their order. Two slots can have the same job.
     * @param counts        the number of buildings of each job id, or empty if the counts are unknown
     * @param needsBuilding true for a job id that needs a building
     * @return the job ids of the slots with an active job, in their order
     */
    public static List<String> active(List<String> jobs, Optional<Map<String, Integer>> counts,
            Predicate<String> needsBuilding) {
        if (counts.isEmpty()) {
            return List.copyOf(jobs);
        }
        Map<String, Integer> known = counts.get();
        return jobs.stream()
                .filter(job -> !needsBuilding.test(job) || known.getOrDefault(job, 0) > 0)
                .toList();
    }
}
