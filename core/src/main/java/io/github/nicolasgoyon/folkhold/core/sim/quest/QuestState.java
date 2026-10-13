package io.github.nicolasgoyon.folkhold.core.sim.quest;

/** The state of a quest. */
public enum QuestState {
    /** On the quest board of its village. No player has it. */
    OFFERED("offered"),
    /** A player has it. The deadline runs. */
    ACCEPTED("accepted"),
    /** The player did it and got the reward. */
    COMPLETED("completed"),
    /** The player did not do it before the deadline, or abandoned it, or the condition failed. */
    FAILED("failed"),
    /** No player accepted it before the end of the offer. */
    EXPIRED("expired");

    private final String serializedName;

    QuestState(String serializedName) {
        this.serializedName = serializedName;
    }

    /** The name in the saved data and in the translation keys. */
    public String serializedName() {
        return serializedName;
    }

    /** True for {@link #COMPLETED}, {@link #FAILED} and {@link #EXPIRED}. */
    public boolean isFinished() {
        return this == COMPLETED || this == FAILED || this == EXPIRED;
    }

    /**
     * The state with a name.
     *
     * @param name the name in the saved data
     * @return the state. An unknown name gives {@link #EXPIRED}.
     */
    public static QuestState fromSerializedName(String name) {
        for (QuestState state : values()) {
            if (state.serializedName.equals(name)) {
                return state;
            }
        }
        return EXPIRED;
    }
}
