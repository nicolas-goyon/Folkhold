package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Locale;
import java.util.Optional;

/**
 * Alert state of a village. All units of the village share it.
 */
public enum AlertState implements SerializedName {
    /** No threat. Units patrol. */
    CALM,
    /** A unit of the village was attacked or sees a threat. */
    ALERT,
    /** The village is under attack. The captain sets this state. */
    ASSAULT;

    /** The index of the state in the network payloads. */
    @SuppressWarnings("EnumOrdinal") // The index is the wire format of the state.
    public int index() {
        return ordinal();
    }

    /**
     * Gives the state with an index from {@link #index()}.
     *
     * @param index the index; an index out of range wraps around
     * @return the state
     */
    public static AlertState byIndex(int index) {
        AlertState[] states = values();
        return states[Math.floorMod(index, states.length)];
    }

    /** Name in the save data. */
    @Override
    public String serializedName() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Translation key of the state name, for example {@code folkhold_world.alert_state.calm}. */
    public String translationKey() {
        return "folkhold_world.alert_state." + serializedName();
    }

    /**
     * Reads a state from its serialized name.
     *
     * @param name the serialized name
     * @return the state, or empty for an unknown name
     */
    public static Optional<AlertState> fromSerializedName(String name) {
        return SerializedName.lookup(AlertState.class, name);
    }
}
