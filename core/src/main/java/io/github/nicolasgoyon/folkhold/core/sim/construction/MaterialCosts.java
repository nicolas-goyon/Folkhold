package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

/**
 * The cost of an item in base items, through the recipes that make it. The base items are the items of the economy
 * of the villages. Example: 6 oak planks make 4 oak stairs, and 1 oak log makes 4 oak planks. Thus 1 oak stairs costs
 * 3/8 of an oak log.
 *
 * <ul>
 *   <li>A base item costs itself.</li>
 *   <li>The cost of a recipe is the sum of the costs of its inputs, divided by the number of items that it makes. An
 *       input that accepts several items takes the cheapest one.</li>
 *   <li>Of the recipes of an item, the cheapest one counts. The price of a cost is the sum of the prices of its base
 *       items.</li>
 *   <li>An item with no recipe from base items has no cost, for example a block that the players mine. A recipe that
 *       needs the item that it makes (a cycle) does not count.</li>
 *   <li>The search goes {@link #MAX_DEPTH} recipes deep at most.</li>
 * </ul>
 */
public final class MaterialCosts {
    /** The search goes this number of recipes deep at most. */
    public static final int MAX_DEPTH = 6;

    /**
     * A recipe: its inputs and the number of items that it makes.
     *
     * @param inputs the inputs
     * @param output the number of items that the recipe makes, 1 or more
     */
    public record Recipe(List<Input> inputs, int output) {
        public Recipe {
            inputs = List.copyOf(inputs);
            if (inputs.isEmpty() || output < 1) {
                throw new IllegalArgumentException("A recipe needs inputs and an output of 1 or more: " + output);
            }
        }
    }

    /**
     * An input of a recipe.
     *
     * @param options the items that the input accepts
     * @param count   the quantity of the input, 1 or more
     */
    public record Input(List<String> options, int count) {
        public Input {
            options = List.copyOf(options);
            if (options.isEmpty() || count < 1) {
                throw new IllegalArgumentException("An input needs items and a count of 1 or more: " + count);
            }
        }
    }

    /** The recipes of the items. */
    @FunctionalInterface
    public interface Recipes {
        /**
         * The recipes that make an item.
         *
         * @param item the item
         * @return the recipes, or an empty list
         */
        List<Recipe> of(String item);
    }

    /**
     * The result of a search.
     *
     * @param cost     the cost, or empty with no recipe from base items
     * @param complete false if a cycle or the depth limit stopped a part of the search
     */
    private record Result(Optional<Map<String, Double>> cost, boolean complete) {
    }

    private final Recipes recipes;
    private final Predicate<String> base;
    private final ToDoubleFunction<String> price;
    private final Map<String, Optional<Map<String, Double>>> known = new HashMap<>();

    /**
     * The costs of the items.
     *
     * @param recipes the recipes of the items
     * @param base    true for a base item
     * @param price   the price of one base item
     */
    public MaterialCosts(Recipes recipes, Predicate<String> base, ToDoubleFunction<String> price) {
        this.recipes = recipes;
        this.base = base;
        this.price = price;
    }

    /**
     * The cost of one item, in base items.
     *
     * @param item the item
     * @return the quantity of each base item, or empty when no recipe makes the item from base items
     */
    public Optional<Map<String, Double>> cost(String item) {
        return search(item, new HashSet<>(), 0).cost();
    }

    /**
     * The price of a cost: the sum of the prices of its base items.
     *
     * @param cost the quantity of each base item
     * @return the price
     */
    public double price(Map<String, Double> cost) {
        double total = 0.0;
        for (Map.Entry<String, Double> entry : cost.entrySet()) {
            total += entry.getValue() * price.applyAsDouble(entry.getKey());
        }
        return total;
    }

    private Result search(String item, Set<String> path, int depth) {
        Optional<Map<String, Double>> cached = known.get(item);
        if (cached != null) {
            return new Result(cached, true);
        }
        if (base.test(item)) {
            Optional<Map<String, Double>> itself = Optional.of(Map.of(item, 1.0));
            known.put(item, itself);
            return new Result(itself, true);
        }
        if (path.contains(item) || depth >= MAX_DEPTH) {
            return new Result(Optional.empty(), false);
        }
        path.add(item);
        boolean complete = true;
        Map<String, Double> best = null;
        double bestPrice = Double.MAX_VALUE;
        for (Recipe recipe : recipes.of(item)) {
            Map<String, Double> total = new HashMap<>();
            boolean possible = true;
            for (Input input : recipe.inputs()) {
                Map<String, Double> cheapest = null;
                double cheapestPrice = Double.MAX_VALUE;
                for (String option : input.options()) {
                    Result result = search(option, path, depth + 1);
                    complete &= result.complete();
                    if (result.cost().isPresent() && price(result.cost().get()) < cheapestPrice) {
                        cheapest = result.cost().get();
                        cheapestPrice = price(cheapest);
                    }
                }
                if (cheapest == null) {
                    possible = false;
                    break;
                }
                cheapest.forEach((baseItem, quantity) -> total.merge(baseItem, quantity * input.count(), Double::sum));
            }
            if (!possible) {
                continue;
            }
            total.replaceAll((baseItem, quantity) -> quantity / recipe.output());
            double totalPrice = price(total);
            if (best == null || totalPrice < bestPrice) {
                best = total;
                bestPrice = totalPrice;
            }
        }
        path.remove(item);
        Optional<Map<String, Double>> cost = best == null ? Optional.empty() : Optional.of(Map.copyOf(best));
        // A search that a cycle stopped can miss a recipe: only a cost that the search found is sure.
        if (complete || cost.isPresent()) {
            known.put(item, cost);
        }
        return new Result(cost, complete);
    }
}
