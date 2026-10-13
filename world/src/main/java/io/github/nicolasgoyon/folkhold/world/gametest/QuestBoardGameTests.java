package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the quest board: the board of the village center, the village of a board, accept and abandon.
 * See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class QuestBoardGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos BOARD = new BlockPos(6, 1, 12);

    private QuestBoardGameTests() {
    }

    /** A village with a heart and a quest board. */
    private static Settlement village(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        helper.setBlock(BOARD, ModBlocks.QUEST_BOARD.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        return Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
    }

    /** An offer of the village, for a tier. */
    private static Quest offer(MinecraftServer server, Settlement village, ReputationTier minTier) {
        Quest quest = new Quest(UUID.randomUUID().toString(), QuestKind.DELIVERY, village.id(), village.factionId(),
                "minecraft:bread", 8, 3, 20, minTier, Long.MAX_VALUE / 2, 24_000);
        QuestData.get(server).quests().add(quest);
        return quest;
    }

    private static void clear(MinecraftServer server, List<Quest> quests) {
        Quests registry = QuestData.get(server).quests();
        for (Quest quest : quests) {
            registry.withdraw(quest.id());
            registry.finish(quest.id(), QuestState.FAILED);
        }
        registry.removeFinished();
    }

    /** The center piece of the village has one quest board. */
    @GameTest(template = "village/center", timeoutTicks = 20, batch = "quest_board_center")
    public static void centerHasTheQuestBoard(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int boards = 0;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (helper.getLevel().getBlockState(pos).is(ModBlocks.QUEST_BOARD.get())) {
                boards++;
            }
        }
        helper.assertTrue(boards == 1, "Expected 1 quest board, found " + boards);
        helper.succeed();
    }

    /** The board finds its village, and the screen shows the offers of the village. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "quest_board_village")
    public static void boardShowsTheOffersOfItsVillage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        Quest quest = offer(server, village, ReputationTier.NEUTRAL);
        ServerPlayer player = TestSupport.mockPlayer(helper);

        Settlement found = QuestBoards.villageAt(helper.getLevel(), helper.absolutePos(BOARD)).orElse(null);
        QuestBoardPayload board = QuestBoards.board(player, village);

        server.getPlayerList().remove(player);
        clear(server, List.of(quest));
        helper.assertTrue(found != null && found.id().equals(village.id()), "The board must find its village");
        helper.assertTrue(board.settlementId().equals(village.id())
                && board.offers().stream().anyMatch(entry -> entry.questId().equals(quest.id())
                        && entry.action() == QuestBoardPayload.ACTION_ACCEPT),
                "The screen must show the offer with an accept button");
        helper.succeed();
    }

    /** A player accepts an offer, then abandons it. The tier and the maximum limit the acceptance. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "quest_board_accept")
    public static void playerAcceptsAndAbandons(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        ReputationData reputation = ReputationData.get(server);
        reputation.get(player.getUUID()).setFaction(village.factionId(), 0);
        reputation.get(player.getUUID()).setVillage(village.id(), 0);
        List<Quest> created = new ArrayList<>();
        Quest friends = offer(server, village, ReputationTier.FRIEND);
        created.add(friends);
        for (int i = 0; i <= WorldConfig.QUEST_MAX_ACTIVE.get(); i++) {
            created.add(offer(server, village, ReputationTier.NEUTRAL));
        }

        Quests.AcceptResult tier = QuestBoards.accept(player, friends.id());
        List<Quests.AcceptResult> results = new ArrayList<>();
        for (Quest quest : created.subList(1, created.size())) {
            results.add(QuestBoards.accept(player, quest.id()));
        }
        Quest first = created.get(1);
        QuestBoards.handle(player, new QuestActionPayload(QuestBoardPayload.ACTION_ABANDON, first.id(),
                village.id()));
        QuestState abandoned = first.state();
        int active = QuestData.get(server).quests().active(player.getUUID()).size();

        server.getPlayerList().remove(player);
        clear(server, created);
        helper.assertTrue(tier == Quests.AcceptResult.TIER_TOO_LOW, "A neutral player cannot take a quest of friends");
        helper.assertTrue(results.get(results.size() - 1) == Quests.AcceptResult.TOO_MANY
                && results.subList(0, results.size() - 1).stream().allMatch(r -> r == Quests.AcceptResult.ACCEPTED),
                "The player accepts quests up to the maximum: " + results);
        helper.assertTrue(abandoned == QuestState.FAILED, "An abandoned quest fails");
        helper.assertTrue(active == WorldConfig.QUEST_MAX_ACTIVE.get() - 1, "One active quest less: " + active);
        helper.succeed();
    }
}
