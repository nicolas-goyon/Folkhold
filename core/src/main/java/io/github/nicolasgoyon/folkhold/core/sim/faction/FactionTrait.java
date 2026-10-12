package io.github.nicolasgoyon.folkhold.core.sim.faction;

/**
 * Trait of a faction. Traits change the behavior of the faction in the abstract simulation (J3).
 */
public enum FactionTrait {
    /** Founds new hamlets faster. */
    EXPANSIONIST,
    /** Trades more and gives better prices. */
    MERCHANT,
    /** Trades less and defends more. */
    ISOLATIONIST
}
