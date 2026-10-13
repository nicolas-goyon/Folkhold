package io.github.nicolasgoyon.folkhold.core.sim.trade;

import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Trade between settlements: surpluses, needs and a greedy match between sellers and buyers.
 *
 * <ul>
 *   <li>A <b>surplus</b> is an item with a stock above {@code 1.5 × S_target}. The settlement sells the quantity
 *       above its target.</li>
 *   <li>A <b>need</b> is an item that the settlement uses (an input of its jobs, or food) with a stock below
 *       {@code 0.5 × S_target}. The settlement buys the quantity below its target.</li>
 *   <li>Each candidate is a seller, a buyer and an item. Its score is the value of the quantity, divided by a
 *       factor of the distance. The match takes the candidates with the highest score first.</li>
 *   <li>All items from one seller to one buyer go into one order (one caravan), up to the caravan capacity.</li>
 * </ul>
 */
public final class TradeMatcher {
    /** A stock above this share of the target is a surplus. */
    public static final double SURPLUS = 1.5;
    /** A stock below this share of the target is a need. */
    public static final double NEED = 0.5;

    /**
     * A settlement in the market.
     *
     * @param id        the id of the settlement
     * @param factionId the id of its faction
     * @param x         block X
     * @param z         block Z
     * @param surpluses the quantities that it sells, by item
     * @param needs     the quantities that it buys, by item
     */
    public record Participant(String id, String factionId, int x, int z, Map<String, Integer> surpluses,
            Map<String, Integer> needs) {
    }

    /**
     * Goods from one seller to one buyer.
     *
     * @param sellerId the id of the seller
     * @param buyerId  the id of the buyer
     * @param items    the quantities, by item
     * @param value    the value of the goods in emeralds, at the prices of the seller
     */
    public record Order(String sellerId, String buyerId, Map<String, Integer> items, double value) {
        /** The total quantity of items. */
        public int total() {
            return items.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    /** Tells if a seller and a buyer can trade (factions, route, caravans on the way). */
    @FunctionalInterface
    public interface PairFilter {
        boolean canTrade(Participant seller, Participant buyer);
    }

    /** Gives the unit price of an item at a seller. */
    @FunctionalInterface
    public interface Pricing {
        double unitPrice(String sellerId, String item);
    }

    /**
     * Settings of the match.
     *
     * @param maxOrders       the maximum number of orders
     * @param caravanCapacity the maximum quantity of items in one order
     * @param distanceWeight  the distance in blocks that divides the score by 2
     */
    public record Settings(int maxOrders, int caravanCapacity, double distanceWeight) {
        public Settings {
            if (maxOrders < 0 || caravanCapacity < 1 || distanceWeight <= 0) {
                throw new IllegalArgumentException("Invalid trade settings");
            }
        }
    }

    private record Candidate(Participant seller, Participant buyer, String item, double score) {
    }

    private TradeMatcher() {
    }

    /**
     * The surpluses of a settlement.
     *
     * @param stock      the stock
     * @param population the population
     * @param catalog    the item values
     * @return the quantity above the target, for each item with a surplus
     */
    public static Map<String, Integer> surpluses(Stock stock, int population, ItemCatalog catalog) {
        Map<String, Integer> result = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : stock.asMap().entrySet()) {
            int target = Prices.target(catalog.info(entry.getKey()), population);
            if (entry.getValue() > SURPLUS * target) {
                result.put(entry.getKey(), entry.getValue() - target);
            }
        }
        return result;
    }

    /**
     * The needs of a settlement.
     *
     * @param stock      the stock
     * @param population the population
     * @param catalog    the item values
     * @param usedItems  the items that the settlement uses
     * @return the quantity below the target, for each used item with a need
     */
    public static Map<String, Integer> needs(Stock stock, int population, ItemCatalog catalog,
            Collection<String> usedItems) {
        Map<String, Integer> result = new TreeMap<>();
        for (String item : usedItems) {
            ItemInfo info = catalog.info(item);
            int target = Prices.target(info, population);
            int count = stock.count(item);
            if (count < NEED * target) {
                result.put(item, target - count);
            }
        }
        return result;
    }

    /**
     * Matches the sellers and the buyers.
     *
     * @param participants the settlements
     * @param filter       the pairs that can trade
     * @param pricing      the prices of the sellers
     * @param settings     the settings
     * @return the orders, in the order of their first item
     */
    public static List<Order> match(List<Participant> participants, PairFilter filter, Pricing pricing,
            Settings settings) {
        List<Candidate> candidates = new ArrayList<>();
        Map<String, Boolean> pairs = new HashMap<>();
        for (Participant seller : participants) {
            for (Map.Entry<String, Integer> surplus : seller.surpluses().entrySet()) {
                String item = surplus.getKey();
                for (Participant buyer : participants) {
                    Integer need = buyer.needs().get(item);
                    if (buyer == seller || need == null || need <= 0) {
                        continue;
                    }
                    boolean allowed = pairs.computeIfAbsent(seller.id() + "|" + buyer.id(),
                            key -> filter.canTrade(seller, buyer));
                    if (!allowed) {
                        continue;
                    }
                    double quantity = Math.min(surplus.getValue(), need);
                    double distance = Math.hypot(buyer.x() - seller.x(), buyer.z() - seller.z());
                    double score = quantity * pricing.unitPrice(seller.id(), item)
                            / (1.0 + distance / settings.distanceWeight());
                    candidates.add(new Candidate(seller, buyer, item, score));
                }
            }
        }
        candidates.sort(Comparator.comparingDouble(Candidate::score).reversed()
                .thenComparing(candidate -> candidate.seller().id())
                .thenComparing(candidate -> candidate.buyer().id())
                .thenComparing(Candidate::item));

        Map<String, Map<String, Integer>> remainingSurplus = new HashMap<>();
        Map<String, Map<String, Integer>> remainingNeed = new HashMap<>();
        for (Participant participant : participants) {
            remainingSurplus.put(participant.id(), new HashMap<>(participant.surpluses()));
            remainingNeed.put(participant.id(), new HashMap<>(participant.needs()));
        }
        Map<String, Map<String, Integer>> orderItems = new LinkedHashMap<>();
        Map<String, Candidate> orderPairs = new HashMap<>();
        for (Candidate candidate : candidates) {
            String key = candidate.seller().id() + "|" + candidate.buyer().id();
            Map<String, Integer> items = orderItems.get(key);
            if (items == null && orderItems.size() >= settings.maxOrders()) {
                continue;
            }
            int loaded = items == null ? 0 : items.values().stream().mapToInt(Integer::intValue).sum();
            Map<String, Integer> surplus = remainingSurplus.get(candidate.seller().id());
            Map<String, Integer> need = remainingNeed.get(candidate.buyer().id());
            int quantity = Math.min(Math.min(surplus.getOrDefault(candidate.item(), 0),
                    need.getOrDefault(candidate.item(), 0)), settings.caravanCapacity() - loaded);
            if (quantity <= 0) {
                continue;
            }
            surplus.merge(candidate.item(), -quantity, Integer::sum);
            need.merge(candidate.item(), -quantity, Integer::sum);
            orderItems.computeIfAbsent(key, k -> new TreeMap<>()).merge(candidate.item(), quantity, Integer::sum);
            orderPairs.putIfAbsent(key, candidate);
        }

        List<Order> orders = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> entry : orderItems.entrySet()) {
            Candidate pair = orderPairs.get(entry.getKey());
            double value = 0.0;
            for (Map.Entry<String, Integer> item : entry.getValue().entrySet()) {
                value += item.getValue() * pricing.unitPrice(pair.seller().id(), item.getKey());
            }
            orders.add(new Order(pair.seller().id(), pair.buyer().id(),
                    Collections.unmodifiableMap(entry.getValue()), value));
        }
        return orders;
    }
}
