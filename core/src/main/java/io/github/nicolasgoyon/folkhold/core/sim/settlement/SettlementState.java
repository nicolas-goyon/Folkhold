package io.github.nicolasgoyon.folkhold.core.sim.settlement;

import java.util.Locale;

/** State of a settlement in the abstract simulation. */
public enum SettlementState {
    /** The settlement lives: it produces, grows and claims territory. */
    ACTIVE,
    /** The settlement is destroyed. The simulation ignores it. Its blocks stay in the world. */
    RUINS;

    /** The name in the saved data. */
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The state with this saved name.
     *
     * @param name the saved name
     * @return the state, or {@link #ACTIVE} if the name is not known
     */
    public static SettlementState fromSerializedName(String name) {
        for (SettlementState state : values()) {
            if (state.serializedName().equals(name)) {
                return state;
            }
        }
        return ACTIVE;
    }
}
