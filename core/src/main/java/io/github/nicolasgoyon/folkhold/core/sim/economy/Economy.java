package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.random.RandomGenerator;

/**
 * One economic cycle of a settlement (one game day with the default values).
 *
 * <ol>
 *   <li><b>Production</b>: the number of active jobs is {@code population / workersPerJob}, with a maximum of the
 *       number of jobs. The active jobs start at the index {@link EconomyState#cycles()} of the job list, modulo
 *       its size, so each job works in turn when the workers are few. Each active job consumes its inputs and
 *       produces its outputs. A job with a missing input produces nothing, and the input goes into the needs. An
 *       input with a chance below 1 (a tool) must be in the stock, and the job consumes each unit with this
 *       chance.</li>
 *   <li><b>Consumption</b>: the population eats {@code foodPerInhabitant} food points for each inhabitant. It
 *       eats the food with the largest stock first.</li>
 *   <li><b>Decay</b>: each stock loses {@code decayRate} of its quantity (rounded).</li>
 *   <li><b>Population</b>: a fed population (90 % or more) grows by {@code growthRate}, up to the maximum. A
 *       hungry population (below 50 %) loses one inhabitant. A population of 0 has no food need: a newcomer
 *       arrives at each cycle, so a village that lost all its inhabitants lives again.</li>
 * </ol>
 *
 * <p>The stock capacity limits the quantity of each item.
 */
public final class Economy {
    /** A population that eats this share of its need or more grows. */
    static final double FED = 0.9;
    /** A population that eats less than this share of its need shrinks. */
    static final double HUNGRY = 0.5;

    private Economy() {
    }

    /**
     * Runs one cycle.
     *
     * @param state         the economic state of the settlement
     * @param jobs          the jobs of the settlement, in the order of {@link EconomyState#jobs()}. A job id
     *                      with no definition is not in the list.
     * @param maxPopulation the maximum population (from the level)
     * @param stockCapacity the maximum quantity of each item (from the level)
     * @param catalog       the item values
     * @param settings      the economy settings
     * @param random        the random source of the output chances
     * @return the report of the cycle
     */
    public static CycleReport runCycle(EconomyState state, List<Job> jobs, int maxPopulation, int stockCapacity,
            ItemCatalog catalog, EconomySettings settings, RandomGenerator random) {
        Stock stock = state.stock();
        Map<String, Integer> produced = new TreeMap<>();
        Map<String, Integer> consumed = new TreeMap<>();
        Set<String> needs = new LinkedHashSet<>();

        int active = Math.min(jobs.size(), state.population() / settings.workersPerJob());
        int first = jobs.isEmpty() ? 0 : state.cycles() % jobs.size();
        int working = 0;
        for (int i = 0; i < active; i++) {
            Recipe recipe = jobs.get((first + i) % jobs.size()).recipe();
            List<String> missing = new ArrayList<>();
            for (Recipe.Input input : recipe.inputs()) {
                if (stock.count(input.item()) < input.count()) {
                    missing.add(input.item());
                }
            }
            if (!missing.isEmpty()) {
                needs.addAll(missing);
                continue;
            }
            working++;
            for (Recipe.Input input : recipe.inputs()) {
                int used = roll(input.count(), input.chance(), random);
                if (used > 0) {
                    consumed.merge(input.item(), stock.remove(input.item(), used), Integer::sum);
                }
            }
            for (Recipe.Output output : recipe.outputs()) {
                int count = roll(output, random);
                if (count > 0) {
                    int added = stock.add(output.item(), count, stockCapacity);
                    if (added > 0) {
                        produced.merge(output.item(), added, Integer::sum);
                    }
                }
            }
        }

        double foodCovered = eat(state, catalog, settings, consumed);
        decay(stock, settings.decayRate());
        updatePopulation(state, foodCovered, maxPopulation, settings);

        CycleReport report = new CycleReport(produced, consumed, active, working, List.copyOf(needs), foodCovered,
                state.population());
        state.setLastCycle(foodCovered, report.jobsWorking());
        state.setCycles(state.cycles() + 1);
        return report;
    }

    /** Number of units of an output: each unit has the chance of the output. */
    static int roll(Recipe.Output output, RandomGenerator random) {
        return roll(output.count(), output.chance(), random);
    }

    /** Number of units from {@code count} units with each a chance. */
    static int roll(int count, double chance, RandomGenerator random) {
        if (chance >= 1.0) {
            return count;
        }
        int result = 0;
        for (int i = 0; i < count; i++) {
            if (random.nextDouble() < chance) {
                result++;
            }
        }
        return result;
    }

    /** The population eats. Returns the share of the need that it ate: 1 for a population with no need. */
    private static double eat(EconomyState state, ItemCatalog catalog, EconomySettings settings,
            Map<String, Integer> consumed) {
        int need = state.population() * settings.foodPerInhabitant();
        if (need <= 0) {
            return 1.0;
        }
        Stock stock = state.stock();
        List<String> foods = new ArrayList<>();
        for (String item : stock.asMap().keySet()) {
            if (catalog.info(item).isFood()) {
                foods.add(item);
            }
        }
        foods.sort(Comparator.comparingInt((String item) -> stock.count(item)).reversed()
                .thenComparing(Comparator.naturalOrder()));
        int eaten = 0;
        for (String item : foods) {
            if (eaten >= need) {
                break;
            }
            int value = catalog.info(item).food();
            int units = Math.min(stock.count(item), (need - eaten + value - 1) / value);
            stock.remove(item, units);
            consumed.merge(item, units, Integer::sum);
            eaten += units * value;
        }
        return Math.min(1.0, (double) eaten / need);
    }

    /** Each stock loses a share of its quantity, rounded to the nearest unit. */
    static void decay(Stock stock, double rate) {
        if (rate <= 0.0) {
            return;
        }
        for (Map.Entry<String, Integer> entry : new TreeMap<>(stock.asMap()).entrySet()) {
            int loss = (int) Math.round(entry.getValue() * rate);
            stock.remove(entry.getKey(), loss);
        }
    }

    private static void updatePopulation(EconomyState state, double foodCovered, int maxPopulation,
            EconomySettings settings) {
        int population = state.population();
        if (foodCovered >= FED && population < maxPopulation) {
            int growth = Math.max(1, (int) Math.round(population * settings.growthRate()));
            state.setPopulation(Math.min(maxPopulation, population + growth));
        } else if (foodCovered < HUNGRY && population > 1) {
            state.setPopulation(population - 1);
        } else if (population > maxPopulation) {
            state.setPopulation(maxPopulation);
        }
    }

    /**
     * Gives the start stock of a settlement: the outputs of its jobs for some cycles, and food for the population
     * for some cycles. A settlement with a higher start level gets more cycles.
     *
     * @param state         the economic state (population and jobs already set)
     * @param jobs          the jobs of the settlement
     * @param cycles        the number of cycles of production
     * @param stockCapacity the maximum quantity of each item
     * @param catalog       the item values
     * @param settings      the economy settings
     */
    public static void seedStock(EconomyState state, List<Job> jobs, int cycles, int stockCapacity,
            ItemCatalog catalog, EconomySettings settings) {
        Stock stock = state.stock();
        for (Job job : jobs) {
            for (Recipe.Output output : job.recipe().outputs()) {
                int count = (int) Math.round(output.count() * output.chance() * cycles);
                stock.add(output.item(), count, stockCapacity);
            }
            for (Recipe.Input input : job.recipe().inputs()) {
                stock.add(input.item(), input.count() * 2, stockCapacity);
            }
        }
        // Food for some cycles, from the food items of the jobs.
        int foodNeed = state.population() * settings.foodPerInhabitant() * cycles;
        int foodInStock = 0;
        for (Map.Entry<String, Integer> entry : stock.asMap().entrySet()) {
            foodInStock += entry.getValue() * catalog.info(entry.getKey()).food();
        }
        if (foodInStock >= foodNeed) {
            return;
        }
        for (Job job : jobs) {
            for (Recipe.Output output : job.recipe().outputs()) {
                ItemInfo info = catalog.info(output.item());
                if (info.isFood() && foodInStock < foodNeed) {
                    int units = (foodNeed - foodInStock + info.food() - 1) / info.food();
                    foodInStock += stock.add(output.item(), units, stockCapacity) * info.food();
                }
            }
        }
    }
}
