package io.github.nicolasgoyon.folkhold.balance.enchantment;

/** The groups of loot tables that the filter of blocked enchantments can change. */
public enum LootScope {
    /** The vanilla fishing tables: {@code minecraft:gameplay/fishing} and its sub-tables. */
    FISHING,
    /** The chest tables: each table with a path that starts with {@code chests/}, in all namespaces. */
    CHEST,
    /** All other tables. */
    OTHER;

    private static final String FISHING_PATH = "gameplay/fishing";

    /**
     * The group of a loot table.
     *
     * <p>A loot modifier gets the id of the table that the game rolls, not the id of a nested table. Fishing rolls
     * {@code minecraft:gameplay/fishing}, and this table rolls {@code gameplay/fishing/treasure} as a nested table.
     *
     * @param namespace the namespace of the id of the loot table
     * @param path      the path of the id of the loot table
     * @return the group
     */
    public static LootScope of(String namespace, String path) {
        if ("minecraft".equals(namespace)
                && (path.equals(FISHING_PATH) || path.startsWith(FISHING_PATH + "/"))) {
            return FISHING;
        }
        if (path.startsWith("chests/")) {
            return CHEST;
        }
        return OTHER;
    }
}
