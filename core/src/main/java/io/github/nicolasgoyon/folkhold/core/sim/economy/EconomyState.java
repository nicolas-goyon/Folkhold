package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.ArrayList;
import java.util.List;

/**
 * Economic state of one settlement: population, jobs, stock and the result of the last cycle.
 */
public final class EconomyState {
    private int population;
    private final List<String> jobs = new ArrayList<>();
    private final Stock stock = new Stock();
    private double foodCovered = 1.0;
    private double jobsWorking = 1.0;
    private boolean initialized;
    private int cycles;

    public int population() {
        return population;
    }

    public void setPopulation(int population) {
        this.population = Math.max(0, population);
    }

    /** The job ids of the settlement. Two jobs can have the same id (for example two farmers). */
    public List<String> jobs() {
        return jobs;
    }

    public void setJobs(List<String> jobIds) {
        jobs.clear();
        jobs.addAll(jobIds);
    }

    public Stock stock() {
        return stock;
    }

    /** Share of the food need that the population ate in the last cycle (0 to 1). */
    public double foodCovered() {
        return foodCovered;
    }

    /** Share of the active jobs that had their inputs in the last cycle (0 to 1). */
    public double jobsWorking() {
        return jobsWorking;
    }

    /**
     * Records the result of the last cycle.
     *
     * @param foodCovered the share of the food need that the population ate
     * @param jobsWorking the share of the active jobs that had their inputs
     */
    public void setLastCycle(double foodCovered, double jobsWorking) {
        this.foodCovered = clamp(foodCovered);
        this.jobsWorking = clamp(jobsWorking);
    }

    /** The number of cycles that ran. The active jobs of a cycle start at this index in the job list. */
    public int cycles() {
        return cycles;
    }

    public void setCycles(int cycles) {
        this.cycles = Math.max(0, cycles);
    }

    /** True after the first population, jobs and stock are set. */
    public boolean isInitialized() {
        return initialized;
    }

    public void setInitialized(boolean initialized) {
        this.initialized = initialized;
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
