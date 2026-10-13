package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.quest.QuestSystem;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the quest model: generation, saved data, end of the offers. The test villages are far from the
 * test area. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestGameTests {
    private static final int FAR_X = 24_000_000;

    private QuestGameTests() {
    }

    /** An active village with an economy and no food: it needs food. */
    private static Settlement hungryVillage(MinecraftServer server, int z) {
        String faction = FactionData.get(server).factions().get(0).id();
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), FAR_X >> 4, z >> 4, faction,
                "Test quests", FAR_X, z);
        settlement.economy().setPopulation(10);
        settlement.economy().setInitialized(true);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    private static void clear(MinecraftServer server, Settlement settlement) {
        Quests quests = QuestData.get(server).quests();
        for (Quest quest : List.copyOf(quests.all())) {
            if (quest.settlementId().equals(settlement.id())) {
                quests.withdraw(quest.id());
                quests.finish(quest.id(), QuestState.FAILED);
            }
        }
        quests.removeFinished();
        settlement.setState(SettlementState.RUINS);
        SettlementData.get(server).setDirty();
    }

    /** A village with a need offers delivery quests, up to the number of offers of its level. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "quest_offers")
    public static void villageOffersDeliveriesForItsNeeds(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = hungryVillage(server, 0);
        long now = helper.getLevel().getGameTime();

        List<Quest> first = QuestSystem.offerDeliveries(server, village, now);
        List<Quest> second = QuestSystem.offerDeliveries(server, village, now);
        List<Quest> offers = QuestData.get(server).quests().offers(village.id());

        clear(server, village);
        helper.assertTrue(EconomyData.isActive(), "The economy data must be loaded");
        helper.assertTrue(first.size() == 1 && second.isEmpty() && offers.size() == 1,
                "A village of level 1 must offer 1 quest: " + first.size() + ", " + second.size());
        Quest quest = first.get(0);
        helper.assertTrue(quest.kind() == QuestKind.DELIVERY && EconomyData.catalog().info(quest.target()).isFood(),
                "The quest must ask for food: " + quest.target());
        helper.assertTrue(quest.count() > 0 && quest.rewardEmeralds() >= 1 && quest.offerEnd() > now,
                "The quest must have a quantity, a reward and an end of offer");
        helper.succeed();
    }

    /** The saved data keeps the offers and the accepted quests. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "quest_save")
    public static void questDataKeepsTheQuests(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        QuestData data = QuestData.get(server);
        Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, "test_village", "test_faction",
                "minecraft:bread", 12, 7, 20, ReputationTier.FRIEND, 5000, 1200);
        data.quests().add(quest);
        UUID player = UUID.randomUUID();
        data.quests().accept(quest.id(), player, ReputationTier.ALLY, 100, 3);

        CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
        Quest loaded = QuestData.load(tag, server.registryAccess()).quests().byId(quest.id()).orElse(null);
        data.quests().finish(quest.id(), QuestState.FAILED);
        data.quests().removeFinished();

        helper.assertTrue(loaded != null, "The loaded data must have the quest");
        helper.assertTrue(loaded.kind() == QuestKind.DELIVERY && loaded.settlementId().equals("test_village")
                && loaded.factionId().equals("test_faction") && loaded.target().equals("minecraft:bread")
                && loaded.count() == 12, "The quest must keep its kind, village, faction and target");
        helper.assertTrue(loaded.rewardEmeralds() == 7 && loaded.rewardReputation() == 20
                && loaded.minTier() == ReputationTier.FRIEND && loaded.offerEnd() == 5000
                && loaded.duration() == 1200, "The quest must keep its reward, tier and times");
        helper.assertTrue(loaded.state() == QuestState.ACCEPTED && player.equals(loaded.playerId())
                && loaded.deadline() == 1300, "The quest must keep its player and deadline");
        helper.succeed();
    }

    /** The offers of a village that becomes ruins end at the next abstract tick. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "quest_ruins")
    public static void offersOfARuinedVillageEnd(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = hungryVillage(server, 5000);
        QuestSystem.offerDeliveries(server, village, helper.getLevel().getGameTime());
        int before = QuestData.get(server).quests().offers(village.id()).size();

        village.setState(SettlementState.RUINS);
        QuestSystem.tick(server, new TickInfo(SettlementData.get(server).tickNumber() + 1, false));
        int after = QuestData.get(server).quests().offers(village.id()).size();

        clear(server, village);
        helper.assertTrue(before == 1 && after == 0, "The offers must end: " + before + " -> " + after);
        helper.succeed();
    }
}
