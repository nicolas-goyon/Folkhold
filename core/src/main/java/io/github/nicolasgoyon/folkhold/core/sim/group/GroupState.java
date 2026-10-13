package io.github.nicolasgoyon.folkhold.core.sim.group;

/** The state of a mobile group. */
public enum GroupState {
    /** The group is at its origin. It leaves at its departure tick. */
    WAITING("waiting"),
    /** The group moves on its route. */
    TRAVELING("traveling"),
    /** The group stays at its position on the route. */
    STOPPED("stopped"),
    /** The group is at its destination. */
    ARRIVED("arrived"),
    /** The merchant of the group is dead. The cargo does not arrive. */
    LOST("lost");

    private final String serializedName;

    GroupState(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data. */
    public String serializedName() {
        return serializedName;
    }

    /**
     * The state with a name.
     *
     * @param name the name in the saved data
     * @return the state. An unknown name gives {@link #TRAVELING}.
     */
    public static GroupState fromSerializedName(String name) {
        for (GroupState state : values()) {
            if (state.serializedName.equals(name)) {
                return state;
            }
        }
        return TRAVELING;
    }
}
