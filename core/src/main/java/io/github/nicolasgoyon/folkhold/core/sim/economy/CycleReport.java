package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.List;
import java.util.Map;

/**
 * Result of one economic cycle of a settlement.
 *
 * @param produced    the items that the jobs produced
 * @param consumed    the items that the jobs and the population consumed
 * @param activeJobs  the number of jobs that had workers
 * @param workingJobs the number of active jobs that had their inputs
 * @param needs       the inputs that the jobs did not find in the stock
 * @param foodCovered the share of the food need that the population ate (0 to 1)
 * @param population  the population after the cycle
 */
public record CycleReport(Map<String, Integer> produced, Map<String, Integer> consumed, int activeJobs,
        int workingJobs, List<String> needs, double foodCovered, int population) {

    /** Share of the active jobs that had their inputs (0 to 1). 1 if no job is active. */
    public double jobsWorking() {
        return activeJobs == 0 ? 1.0 : (double) workingJobs / activeJobs;
    }
}
