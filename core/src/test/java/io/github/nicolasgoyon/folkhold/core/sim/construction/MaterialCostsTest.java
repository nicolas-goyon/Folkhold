package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.construction.MaterialCosts.Input;
import io.github.nicolasgoyon.folkhold.core.sim.construction.MaterialCosts.Recipe;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class MaterialCostsTest {
    private static final double EPSILON = 1.0E-9;
    private static final Map<String, Double> PRICES = Map.of("log", 0.1, "birch_log", 0.2, "raw_iron", 0.3,
            "cobblestone", 0.02);

    /** The recipes of the tests, as in the game: planks, stairs, iron, and a mined block with no recipe. */
    private static Map<String, List<Recipe>> recipes() {
        Map<String, List<Recipe>> recipes = new HashMap<>();
        recipes.put("planks", List.of(new Recipe(List.of(new Input(List.of("log"), 1)), 4),
                new Recipe(List.of(new Input(List.of("birch_log"), 1)), 4)));
        recipes.put("stairs", List.of(new Recipe(List.of(new Input(List.of("planks"), 6)), 4)));
        recipes.put("chest", List.of(new Recipe(List.of(new Input(List.of("planks", "calcite"), 8)), 1)));
        recipes.put("iron_ingot", List.of(new Recipe(List.of(new Input(List.of("raw_iron"), 1)), 1),
                new Recipe(List.of(new Input(List.of("iron_block"), 1)), 9)));
        recipes.put("iron_block", List.of(new Recipe(List.of(new Input(List.of("iron_ingot"), 9)), 1)));
        recipes.put("lantern", List.of(new Recipe(List.of(new Input(List.of("iron_nugget"), 8),
                new Input(List.of("torch"), 1)), 1)));
        recipes.put("iron_nugget", List.of(new Recipe(List.of(new Input(List.of("iron_ingot"), 1)), 9)));
        recipes.put("torch", List.of(new Recipe(List.of(new Input(List.of("coal"), 1),
                new Input(List.of("stick"), 1)), 4)));
        return recipes;
    }

    private static MaterialCosts costs() {
        Map<String, List<Recipe>> recipes = recipes();
        return new MaterialCosts(item -> recipes.getOrDefault(item, List.of()), PRICES::containsKey,
                item -> PRICES.getOrDefault(item, 1.0));
    }

    private static double quantity(MaterialCosts costs, String item, String baseItem) {
        return costs.cost(item).orElseThrow().getOrDefault(baseItem, 0.0);
    }

    /** A base item costs itself. Planks cost 1/4 of a log: the cheapest log. */
    @Test
    void baseItemsAndTheCheapestRecipe() {
        MaterialCosts costs = costs();

        assertEquals(Map.of("log", 1.0), costs.cost("log").orElseThrow());
        assertEquals(Map.of("log", 0.25), costs.cost("planks").orElseThrow(), "The cheaper log counts");
    }

    /** The costs go through several recipes: 1 stairs costs 6/4 planks, so 3/8 of a log. */
    @Test
    void costsGoThroughSeveralRecipes() {
        MaterialCosts costs = costs();

        assertEquals(0.375, quantity(costs, "stairs", "log"), EPSILON);
        assertEquals(2.0, quantity(costs, "chest", "log"), EPSILON, "The input takes the option with a cost");
        assertEquals(0.375 * 0.1, costs.price(costs.cost("stairs").orElseThrow()), EPSILON);
    }

    /** A recipe that needs the item that it makes does not count: an iron block costs 9 raw iron. */
    @Test
    void cyclesDoNotCount() {
        MaterialCosts costs = costs();

        assertEquals(Map.of("raw_iron", 1.0), costs.cost("iron_ingot").orElseThrow());
        assertEquals(9.0, quantity(costs, "iron_block", "raw_iron"), EPSILON);
        assertEquals(1.0 / 9.0, quantity(costs, "iron_nugget", "raw_iron"), EPSILON);
    }

    /** An item with no recipe from base items has no cost: a mined block, or a recipe with a missing input. */
    @Test
    void itemWithNoRecipeFromBaseItemsHasNoCost() {
        MaterialCosts costs = costs();

        assertTrue(costs.cost("calcite").isEmpty(), "A mined block has no cost");
        assertTrue(costs.cost("torch").isEmpty(), "Coal and sticks are not base items here");
        assertTrue(costs.cost("lantern").isEmpty(), "The lantern needs a torch");
    }

    /** The search stops at the depth limit. */
    @Test
    void searchStopsAtTheDepthLimit() {
        Map<String, List<Recipe>> chain = new HashMap<>();
        for (int i = 0; i < MaterialCosts.MAX_DEPTH + 1; i++) {
            chain.put("item" + i, List.of(new Recipe(List.of(new Input(List.of("item" + (i + 1)), 1)), 1)));
        }
        MaterialCosts deep = new MaterialCosts(item -> chain.getOrDefault(item, List.of()),
                Set.of("item" + (MaterialCosts.MAX_DEPTH + 1))::contains, item -> 1.0);
        MaterialCosts near = new MaterialCosts(item -> chain.getOrDefault(item, List.of()),
                Set.of("item" + MaterialCosts.MAX_DEPTH)::contains, item -> 1.0);

        assertTrue(deep.cost("item0").isEmpty(), "A base item after the depth limit is not found");
        assertTrue(near.cost("item0").isPresent(), "A base item at the depth limit is found");
    }

    @Test
    void recipesMustBeValid() {
        assertThrows(IllegalArgumentException.class, () -> new Recipe(List.of(), 1));
        assertThrows(IllegalArgumentException.class, () -> new Input(List.of("log"), 0));
        assertThrows(IllegalArgumentException.class, () -> new Input(List.of(), 1));
    }
}
