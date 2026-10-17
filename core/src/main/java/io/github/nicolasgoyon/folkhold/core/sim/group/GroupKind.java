package io.github.nicolasgoyon.folkhold.core.sim.group;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Optional;

/** The kind of a mobile group. */
public enum GroupKind implements SerializedName {
    /** Merchant, loaded mules and guards. It carries the goods of a trade between two villages. */
    CARAVAN("caravan"),
    /** Merchant of the neutral faction and a pack animal. It goes from village to village. */
    TRAVELING_MERCHANT("traveling_merchant");

    private final String serializedName;

    GroupKind(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data. */
    @Override
    public String serializedName() {
        return serializedName;
    }

    /**
     * The kind with a serialized name.
     *
     * @param name the name in the saved data
     * @return the kind, or empty for an unknown name
     */
    public static Optional<GroupKind> fromSerializedName(String name) {
        return SerializedName.lookup(GroupKind.class, name);
    }
}
