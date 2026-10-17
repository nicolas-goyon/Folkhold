package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.QuestBoardBlock;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.network.QuestActionPayload;
import io.github.nicolasgoyon.folkhold.world.network.QuestBoardPayload;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.registry.ModItems;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
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
        ModBlocks.QUEST_BOARD.get().place(helper.getLevel(), helper.absolutePos(BOARD), Direction.SOUTH,
                Block.UPDATE_ALL);
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

    /**
     * The center piece of the village has one complete quest board: 9 parts, one of them renders the model. The
     * board is at the west end of the east-west path and faces east, to the plaza.
     */
    @GameTest(template = "village/center", timeoutTicks = 20, batch = "quest_board_center")
    public static void centerHasTheQuestBoard(GameTestHelper helper) {
        AABB bounds = helper.getBounds();
        int parts = 0;
        int renderParts = 0;
        BlockPos render = null;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            BlockState state = helper.getLevel().getBlockState(pos);
            if (state.is(ModBlocks.QUEST_BOARD.get())) {
                parts++;
                if (QuestBoardBlock.isRenderPart(state)) {
                    renderParts++;
                    render = pos.immutable();
                }
            }
        }
        helper.assertTrue(parts == 9 && renderParts == 1,
                "Expected 1 board of 9 parts, found " + parts + " parts and " + renderParts + " render parts");
        helper.assertTrue(isComplete(helper, QuestBoardBlock.basePos(render, helper.getLevel().getBlockState(render)),
                helper.getLevel().getBlockState(render).getValue(QuestBoardBlock.FACING)),
                "The parts of the board must be at their positions");
        BlockPos heart = null;
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ),
                BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            if (helper.getLevel().getBlockState(pos).is(ModBlocks.VILLAGE_HEART.get())) {
                heart = pos.immutable();
            }
        }
        helper.assertTrue(heart != null && render.getZ() == heart.getZ() && render.getX() < heart.getX() - 10,
                "The board must be at the west end of the east-west path: " + render + ", heart " + heart);
        helper.assertTrue(helper.getLevel().getBlockState(render).getValue(QuestBoardBlock.FACING) == Direction.EAST,
                "The board must face the plaza");
        helper.succeed();
    }

    /** True if the 9 parts of a board are at their positions. */
    private static boolean isComplete(GameTestHelper helper, BlockPos base, Direction facing) {
        QuestBoardBlock block = ModBlocks.QUEST_BOARD.get();
        for (int column = 0; column < QuestBoardBlock.SIZE; column++) {
            for (int row = 0; row < QuestBoardBlock.SIZE; row++) {
                BlockPos pos = QuestBoardBlock.partPos(base, facing, column, row);
                if (!block.isPart(helper.getLevel().getBlockState(pos), facing, column, row)) {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if no part of a board is at the positions of a board. */
    private static boolean isRemoved(GameTestHelper helper, BlockPos base, Direction facing) {
        for (int column = 0; column < QuestBoardBlock.SIZE; column++) {
            for (int row = 0; row < QuestBoardBlock.SIZE; row++) {
                if (helper.getLevel().getBlockState(QuestBoardBlock.partPos(base, facing, column, row))
                        .is(ModBlocks.QUEST_BOARD.get())) {
                    return false;
                }
            }
        }
        return true;
    }

    /** The board needs 9 free positions. The 9 parts are at their positions, and each part finds the base. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "quest_board_place")
    public static void boardHasNineParts(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        QuestBoardBlock block = ModBlocks.QUEST_BOARD.get();
        helper.setBlock(new BlockPos(7, 3, 8), Blocks.STONE);
        boolean blocked = !QuestBoardBlock.canPlaceAt(helper.getLevel(), base, Direction.NORTH);
        helper.setBlock(new BlockPos(7, 3, 8), Blocks.AIR);
        boolean free = QuestBoardBlock.canPlaceAt(helper.getLevel(), base, Direction.NORTH);

        block.place(helper.getLevel(), base, Direction.NORTH, Block.UPDATE_ALL);

        helper.assertTrue(blocked, "A block in one of the 9 positions must stop the placement");
        helper.assertTrue(free, "The board must go into 9 free positions");
        helper.assertTrue(isComplete(helper, base, Direction.NORTH), "The 9 parts must be at their positions");
        BlockPos topRight = QuestBoardBlock.partPos(base, Direction.NORTH, 2, 2);
        helper.assertTrue(QuestBoardBlock.basePos(topRight, helper.getLevel().getBlockState(topRight)).equals(base),
                "Each part must find the base of its board");
        helper.assertTrue(topRight.equals(base.east().above(2)),
                "The columns go in the clockwise direction of the board: " + topRight);
        helper.succeed();
    }

    /**
     * The removal of one part removes the complete board. Only the bottom middle part drops the item, so the board
     * drops one item.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "quest_board_break")
    public static void breakingOnePartRemovesTheBoard(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        QuestBoardBlock block = ModBlocks.QUEST_BOARD.get();
        AABB area = new AABB(base).inflate(4.0);

        block.place(helper.getLevel(), base, Direction.EAST, Block.UPDATE_ALL);
        helper.getLevel().destroyBlock(QuestBoardBlock.partPos(base, Direction.EAST, 0, 2), true);
        boolean removedFromTop = isRemoved(helper, base, Direction.EAST);
        int dropsFromTop = drops(helper, area);

        block.place(helper.getLevel(), base, Direction.EAST, Block.UPDATE_ALL);
        helper.getLevel().destroyBlock(base, true);
        boolean removedFromBase = isRemoved(helper, base, Direction.EAST);
        int dropsFromBase = drops(helper, area);
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        helper.assertTrue(removedFromTop && removedFromBase, "The removal of one part must remove the board");
        helper.assertTrue(dropsFromTop == 0, "A part that is not the base drops nothing: " + dropsFromTop);
        helper.assertTrue(dropsFromBase == 1, "The base drops one board: " + dropsFromBase);
        helper.succeed();
    }

    /** The number of quest board items on the ground in an area. */
    private static int drops(GameTestHelper helper, AABB area) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area).stream()
                .filter(item -> item.getItem().is(ModItems.QUEST_BOARD.get()))
                .mapToInt(item -> item.getItem().getCount())
                .sum();
    }

    /** Each part of the board finds the village of the board, so a click on each part opens its quests. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "quest_board_parts")
    public static void eachPartFindsTheVillage(GameTestHelper helper) {
        Settlement village = village(helper);
        BlockPos base = helper.absolutePos(BOARD);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    for (int column = 0; column < QuestBoardBlock.SIZE; column++) {
                        for (int row = 0; row < QuestBoardBlock.SIZE; row++) {
                            BlockPos pos = QuestBoardBlock.partPos(base, Direction.SOUTH, column, row);
                            Settlement found = QuestBoards.villageAt(helper.getLevel(), pos).orElse(null);
                            helper.assertTrue(found != null && found.id().equals(village.id()),
                                    "The part " + column + ", " + row + " must find the village");
                        }
                    }
                })
                .thenSucceed();
    }

    /**
     * The board finds its village, and the screen shows the offers of the village. The heart registers itself when
     * its block entity loads, so the test waits 2 ticks.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "quest_board_village")
    public static void boardShowsTheOffersOfItsVillage(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        Quest quest = offer(server, village, ReputationTier.NEUTRAL);
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    ServerPlayer player = TestSupport.mockPlayer(helper);
                    // A neutral player: the start reputation of a hostile faction is below neutral.
                    ReputationData.get(server).get(player.getUUID()).setFaction(village.factionId(), 0);
                    ReputationData.get(server).get(player.getUUID()).setVillage(village.id(), 0);
                    Settlement found = QuestBoards.villageAt(helper.getLevel(), helper.absolutePos(BOARD))
                            .orElse(null);
                    QuestBoardPayload board = QuestBoards.board(player, village);
                    server.getPlayerList().remove(player);
                    clear(server, List.of(quest));
                    helper.assertTrue(found != null && found.id().equals(village.id()),
                            "The board must find its village");
                    helper.assertTrue(board.settlementId().equals(village.id())
                            && board.offers().stream().anyMatch(entry -> entry.questId().equals(quest.id())
                                    && entry.blocked().isEmpty()),
                            "The screen must show the offer, with no reason to refuse it");
                })
                .thenSucceed();
    }

    /** A player accepts an offer, then abandons it. The tier and the maximum limit the acceptance. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "quest_board_accept")
    public static void playerAcceptsAndAbandons(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.teleportTo(village.x() + 2.5, village.heartY(), village.z() + 0.5);
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

    /**
     * The offers give the reason why the player cannot accept them: a tier too low, or too many active quests. A
     * delivery shows the items of the player.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "quest_board_blocked")
    public static void offersShowWhyThePlayerCannotAccept(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        ReputationData reputation = ReputationData.get(server);
        reputation.get(player.getUUID()).setFaction(village.factionId(), 0);
        reputation.get(player.getUUID()).setVillage(village.id(), 0);
        player.getInventory().clearContent();
        player.getInventory().add(new ItemStack(Items.BREAD, 3));
        List<Quest> created = new ArrayList<>();
        Quest friends = offer(server, village, ReputationTier.FRIEND);
        Quest neutral = offer(server, village, ReputationTier.NEUTRAL);
        created.add(friends);
        created.add(neutral);

        QuestBoardPayload first = QuestBoards.board(player, village);
        for (int i = 0; i < WorldConfig.QUEST_MAX_ACTIVE.get(); i++) {
            Quest filler = offer(server, village, ReputationTier.NEUTRAL);
            created.add(filler);
            QuestBoards.give(player, filler.id());
        }
        QuestBoardPayload full = QuestBoards.board(player, village);

        server.getPlayerList().remove(player);
        clear(server, created);
        QuestBoardPayload.Offer friendOffer = first.offers().stream()
                .filter(offer -> offer.questId().equals(friends.id())).findFirst().orElseThrow();
        QuestBoardPayload.Offer neutralOffer = first.offers().stream()
                .filter(offer -> offer.questId().equals(neutral.id())).findFirst().orElseThrow();
        helper.assertTrue(friendOffer.blocked().isPresent(), "A neutral player cannot accept a quest of friends");
        helper.assertTrue(neutralOffer.blocked().isEmpty() && neutralOffer.itemId().equals("minecraft:bread")
                && neutralOffer.progress().getString().contains("3"),
                "A delivery shows its item and the items of the player: " + neutralOffer.progress().getString());
        helper.assertTrue(full.offers().stream().filter(offer -> offer.questId().equals(neutral.id()))
                .allMatch(offer -> offer.blocked().isPresent()), "With too many quests, the player cannot accept");
        helper.assertTrue(first.tier().equals("neutral") && first.maxActive() == WorldConfig.QUEST_MAX_ACTIVE.get(),
                "The board gives the tier of the player and the maximum of active quests");
        helper.succeed();
    }

    /**
     * The papers of the board follow the number of offers of the village, up to the maximum. The test waits 2 ticks:
     * the heart registers itself at its first tick.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "quest_board_papers")
    public static void papersFollowTheOffers(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement village = village(helper);
        QuestBoardBlock block = ModBlocks.QUEST_BOARD.get();
        BlockPos middle = QuestBoardBlock.partPos(helper.absolutePos(BOARD), Direction.SOUTH, 1, 1);
        Quests registry = QuestData.get(server).quests();
        clear(server, List.copyOf(registry.offers(village.id())));
        helper.startSequence()
                .thenIdle(2)
                .thenExecute(() -> {
                    List<Quest> offers = new ArrayList<>();
                    offers.add(offer(server, village, ReputationTier.NEUTRAL));
                    offers.add(offer(server, village, ReputationTier.NEUTRAL));
                    update(helper, middle);
                    int two = helper.getLevel().getBlockState(middle).getValue(QuestBoardBlock.PAPERS);
                    for (int i = 0; i < QuestBoardBlock.MAX_PAPERS + 3; i++) {
                        offers.add(offer(server, village, ReputationTier.NEUTRAL));
                    }
                    update(helper, middle);
                    int many = helper.getLevel().getBlockState(middle).getValue(QuestBoardBlock.PAPERS);
                    clear(server, offers);
                    update(helper, middle);
                    int none = helper.getLevel().getBlockState(middle).getValue(QuestBoardBlock.PAPERS);

                    helper.assertTrue(two == 2, "Two offers must give two papers, not " + two);
                    helper.assertTrue(many == QuestBoardBlock.MAX_PAPERS,
                            "Many offers must give the maximum, not " + many);
                    helper.assertTrue(none == 0, "No offer must give no paper, not " + none);
                    helper.assertTrue(block.isPart(helper.getLevel().getBlockState(middle), Direction.SOUTH, 1, 1),
                            "The board must stay complete");
                })
                .thenSucceed();
    }

    /** Runs the scheduled tick of the middle part of a board now. */
    private static void update(GameTestHelper helper, BlockPos middle) {
        helper.getLevel().getBlockState(middle).tick(helper.getLevel(), middle, helper.getLevel().getRandom());
    }
}
