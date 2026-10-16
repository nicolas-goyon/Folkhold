package io.github.nicolasgoyon.folkhold.core.sim.quest;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.Objects;
import java.util.UUID;

/**
 * A quest of a village. The mod generates it from the state of the world.
 *
 * <ul>
 *   <li>Delivery: the target is an item id and the count is the quantity.</li>
 *   <li>Escort: the target is the id of a caravan. The count is not used.</li>
 * </ul>
 *
 * <p>The times are game times in ticks.
 */
public final class Quest {
    private final String id;
    private final QuestKind kind;
    private String settlementId;
    private final String factionId;
    private final String target;
    private final int count;
    private final long rewardEmeralds;
    private final int rewardReputation;
    private final ReputationTier minTier;
    private final long offerEnd;
    private final long duration;
    private QuestState state = QuestState.OFFERED;
    private UUID playerId;
    private long deadline;

    /**
     * Makes a quest in the state {@link QuestState#OFFERED}.
     *
     * @param id               the id of the quest (a UUID as text)
     * @param kind             the kind
     * @param settlementId     the id of the village that gives the quest
     * @param factionId        the id of the faction of the village
     * @param target           the item id (delivery) or the caravan id (escort)
     * @param count            the quantity of items (delivery)
     * @param rewardEmeralds   the emeralds of the reward
     * @param rewardReputation the reputation of the reward
     * @param minTier          the lowest reputation tier that can accept the quest
     * @param offerEnd         the game time when the offer ends
     * @param duration         the time from the acceptance to the deadline, in ticks
     */
    public Quest(String id, QuestKind kind, String settlementId, String factionId, String target, int count,
            long rewardEmeralds, int rewardReputation, ReputationTier minTier, long offerEnd, long duration) {
        this.id = Objects.requireNonNull(id);
        this.kind = Objects.requireNonNull(kind);
        this.settlementId = Objects.requireNonNull(settlementId);
        this.factionId = Objects.requireNonNull(factionId);
        this.target = Objects.requireNonNull(target);
        this.count = count;
        this.rewardEmeralds = rewardEmeralds;
        this.rewardReputation = rewardReputation;
        this.minTier = Objects.requireNonNull(minTier);
        this.offerEnd = offerEnd;
        this.duration = duration;
    }

    public String id() {
        return id;
    }

    public QuestKind kind() {
        return kind;
    }

    public String settlementId() {
        return settlementId;
    }

    /**
     * Replaces the id of the village that gives the quest.
     *
     * @param oldId the current id of the village
     * @param newId the new id
     * @return true if the quest had the old id
     */
    public boolean renameSettlement(String oldId, String newId) {
        if (!settlementId.equals(oldId)) {
            return false;
        }
        settlementId = Objects.requireNonNull(newId);
        return true;
    }

    public String factionId() {
        return factionId;
    }

    public String target() {
        return target;
    }

    public int count() {
        return count;
    }

    public long rewardEmeralds() {
        return rewardEmeralds;
    }

    public int rewardReputation() {
        return rewardReputation;
    }

    public ReputationTier minTier() {
        return minTier;
    }

    /** The game time when the offer ends. */
    public long offerEnd() {
        return offerEnd;
    }

    /** The time from the acceptance to the deadline, in ticks. */
    public long duration() {
        return duration;
    }

    public QuestState state() {
        return state;
    }

    /** The player who accepted the quest, or null. */
    public UUID playerId() {
        return playerId;
    }

    /** The game time of the deadline. 0 before the acceptance. */
    public long deadline() {
        return deadline;
    }

    /**
     * Sets the state of a quest from the saved data.
     *
     * @param state    the state
     * @param playerId the player, or null
     * @param deadline the deadline
     */
    public void restore(QuestState state, UUID playerId, long deadline) {
        this.state = Objects.requireNonNull(state);
        this.playerId = playerId;
        this.deadline = deadline;
    }

    /** Only {@link Quests} changes the state. */
    void accept(UUID player, long now) {
        this.playerId = Objects.requireNonNull(player);
        this.deadline = now + duration;
        this.state = QuestState.ACCEPTED;
    }

    /** Only {@link Quests} changes the state. */
    void finish(QuestState finalState) {
        if (!finalState.isFinished()) {
            throw new IllegalArgumentException("Not a final state: " + finalState);
        }
        this.state = finalState;
    }

    @Override
    public String toString() {
        return kind.serializedName() + " " + id + " (" + settlementId + ", " + target + " x" + count + ", "
                + state.serializedName() + ", " + rewardEmeralds + " emeralds)";
    }
}
