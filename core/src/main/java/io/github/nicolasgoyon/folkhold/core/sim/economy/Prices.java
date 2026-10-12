package io.github.nicolasgoyon.folkhold.core.sim.economy;

/**
 * Prices of a settlement, from its stock.
 *
 * <p>{@code P = P_base × clamp((S_target / max(S, 1))^ε, min, max)}. A surplus lowers the price. A shortage
 * raises the price. The settlement sells at {@code P × sellMarkup} and buys at {@code P × buyMarkdown}.
 */
public final class Prices {

    private Prices() {
    }

    /**
     * The stock target of an item. It grows with the population: the item target is for 10 inhabitants.
     *
     * @param info       the item
     * @param population the population of the settlement
     * @return the target (1 or more)
     */
    public static int target(ItemInfo info, int population) {
        double scale = Math.max(1, population) / 10.0;
        return (int) Math.max(1, Math.round(info.target() * scale));
    }

    /**
     * The price of an item in a settlement.
     *
     * @param info       the item
     * @param stock      the quantity in stock
     * @param population the population of the settlement
     * @param settings   the economy settings
     * @return the price in emeralds
     */
    public static double price(ItemInfo info, int stock, int population, EconomySettings settings) {
        double ratio = (double) target(info, population) / Math.max(stock, 1);
        double factor = Math.pow(ratio, settings.elasticity());
        factor = Math.max(settings.minPriceFactor(), Math.min(settings.maxPriceFactor(), factor));
        return info.price() * factor;
    }

    /** The price when the settlement sells to the player. */
    public static double sellPrice(ItemInfo info, int stock, int population, EconomySettings settings) {
        return price(info, stock, population, settings) * settings.sellMarkup();
    }

    /** The price when the settlement buys from the player. */
    public static double buyPrice(ItemInfo info, int stock, int population, EconomySettings settings) {
        return price(info, stock, population, settings) * settings.buyMarkdown();
    }
}
