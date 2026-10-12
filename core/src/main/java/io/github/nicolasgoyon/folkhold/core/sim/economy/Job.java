package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.List;

/**
 * An economic activity of a settlement.
 *
 * @param id       the job id, for example {@code folkhold_world:farmer}
 * @param recipe   the recipe of one cycle
 * @param minLevel the minimum level of the settlement
 * @param weight   the weight of the job in the random selection (1 or more)
 * @param biomes   the biomes where the job can exist: biome ids or biome tags ({@code #namespace:path}). An
 *                 empty list means all biomes.
 */
public record Job(String id, Recipe recipe, int minLevel, int weight, List<String> biomes) {

    public Job {
        if (id == null || id.isBlank() || minLevel < 1 || weight < 1) {
            throw new IllegalArgumentException("Invalid job: " + id + ", level " + minLevel + ", weight " + weight);
        }
        biomes = List.copyOf(biomes);
    }

    /** True if one output of the job is food. */
    public boolean producesFood(ItemCatalog catalog) {
        return recipe.outputs().stream().anyMatch(output -> catalog.info(output.item()).isFood());
    }
}
