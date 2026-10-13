package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.quest.Deliveries;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the delivery quests. Each test puts the treasury back at the end. See {@link TestSupport} for
 * the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DeliveryGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final String BREAD = "minecraft:bread";

    private DeliveryGameTests() {
    }

    private static Settlement village(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        settlement.economy().setPopulation(10);
        return settlement;
    }

    /** A neutral player with some bread, and an accepted delivery of 8 bread for the village. */
    private static Quest acceptedDelivery(MinecraftServer server, Settlement village, ServerPlayer player,
            int bread) {
        ReputationData reputation = ReputationData.get(server);
        reputation.get(player.getUUID()).setFaction(village.factionId(), 0);
        reputation.get(player.getUUID()).setVillage(village.id(), 0);
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.BREAD, bread));
        Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, village.id(), village.factionId(),
                BREAD, 8, 3, 20, ReputationTier.NEUTRAL, Long.MAX_VALUE / 2, 24_000);
        QuestData.get(server).quests().add(quest);
        QuestBoards.accept(player, quest.id());
        return quest;
    }

    private static void clear(MinecraftServer server, Quest quest) {
        Quests quests = QuestData.get(server).quests();
        quests.finish(quest.id(), QuestState.FAILED);
        quests.removeFinished();
    }

    /** The player gives the bread: the stock gets it, and the player gets the emeralds and the reputation. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "delivery_complete")
    public static void deliveryCompletesTheQuest(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        SettlementData settlements = SettlementData.get(server);
        long treasury = settlements.treasury(village.factionId());
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Quest quest = acceptedDelivery(server, village, player, 10);
        int stockBefore = village.economy().stock().count(BREAD);

        QuestBoards.handle(player, new QuestActionPayload(QuestBoardPayload.ACTION_DELIVER, quest.id(),
                village.id()));

        int bread = Deliveries.count(player.getInventory(), Items.BREAD);
        int emeralds = Deliveries.count(player.getInventory(), Items.EMERALD);
        int stockAfter = village.economy().stock().count(BREAD);
        int value = Reputations.effective(server, player, village.factionId(), village.id()).orElse(0);
        long paid = treasury - settlements.treasury(village.factionId());
        server.getPlayerList().remove(player);
        settlements.addTreasury(village.factionId(), treasury - settlements.treasury(village.factionId()));
        clear(server, quest);

        helper.assertTrue(quest.state() == QuestState.COMPLETED, "The quest must be complete: " + quest.state());
        helper.assertTrue(bread == 2 && stockAfter - stockBefore == 8,
                "The 8 bread must go from the player to the stock: " + bread + ", " + (stockAfter - stockBefore));
        helper.assertTrue(emeralds == 3 && paid == 3, "The reward must come from the treasury: " + emeralds);
        helper.assertTrue(value > 0, "The reputation must go up: " + value);
        helper.succeed();
    }

    /** With too few items, the quest stays active and the player keeps the items. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "delivery_missing")
    public static void deliveryNeedsAllTheItems(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Quest quest = acceptedDelivery(server, village, player, 5);

        boolean complete = Deliveries.deliver(player, quest.id());

        int bread = Deliveries.count(player.getInventory(), Items.BREAD);
        server.getPlayerList().remove(player);
        QuestState state = quest.state();
        clear(server, quest);
        helper.assertTrue(!complete && state == QuestState.ACCEPTED, "The quest must stay active");
        helper.assertTrue(bread == 5, "The player must keep the bread: " + bread);
        helper.succeed();
    }

    /** The screen of the village of the quest shows the deliver button. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "delivery_button")
    public static void screenShowsTheDeliverButton(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        Quest quest = acceptedDelivery(server, village, player, 0);

        QuestBoardPayload board = QuestBoards.board(player, village);

        server.getPlayerList().remove(player);
        clear(server, quest);
        helper.assertTrue(board.active().stream().anyMatch(entry -> entry.questId().equals(quest.id())
                && entry.action() == QuestBoardPayload.ACTION_DELIVER && entry.abandon()),
                "The active delivery must have the deliver and abandon buttons");
        helper.succeed();
    }
}
