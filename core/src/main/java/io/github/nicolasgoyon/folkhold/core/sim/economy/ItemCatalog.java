package io.github.nicolasgoyon.folkhold.core.sim.economy;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Economic values of the items. An item that is not in the catalog has the default values: price 1, no food,
 * target 32.
 */
public final class ItemCatalog {
    public static final ItemCatalog EMPTY = new ItemCatalog(Map.of());

    private final Map<String, ItemInfo> items;

    public ItemCatalog(Map<String, ItemInfo> items) {
        this.items = Collections.unmodifiableMap(new LinkedHashMap<>(items));
    }

    /** The values of an item, or the default values. */
    public ItemInfo info(String id) {
        ItemInfo info = items.get(id);
        return info != null ? info : new ItemInfo(id, 1.0, 0, 32);
    }

    public boolean contains(String id) {
        return items.containsKey(id);
    }

    public Collection<ItemInfo> all() {
        return items.values();
    }
}
