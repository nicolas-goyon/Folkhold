package io.github.nicolasgoyon.folkhold.core.sim.quest;

import io.github.nicolasgoyon.folkhold.core.sim.SerializedName;
import java.util.Optional;

/** The kind of a quest. */
public enum QuestKind implements SerializedName {
    /** A village with a shortage asks for an item. */
    DELIVERY("delivery"),
    /** The player protects a caravan to its destination. */
    ESCORT("escort");

    private final String serializedName;

    QuestKind(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data and in the translation keys. */
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
    public static Optional<QuestKind> fromSerializedName(String name) {
        return SerializedName.lookup(QuestKind.class, name);
    }
}
