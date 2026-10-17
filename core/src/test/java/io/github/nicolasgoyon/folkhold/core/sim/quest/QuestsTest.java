package io.github.nicolasgoyon.folkhold.core.sim.quest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests.AcceptResult;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class QuestsTest {
    private static final UUID PLAYER = UUID.randomUUID();
    private static final QuestGenerator.Settings SETTINGS =
            new QuestGenerator.Settings(1000, 500, 1.5, 20, 32, 20);

    private static Quest quest(String id, ReputationTier minTier) {
        return new Quest(id, QuestKind.DELIVERY, "village", "faction", "minecraft:bread", 10, 5, 20, minTier,
                1000, 500);
    }

    @Test
    void playerAcceptsAnOffer() {
        Quests quests = new Quests();
        quests.add(quest("a", ReputationTier.NEUTRAL));

        assertEquals(AcceptResult.ACCEPTED, quests.accept("a", PLAYER, ReputationTier.NEUTRAL, 100, 3));

        Quest quest = quests.byId("a").orElseThrow();
        assertEquals(QuestState.ACCEPTED, quest.state());
        assertEquals(PLAYER, quest.playerId());
        assertEquals(600, quest.deadline());
        assertEquals(List.of(quest), quests.active(PLAYER));
        assertTrue(quests.offers("village").isEmpty());
        assertEquals(AcceptResult.NOT_OFFERED, quests.accept("a", UUID.randomUUID(), ReputationTier.ALLY, 100, 3));
        assertEquals(AcceptResult.UNKNOWN, quests.accept("b", PLAYER, ReputationTier.ALLY, 100, 3));
    }

    @Test
    void tierAndMaximumLimitTheAcceptance() {
        Quests quests = new Quests();
        quests.add(quest("friend", ReputationTier.FRIEND));
        quests.add(quest("a", ReputationTier.NEUTRAL));
        quests.add(quest("b", ReputationTier.NEUTRAL));

        assertEquals(AcceptResult.TIER_TOO_LOW, quests.accept("friend", PLAYER, ReputationTier.NEUTRAL, 0, 1));
        assertEquals(AcceptResult.ACCEPTED, quests.accept("a", PLAYER, ReputationTier.NEUTRAL, 0, 1));
        assertEquals(AcceptResult.TOO_MANY, quests.accept("b", PLAYER, ReputationTier.NEUTRAL, 0, 1));
    }

    @Test
    void offersExpireAndAcceptedQuestsFail() {
        Quests quests = new Quests();
        quests.add(quest("offer", ReputationTier.NEUTRAL));
        quests.add(quest("taken", ReputationTier.NEUTRAL));
        quests.accept("taken", PLAYER, ReputationTier.NEUTRAL, 0, 3);

        assertTrue(quests.expire(499).isEmpty());
        List<Quest> changed = quests.expire(1000);

        assertEquals(2, changed.size());
        assertEquals(QuestState.EXPIRED, quests.byId("offer").orElseThrow().state());
        assertEquals(QuestState.FAILED, quests.byId("taken").orElseThrow().state());
        assertEquals(2, quests.removeFinished());
        assertEquals(0, quests.size());
    }

    @Test
    void abandonAndFinishChangeOnlyAcceptedQuests() {
        Quests quests = new Quests();
        quests.add(quest("a", ReputationTier.NEUTRAL));
        quests.add(quest("b", ReputationTier.NEUTRAL));

        assertFalse(quests.abandon("a", PLAYER), "An offer cannot be abandoned");
        quests.accept("a", PLAYER, ReputationTier.NEUTRAL, 0, 3);
        assertFalse(quests.abandon("a", UUID.randomUUID()), "Another player cannot abandon the quest");
        assertTrue(quests.abandon("a", PLAYER));
        assertEquals(QuestState.FAILED, quests.byId("a").orElseThrow().state());

        quests.accept("b", PLAYER, ReputationTier.NEUTRAL, 0, 3);
        assertTrue(quests.finish("b", QuestState.COMPLETED));
        assertFalse(quests.finish("b", QuestState.FAILED), "A finished quest does not change");
        assertEquals(QuestState.COMPLETED, quests.byId("b").orElseThrow().state());
    }

    @Test
    void finishAcceptsOnlyCompletedAndFailed() {
        Quests quests = new Quests();
        quests.add(quest("a", ReputationTier.NEUTRAL));
        quests.accept("a", PLAYER, ReputationTier.NEUTRAL, 0, 5);

        assertThrows(IllegalArgumentException.class, () -> quests.finish("a", QuestState.EXPIRED));
        assertThrows(IllegalArgumentException.class, () -> quests.finish("a", QuestState.ACCEPTED));
        assertEquals(QuestState.ACCEPTED, quests.byId("a").orElseThrow().state());
    }

    @Test
    void offerCountFollowsTheLevel() {
        assertEquals(1, QuestGenerator.offerCount(1));
        assertEquals(1, QuestGenerator.offerCount(4));
        assertEquals(2, QuestGenerator.offerCount(5));
        assertEquals(5, QuestGenerator.offerCount(20));
    }

    @Test
    void deliveriesAskForTheNeedsWithTheMostValue() {
        Map<String, Integer> needs = Map.of("minecraft:bread", 30, "minecraft:iron_ingot", 10, "minecraft:coal", 5);
        Map<String, Double> prices = Map.of("minecraft:bread", 0.2, "minecraft:iron_ingot", 2.0, "minecraft:coal",
                0.5);

        List<Quest> quests = QuestGenerator.deliveries("village", "faction", needs, prices::get,
                Set.of("minecraft:coal"), 2, 100, SETTINGS);

        assertEquals(2, quests.size());
        assertEquals("minecraft:iron_ingot", quests.get(0).target(), "Iron has the most value");
        assertEquals(10, quests.get(0).count());
        assertEquals(30, quests.get(0).rewardEmeralds(), "10 × 2.0 × 1.5");
        assertEquals(ReputationTier.FRIEND, quests.get(0).minTier(), "A reward of 20 or more is for friends");
        assertEquals("minecraft:bread", quests.get(1).target(), "Coal is already offered");
        assertEquals(9, quests.get(1).rewardEmeralds(), "30 × 0.2 × 1.5");
        assertEquals(ReputationTier.NEUTRAL, quests.get(1).minTier());
        assertEquals(1100, quests.get(1).offerEnd());
        assertEquals(QuestKind.DELIVERY, quests.get(1).kind());
    }

    @Test
    void deliveryQuantityHasAMaximum() {
        List<Quest> quests = QuestGenerator.deliveries("village", "faction", Map.of("minecraft:bread", 500),
                item -> 0.01, Set.of(), 1, 0, SETTINGS);

        assertEquals(32, quests.get(0).count());
        assertEquals(1, quests.get(0).rewardEmeralds(), "The reward is 1 emerald or more");
    }
}
