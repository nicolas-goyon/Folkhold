package io.github.nicolasgoyon.folkhold.world.village;

import java.util.Locale;

/**
 * Alert state of a village. All units of the village share it.
 */
public enum AlertState {
    /** No threat. Units patrol. */
    CALM,
    /** A unit of the village was attacked or sees a threat. */
    ALERT,
    /** The village is under attack. The captain sets this state. */
    ASSAULT;

    /** Name in the save data. */
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
     * @return the state, or {@link #CALM} if the name is unknown
     */
    public static AlertState fromSerializedName(String name) {
        for (AlertState state : values()) {
            if (state.serializedName().equals(name)) {
                return state;
            }
        }
        return CALM;
    }
}
