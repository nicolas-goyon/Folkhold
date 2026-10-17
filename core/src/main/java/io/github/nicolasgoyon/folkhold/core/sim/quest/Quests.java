package io.github.nicolasgoyon.folkhold.core.sim.quest;

import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The quests of a world: the offers of the villages and the quest log of each player.
 *
 * <ul>
 *   <li>A player accepts an offer if the reputation tier of the player is high enough and if the player has fewer
 *       active quests than the maximum.</li>
 *   <li>An offer that no player accepts before its end expires. An accepted quest that is not completed before its
 *       deadline fails.</li>
 *   <li>A player can abandon an accepted quest. The quest fails.</li>
 * </ul>
 */
public final class Quests {
    private final Map<String, Quest> quests = new LinkedHashMap<>();

    /** Result of an acceptance. */
    public enum AcceptResult {
        ACCEPTED,
        /** No quest has this id. */
        UNKNOWN,
        /** The quest is not an offer. */
        NOT_OFFERED,
        /** The reputation tier of the player is too low. */
        TIER_TOO_LOW,
        /** The player has the maximum number of active quests. */
        TOO_MANY
    }

    /**
     * Adds a quest.
     *
     * @param quest the quest. Its id must not be in the registry.
     */
    public void add(Quest quest) {
        if (quests.containsKey(quest.id())) {
            throw new IllegalArgumentException("Duplicate quest: " + quest.id());
        }
        quests.put(quest.id(), quest);
    }

    public Optional<Quest> byId(String id) {
        return Optional.ofNullable(quests.get(id));
    }

    /**
     * Replaces the id of a village in all quests.
     *
     * @param oldId the current id of the village
     * @param newId the new id
     * @return the number of quests that had the old id
     */
    public int renameSettlement(String oldId, String newId) {
        int count = 0;
        for (Quest quest : quests.values()) {
            if (quest.renameSettlement(oldId, newId)) {
                count++;
            }
        }
        return count;
    }

    /** All quests, in the order of addition. */
    public Collection<Quest> all() {
        return Collections.unmodifiableCollection(quests.values());
    }

    /** The offers of a village. */
    public List<Quest> offers(String settlementId) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.state() == QuestState.OFFERED && quest.settlementId().equals(settlementId)) {
                result.add(quest);
            }
        }
        return result;
    }

    /** The accepted quests of a player. */
    public List<Quest> active(UUID playerId) {
        List<Quest> result = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.state() == QuestState.ACCEPTED && playerId.equals(quest.playerId())) {
                result.add(quest);
            }
        }
        return result;
    }

    /**
     * A player accepts an offer.
     *
     * @param questId   the id of the quest
     * @param playerId  the player
     * @param tier      the reputation tier of the player with the faction of the quest
     * @param now       the game time
     * @param maxActive the maximum number of active quests of a player
     * @return the result
     */
    public AcceptResult accept(String questId, UUID playerId, ReputationTier tier, long now, int maxActive) {
        Quest quest = quests.get(questId);
        if (quest == null) {
            return AcceptResult.UNKNOWN;
        }
        if (quest.state() != QuestState.OFFERED) {
            return AcceptResult.NOT_OFFERED;
        }
        if (!tier.isAtLeast(quest.minTier())) {
            return AcceptResult.TIER_TOO_LOW;
        }
        if (active(playerId).size() >= maxActive) {
            return AcceptResult.TOO_MANY;
        }
        quest.accept(playerId, now);
        return AcceptResult.ACCEPTED;
    }

    /**
     * A player abandons an accepted quest. The quest fails.
     *
     * @param questId  the id of the quest
     * @param playerId the player
     * @return true if the player had this quest
     */
    public boolean abandon(String questId, UUID playerId) {
        Quest quest = quests.get(questId);
        if (quest == null || quest.state() != QuestState.ACCEPTED || !playerId.equals(quest.playerId())) {
            return false;
        }
        quest.finish(QuestState.FAILED);
        return true;
    }

    /**
     * Ends an accepted quest.
     *
     * @param questId the id of the quest
     * @param state   {@link QuestState#COMPLETED} or {@link QuestState#FAILED}
     * @return true if the quest was accepted
     * @throws IllegalArgumentException for another state
     */
    public boolean finish(String questId, QuestState state) {
        if (state != QuestState.COMPLETED && state != QuestState.FAILED) {
            throw new IllegalArgumentException("An accepted quest ends as completed or failed, not " + state);
        }
        Quest quest = quests.get(questId);
        if (quest == null || quest.state() != QuestState.ACCEPTED) {
            return false;
        }
        quest.finish(state);
        return true;
    }

    /**
     * Ends an offer, for example when its village becomes ruins.
     *
     * @param questId the id of the quest
     * @return true if the quest was an offer
     */
    public boolean withdraw(String questId) {
        Quest quest = quests.get(questId);
        if (quest == null || quest.state() != QuestState.OFFERED) {
            return false;
        }
        quest.finish(QuestState.EXPIRED);
        return true;
    }

    /**
     * Ends the offers after their end and the accepted quests after their deadline.
     *
     * @param now the game time
     * @return the quests that changed state: expired offers and failed quests
     */
    public List<Quest> expire(long now) {
        List<Quest> changed = new ArrayList<>();
        for (Quest quest : quests.values()) {
            if (quest.state() == QuestState.OFFERED && now >= quest.offerEnd()) {
                quest.finish(QuestState.EXPIRED);
                changed.add(quest);
            } else if (quest.state() == QuestState.ACCEPTED && now >= quest.deadline()) {
                quest.finish(QuestState.FAILED);
                changed.add(quest);
            }
        }
        return changed;
    }

    /**
     * Removes the finished quests.
     *
     * @return the number of removed quests
     */
    public int removeFinished() {
        int before = quests.size();
        quests.values().removeIf(quest -> quest.state().isFinished());
        return before - quests.size();
    }

    public int size() {
        return quests.size();
    }
}
