package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.function.Function;
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

    private static Job pick(List<Job> jobs, SplittableRandom random) {
        int total = 0;
        for (Job job : jobs) {
            total += job.weight();
        }
        int roll = random.nextInt(total);
        for (Job job : jobs) {
            if (roll < job.weight()) {
                return job;
            }
            roll -= job.weight();
        }
        return jobs.get(jobs.size() - 1);
    }
}
