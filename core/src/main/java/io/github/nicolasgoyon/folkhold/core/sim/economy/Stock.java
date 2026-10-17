package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

/**
 * Quantities of items in the stock of a settlement. A quantity is never below 0. An item with a quantity of 0 is
 * not in the stock.
 */
public final class Stock {
    private final Map<String, Integer> items = new TreeMap<>();

    public int count(String item) {
        return items.getOrDefault(item, 0);
    }

    /**
     * Adds items, up to the capacity of the item.
     *
     * @param item     the item id
     * @param count    the quantity to add (0 or more)
     * @param capacity the maximum quantity of this item
     * @return the quantity that the stock accepted
     */
    public int add(String item, int count, int capacity) {
        int current = count(item);
        int accepted = Math.max(0, Math.min(count, capacity - current));
        if (accepted > 0) {
            items.put(item, current + accepted);
        }
        return accepted;
    }

    /**
     * Removes items.
     *
     * @param item  the item id
     * @param count the quantity to remove (0 or more)
     * @return the quantity that the stock removed (less than {@code count} if the stock has less)
     */
    public int remove(String item, int count) {
        int current = count(item);
        int removed = Math.max(0, Math.min(count, current));
        set(item, current - removed);
        return removed;
    }

    /**
     * Sets the quantity of an item.
     *
     * @param item  the item id
     * @param count the quantity (a value below 0 gives 0)
     */
    public void set(String item, int count) {
        if (count <= 0) {
            items.remove(item);
        } else {
            items.put(item, count);
        }
    }

    /** All items with a quantity above 0, sorted by id. */
    public Map<String, Integer> asMap() {
        return Collections.unmodifiableMap(items);
    }

    /** Total quantity of all items. */
    public int total() {
        int total = 0;
        for (int count : items.values()) {
            total += count;
        }
        return total;
    }
}
