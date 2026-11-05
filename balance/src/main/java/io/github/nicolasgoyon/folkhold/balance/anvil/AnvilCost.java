package io.github.nicolasgoyon.folkhold.balance.anvil;

/**
 * The cost formulas of the anvil, in levels. There is no prior work penalty: the cost of an operation depends only on
 * the items.
 *
 * <ul>
 *   <li>Enchantment cost = {@code operationCost + ceil(k × Σ w × (2^b − 2^a))}: a is the level on the left item, b is
 *       the level of the result, w is the weight of the enchantment. Level 0 (no enchantment) has the value 0, not
 *       2⁰. Each level costs double the level before it, as the vanilla prior work penalty. A level built in steps is
 *       never cheaper than the same level in one operation. A cost above {@link #MAX_COST} becomes
 *       {@code MAX_COST + 1}: the anvil then gives no result.</li>
 *   <li>Full repair cost = {@code base + ceil(r × Σ w × level)} for the enchantments of the item that are not
 *       curses. A partial repair costs {@code ceil(full × units / 4)}.</li>
 * </ul>
 */
public final class AnvilCost {
    /** The number of material units for a full repair: each unit repairs 25 % of the maximum durability. */
    public static final int UNITS_FOR_FULL_REPAIR = 4;
    /** The largest cost: the menu sends the cost to the client as a short. */
    public static final int MAX_COST = Short.MAX_VALUE;

    private AnvilCost() {
    }

    /**
     * The value of a level in the enchantment cost.
     *
     * @param level the level of the enchantment
     * @return 2^level, or 0 when the level is 0 or less (no enchantment). The value is exact up to level 1023.
     */
    public static double levelValue(int level) {
        return level <= 0 ? 0.0 : Math.scalb(1.0, level);
    }

    /**
     * The exponential part of the enchantment cost for one enchantment, before the factor k. The result is a double:
     * a long overflows above level 62, and the game stores levels up to 255.
     *
     * @param weight the weight of the enchantment
     * @param from   the level on the left item (0 if none)
     * @param to     the level of the result
     * @return {@code weight × (2^to − 2^from)}, or 0 when the level does not increase
     */
    public static double levelIncrease(int weight, int from, int to) {
        if (to <= from) {
            return 0.0;
        }
        return weight * (levelValue(to) - levelValue(from));
    }

    /**
     * The enchantment cost.
     *
     * @param operationCost the fixed cost of an enchantment operation
     * @param factor        the factor k
     * @param increase      the sum of {@link #levelIncrease(int, int, int)} for all the enchantments
     * @return the cost in levels, or {@code MAX_COST + 1} when the cost is above {@link #MAX_COST}
     */
    public static long enchantmentCost(int operationCost, double factor, double increase) {
        double cost = operationCost + Math.ceil(factor * increase);
        // The comparison is false for NaN: such a cost also gives no result.
        return cost <= MAX_COST ? (long) cost : MAX_COST + 1L;
    }

    /**
     * The cost of a full repair.
     *
     * @param base           the base cost of the repair material
     * @param factor         the factor r
     * @param weightedLevels the sum of {@code weight × level} for the enchantments that are not curses
     * @return the cost in levels
     */
    public static long fullRepairCost(int base, double factor, long weightedLevels) {
        return base + (long) Math.ceil(factor * weightedLevels);
    }

    /**
     * The cost of a repair with a number of material units.
     *
     * @param fullCost the cost of a full repair
     * @param units    the number of units, from 1 to {@link #UNITS_FOR_FULL_REPAIR}
     * @return {@code ceil(fullCost × units / 4)}
     */
    public static long repairCost(long fullCost, int units) {
        int used = Math.min(units, UNITS_FOR_FULL_REPAIR);
        return (fullCost * used + UNITS_FOR_FULL_REPAIR - 1) / UNITS_FOR_FULL_REPAIR;
    }

    /**
     * The number of material units that the vanilla anvil uses: each unit repairs a quarter of the maximum
     * durability, and the anvil stops when the item has no damage or when the material is used up.
     *
     * @param damage    the damage of the item
     * @param maxDamage the maximum damage of the item
     * @param available the number of material items
     * @return the number of units
     */
    public static int materialUnits(int damage, int maxDamage, int available) {
        int units = 0;
        int remaining = damage;
        int step = Math.min(remaining, maxDamage / UNITS_FOR_FULL_REPAIR);
        while (step > 0 && units < available) {
            remaining -= step;
            units++;
            step = Math.min(remaining, maxDamage / UNITS_FOR_FULL_REPAIR);
        }
        return units;
    }

    /**
     * The number of units that a repair with a second item counts: the repaired durability, in quarters of the
     * maximum durability, rounded up.
     *
     * @param repaired  the repaired durability
     * @param maxDamage the maximum damage of the item
     * @return the number of units, from 1 to {@link #UNITS_FOR_FULL_REPAIR}
     */
    public static int equivalentUnits(int repaired, int maxDamage) {
        long units = ((long) repaired * UNITS_FOR_FULL_REPAIR + maxDamage - 1) / maxDamage;
        return (int) Math.max(1, Math.min(UNITS_FOR_FULL_REPAIR, units));
    }
}
