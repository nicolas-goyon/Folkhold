package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
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
 *   <li>After the inputs, the worker also buys the food that the village needs most: the food item with the largest
 *       need below its target, if the worker does not sell this item.</li>
 *   <li>The offer count of the level divides between the traders of the village (see {@link #share}). A worker has
 *       {@link #MIN_OFFERS} offers or more, and not more than the offer count of the level.</li>
 *   <li>Of the offers of a worker, two thirds are sell offers, and the rest are buy offers, with 1 buy offer or more.
 *       When a worker has fewer items to sell than sell offers, the free offers buy, and the reverse.</li>
 *   <li>The items of the stock that no worker sells go to the caravan merchant during its stay (see
 *       {@link #surplus}).</li>
 * </ul>
 */
public final class JobTrades {
    /** The minimum number of offers of a trader: 1 sell offer and 1 buy offer. */
    public static final int MIN_OFFERS = 2;

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
     * The number of offers of one trader. The offer count of the level divides between the traders: the first traders
     * get the remainder, so the sum of the parts is the offer count of the level. Each trader gets
     * {@link #MIN_OFFERS} offers or more, and not more than the offer count of the level.
     *
     * @param levelOffers the offer count of the level
     * @param traders     the number of traders of the village
     * @param index       the index of the trader (0 to {@code traders - 1})
     * @return the number of offers, or 0 when the village has no traders or no offers
     */
    public static int share(int levelOffers, int traders, int index) {
        if (traders <= 0 || levelOffers <= 0) {
            return 0;
        }
        int part = levelOffers / traders + (index < levelOffers % traders ? 1 : 0);
        return Math.min(levelOffers, Math.max(MIN_OFFERS, part));
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
        Comparator<String> byNeed = Comparator.comparingInt((String item) ->
                stock.getOrDefault(item, 0) - Prices.target(catalog.info(item), population))
                .thenComparing(item -> item);
        List<String> buys = new ArrayList<>(inputs.stream()
                .filter(item -> tradable.test(item)
                        && stock.getOrDefault(item, 0) < Prices.target(catalog.info(item), population) / 2)
                .sorted(byNeed)
                .toList());
        catalog.all().stream()
                .filter(info -> info.isFood() && tradable.test(info.id()) && !outputs.contains(info.id())
                        && !buys.contains(info.id())
                        && stock.getOrDefault(info.id(), 0) < Prices.target(info, population))
                .map(ItemInfo::id)
                .min(byNeed)
                .ifPresent(buys::add);
        if (offers <= 0) {
            return new Plan(sells, 0, List.copyOf(buys), 0);
        }
        int sellSlots = Math.max(1, (offers * 2 + 2) / 3);
        int buySlots = offers - sellSlots;
        if (buySlots == 0 && offers >= MIN_OFFERS) {
            sellSlots--;
            buySlots++;
        }
        if (sells.size() < sellSlots) {
            buySlots += sellSlots - sells.size();
            sellSlots = sells.size();
        } else if (buys.size() < buySlots) {
            sellSlots += buySlots - buys.size();
            buySlots = buys.size();
        }
        return new Plan(sells, sellSlots, List.copyOf(buys), buySlots);
    }

    /**
     * The surplus of the stock that no worker sells, with the quantity for sale. The caravan merchant sells it during
     * its stay at the village.
     *
     * <ul>
     *   <li>An output of a job of a worker is not in the surplus: the worker sells it.</li>
     *   <li>The village keeps its target of the inputs of its active jobs and of the food: only the quantity above the
     *       target is for sale.</li>
     *   <li>The other items are for sale with all their stock.</li>
     *   <li>The most valuable surplus comes first.</li>
     * </ul>
     *
     * @param sellers    the active jobs of the workers that trade
     * @param active     all the active jobs of the village
     * @param stock      the stock of the village, by item id
     * @param catalog    the item values
     * @param population the population of the village
     * @param tradable   true for the items that can be in an offer (a known item, not the currency)
     * @return the quantity for sale, by item id, sorted
     */
    public static Map<String, Integer> surplus(Collection<Job> sellers, Collection<Job> active,
            Map<String, Integer> stock, ItemCatalog catalog, int population, Predicate<String> tradable) {
        Set<String> outputs = new LinkedHashSet<>();
        for (Job job : sellers) {
            job.recipe().outputs().forEach(output -> outputs.add(output.item()));
        }
        Set<String> inputs = new LinkedHashSet<>();
        for (Job job : active) {
            job.recipe().inputs().forEach(input -> inputs.add(input.item()));
        }
        Map<String, Integer> result = new LinkedHashMap<>();
        stock.entrySet().stream()
                .filter(entry -> !outputs.contains(entry.getKey()) && tradable.test(entry.getKey()))
                .map(entry -> {
                    ItemInfo info = catalog.info(entry.getKey());
                    int reserve = inputs.contains(entry.getKey()) || info.isFood() ? Prices.target(info, population)
                            : 0;
                    return Map.entry(entry.getKey(), entry.getValue() - reserve);
                })
                .filter(entry -> entry.getValue() > 0)
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) ->
                        -catalog.info(entry.getKey()).price() * entry.getValue())
                        .thenComparing(Map.Entry::getKey))
                .forEachOrdered(entry -> result.put(entry.getKey(), entry.getValue()));
        return result;
    }
}
