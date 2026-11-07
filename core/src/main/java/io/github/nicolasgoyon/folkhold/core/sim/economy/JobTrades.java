package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

/**
 * The trades of a worker: the goods of its jobs, from the stock of the village.
 *
 * <ul>
 *   <li>The worker sells the outputs of its jobs that the stock has. The most valuable stock comes first.</li>
 *   <li>The worker buys the inputs of its jobs that the village needs: a stock below half of its target. The largest
 *       need comes first.</li>
 *   <li>The offer count of the level divides between the traders of the village (see {@link #share}). Of the offers
 *       of a worker, two thirds are sell offers, and the rest are buy offers.</li>
 * </ul>
 */
public final class JobTrades {

    private JobTrades() {
    }

    /**
     * The items that a worker can sell and buy, in the order of the offers, and the number of offers of each kind.
     * An item can give no offer (for example, the treasury cannot pay it): then the next item of the list gives the
     * offer.
     *
     * @param sells     the items to sell
     * @param sellSlots the maximum number of sell offers
     * @param buys      the items to buy
     * @param buySlots  the maximum number of buy offers
     */
    public record Plan(List<String> sells, int sellSlots, List<String> buys, int buySlots) {
    }

    /**
     * The number of offers of one trader: the offer count of the level, divided between the traders and rounded up.
     *
     * @param levelOffers the offer count of the level
     * @param traders     the number of traders of the village
     * @return the number of offers, at least 1 when the village has traders and offers
     */
    public static int share(int levelOffers, int traders) {
        if (traders <= 0 || levelOffers <= 0) {
            return 0;
        }
        return (levelOffers + traders - 1) / traders;
    }

    /**
     * The trades of a worker.
     *
     * @param jobs       the active jobs of the worker
     * @param stock      the stock of the village, by item id
     * @param catalog    the item values
     * @param population the population of the village
     * @param offers     the number of offers of the worker (see {@link #share})
     * @param tradable   true for the items that can be in an offer (a known item, not the currency)
     * @return the plan of the offers
     */
    public static Plan plan(Collection<Job> jobs, Map<String, Integer> stock, ItemCatalog catalog, int population,
            int offers, Predicate<String> tradable) {
        Set<String> outputs = new LinkedHashSet<>();
        Set<String> inputs = new LinkedHashSet<>();
        for (Job job : jobs) {
            job.recipe().outputs().forEach(output -> outputs.add(output.item()));
            job.recipe().inputs().forEach(input -> inputs.add(input.item()));
        }
        List<String> sells = outputs.stream()
                .filter(item -> tradable.test(item) && stock.getOrDefault(item, 0) > 0)
                .sorted(Comparator.comparingDouble((String item) ->
                        -catalog.info(item).price() * stock.getOrDefault(item, 0)).thenComparing(item -> item))
                .toList();
        List<String> buys = inputs.stream()
                .filter(item -> tradable.test(item)
                        && stock.getOrDefault(item, 0) < Prices.target(catalog.info(item), population) / 2)
                .sorted(Comparator.comparingInt((String item) ->
                        stock.getOrDefault(item, 0) - Prices.target(catalog.info(item), population))
                        .thenComparing(item -> item))
                .toList();
        int sellSlots = offers <= 0 ? 0 : Math.max(1, (offers * 2 + 2) / 3);
        int buySlots = Math.max(0, offers - sellSlots);
        return new Plan(sells, sellSlots, buys, buySlots);
    }
}
