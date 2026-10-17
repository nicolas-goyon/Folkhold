package io.github.nicolasgoyon.folkhold.core.sim.group;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Optional;

/** The state of a mobile group. */
public enum GroupState implements SerializedName {
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
    @Override
    public String serializedName() {
        return serializedName;
    }

    /**
     * The state with a serialized name.
     *
     * @param name the name in the saved data
     * @return the state, or empty for an unknown name
     */
    public static Optional<GroupState> fromSerializedName(String name) {
        return SerializedName.lookup(GroupState.class, name);
    }
}
