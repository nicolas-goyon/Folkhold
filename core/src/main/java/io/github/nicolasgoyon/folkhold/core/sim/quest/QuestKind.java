package io.github.nicolasgoyon.folkhold.core.sim.quest;

/** The kind of a quest. */
public enum QuestKind {
    /** A village with a shortage asks for an item. */
    DELIVERY("delivery"),
    /** The player protects a caravan to its destination. */
    ESCORT("escort");

    private final String serializedName;

    QuestKind(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data and in the translation keys. */
    public String serializedName() {
        return serializedName;
    }

    /**
     * The kind with a name.
     *
     * @param name the name in the saved data
     * @return the kind. An unknown name gives {@link #DELIVERY}.
     */
    public static QuestKind fromSerializedName(String name) {
        for (QuestKind kind : values()) {
            if (kind.serializedName.equals(name)) {
                return kind;
            }
        }
        return DELIVERY;
    }
}
