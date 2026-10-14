package io.github.nicolasgoyon.folkhold.core.sim.settlement;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Locale;
import java.util.Optional;

/** State of a settlement in the abstract simulation. */
public enum SettlementState implements SerializedName {
    /** The settlement lives: it produces, grows and claims territory. */
    ACTIVE,
    /** The settlement is destroyed. The simulation ignores it. Its blocks stay in the world. */
    RUINS;

    /** The name in the saved data. */
    @Override
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * The state with a serialized name.
     *
     * @param name the name in the saved data
     * @return the state, or empty for an unknown name
     */
    public static Optional<SettlementState> fromSerializedName(String name) {
        return SerializedName.lookup(SettlementState.class, name);
    }
}
