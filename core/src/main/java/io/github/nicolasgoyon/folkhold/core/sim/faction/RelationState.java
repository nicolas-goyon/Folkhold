package io.github.nicolasgoyon.folkhold.core.sim.faction;

/**
 * State of the relation between two factions, from the relation value (-100 to +100).
 */
public enum RelationState {
    /** 50 to 100. */
    ALLIANCE,
    /** 0 to 49. */
    PEACE,
    /** -50 to -1. */
    TENSION,
    /** -100 to -51. */
    WAR;

    /**
     * Gives the state of a relation value.
     *
     * @param value the relation value, from -100 to +100
     * @return the state
     */
    public static RelationState of(int value) {
        if (value >= 50) {
            return ALLIANCE;
        }
        if (value >= 0) {
            return PEACE;
        }
        if (value >= -50) {
            return TENSION;
        }
        return WAR;
    }
}
