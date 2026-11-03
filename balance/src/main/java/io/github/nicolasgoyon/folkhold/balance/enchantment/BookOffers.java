package io.github.nicolasgoyon.folkhold.balance.enchantment;

/**
 * The vanilla rules of an enchanted book offer of a librarian ({@code VillagerTrades.EnchantBookForEmeralds}).
 */
public final class BookOffers {
    /** The largest price of a book, in emeralds. */
    public static final int MAX_PRICE = 64;

    private BookOffers() {
    }

    /**
     * The number of values of the random part of the price: the random part is from 0 to this value - 1.
     *
     * @param level the level of the enchantment
     * @return the number of values
     */
    public static int priceRollBound(int level) {
        return 5 + 10 * level;
    }

    /**
     * The price of a book: {@code 2 + roll + 3 × level} emeralds, two times this value for an enchantment of the tag
     * {@code #minecraft:double_trade_price}, and {@link #MAX_PRICE} at most.
     *
     * @param level   the level of the enchantment
     * @param roll    the random part, from 0 to {@link #priceRollBound(int)} - 1
     * @param doubled true for an enchantment of the tag {@code #minecraft:double_trade_price}
     * @return the price in emeralds
     */
    public static int price(int level, int roll, boolean doubled) {
        int price = 2 + roll + 3 * level;
        if (doubled) {
            price *= 2;
        }
        return Math.min(price, MAX_PRICE);
    }

    /**
     * The level of an enchantment that replaces a blocked enchantment: the level of the blocked enchantment, in the
     * range of levels of the new enchantment.
     *
     * @param level    the level of the blocked enchantment
     * @param minLevel the smallest level of the new enchantment
     * @param maxLevel the largest level of the new enchantment
     * @return the level of the new enchantment
     */
    public static int replacementLevel(int level, int minLevel, int maxLevel) {
        return Math.max(minLevel, Math.min(maxLevel, level));
    }
}
