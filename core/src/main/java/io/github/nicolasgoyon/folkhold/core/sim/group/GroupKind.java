package io.github.nicolasgoyon.folkhold.core.sim.group;

/** The kind of a mobile group. */
public enum GroupKind {
    /** Merchant, loaded mules and guards. It carries the goods of a trade between two villages. */
    CARAVAN("caravan"),
    /** Merchant of the neutral faction and a pack animal. It goes from village to village. */
    TRAVELING_MERCHANT("traveling_merchant");

    private final String serializedName;

    GroupKind(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data. */
    public String serializedName() {
        return serializedName;
    }

    /**
     * The kind with a name.
     *
     * @param name the name in the saved data
     * @return the kind. An unknown name gives {@link #CARAVAN}.
     */
    public static GroupKind fromSerializedName(String name) {
        for (GroupKind kind : values()) {
            if (kind.serializedName.equals(name)) {
                return kind;
            }
        }
        return CARAVAN;
    }
}
