package io.github.nicolasgoyon.folkhold.balance.anvil;

/** The rule that gives the level of an enchantment when the anvil combines two levels. */
public enum MergeRule {
    /** Two equal levels give the level + 1. Two different levels give the higher level. */
    VANILLA,
    /** The two levels add together. */
    SUM;

    /**
     * The level of the result.
     *
     * @param left  the level on the left item, 0 if the left item does not have the enchantment
     * @param right the level on the right item
     * @return the level of the result, with no cap
     */
    public int merge(int left, int right) {
        return switch (this) {
            case VANILLA -> left == right ? left + 1 : Math.max(left, right);
            case SUM -> left + right;
        };
    }
}
