package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.block.QuestBoardBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.core.sim.construction.StepType;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quest;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestKind;
import io.github.nicolasgoyon.folkhold.core.sim.quest.QuestState;
import io.github.nicolasgoyon.folkhold.core.sim.quest.Quests;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BlockKinds;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRun;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.RepairRules;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LayeredCauldronBlock;
import net.minecraft.world.level.block.LecternBlock;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.entity.ChestBlockEntity;
import net.minecraft.world.level.block.entity.LecternBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.common.util.BlockSnapshot;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the repairs of the faction villages (see {@link VillageRepairs}). The repair tests use the
 * template {@code village/center}: the reference of the village is this template at its position in the test.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageRepairGameTests {
    private static final ResourceLocation CENTER = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/center");
    /** A part of the north palisade, in the coordinates of the template: 3 logs above the base of the wall. */
    private static final List<BlockPos> WALL = List.of(new BlockPos(8, 4, 1), new BlockPos(8, 5, 1),
            new BlockPos(8, 6, 1));
    /** The torch on the inner face of the palisade, on the top log of {@link #WALL}. */
    private static final BlockPos TORCH = new BlockPos(8, 6, 2);
    /** An empty position on the grass, inside the palisade. */
    private static final BlockPos PLAYER_BLOCK = new BlockPos(8, 4, 4);
    private static final BlockPos GRASS = new BlockPos(9, 4, 4);
    /** A torch of the palisade in the box of a construction site of the village. */
    private static final BlockPos CONSTRUCTION_TORCH = new BlockPos(12, 6, 2);
    private static final int BUILDERS = 2;
    /** The quantity of each delivered material in the stock. */
    private static final int STOCK = 64;

    private VillageRepairGameTests() {
    }

    /**
     * The comparison of the repairs ignores the life of a village: open gates, lit braziers, the papers of the quest
     * board, the colors of the banners, the soil, the connections of the fences, ice for water, the plants on an
     * empty position and the jigsaw blocks. A missing block, a block of another kind and stairs with another facing
     * are damaged.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_repair_rules")
    public static void repairRulesIgnoreTheLifeOfTheVillage(GameTestHelper helper) {
        BlockState gate = Blocks.SPRUCE_FENCE_GATE.defaultBlockState();
        BlockState banner = Blocks.WHITE_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING,
                Direction.NORTH);
        BlockState stairs = Blocks.SPRUCE_STAIRS.defaultBlockState();
        assertDamaged(helper, false, gate.setValue(FenceGateBlock.OPEN, true), gate, "an open gate");
        assertDamaged(helper, false, ModBlocks.BRAZIER.get().defaultBlockState().setValue(BrazierBlock.LIT, true),
                ModBlocks.BRAZIER.get().defaultBlockState(), "a lit brazier");
        BlockState board = ModBlocks.QUEST_BOARD.get().defaultBlockState();
        assertDamaged(helper, false, board.setValue(QuestBoardBlock.PAPERS, 2), board.setValue(QuestBoardBlock.PAPERS,
                0), "a quest board with papers");
        assertDamaged(helper, false, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING,
                Direction.NORTH), banner, "a banner of the faction");
        assertDamaged(helper, true, Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING,
                Direction.SOUTH), banner, "a banner with another facing");
        assertDamaged(helper, false, ModBlocks.BRAZIER.get().defaultBlockState(),
                Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false), "a brazier for a campfire");
        assertDamaged(helper, false, Blocks.DIRT.defaultBlockState(), Blocks.GRASS_BLOCK.defaultBlockState(),
                "dirt for grass");
        assertDamaged(helper, false, Blocks.SPRUCE_FENCE.defaultBlockState().setValue(FenceBlock.NORTH, true),
                Blocks.SPRUCE_FENCE.defaultBlockState(), "a fence with another connection");
        assertDamaged(helper, false, Blocks.ICE.defaultBlockState(), Blocks.WATER.defaultBlockState(), "ice for water");
        assertDamaged(helper, false, Blocks.SHORT_GRASS.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                "grass on an empty position");
        assertDamaged(helper, false, Blocks.SNOW.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                "snow on an empty position");
        assertDamaged(helper, false, Blocks.JIGSAW.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                "a jigsaw block");
        assertDamaged(helper, true, Blocks.AIR.defaultBlockState(), Blocks.SPRUCE_LOG.defaultBlockState(),
                "a missing log");
        assertDamaged(helper, true, Blocks.STONE.defaultBlockState(), Blocks.AIR.defaultBlockState(),
                "stone on an empty position");
        assertDamaged(helper, true, Blocks.COBBLESTONE.defaultBlockState(), Blocks.SPRUCE_PLANKS.defaultBlockState(),
                "a block of another kind");
        assertDamaged(helper, true, stairs.setValue(StairBlock.FACING, Direction.SOUTH),
                stairs.setValue(StairBlock.FACING, Direction.NORTH), "stairs with another facing");
        helper.succeed();
    }

    /**
     * The comparison of the repairs ignores the states that the weather, the growth and the normal use change: a
     * cauldron that the rain or the snow fills, a cauldron with no water, a stem after the harvest of its fruit, a
     * rail that connects to a new rail and an eaten cake. A lava cauldron for a water cauldron is damaged. A player
     * action that gives another block is damaged: a candle cake that becomes a cake, a chipped anvil, a flower pot
     * with a plant.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_repair_rules")
    public static void repairRulesIgnoreTheStatesOfNormalUse(GameTestHelper helper) {
        BlockState waterCauldron = Blocks.WATER_CAULDRON.defaultBlockState().setValue(LayeredCauldronBlock.LEVEL, 3);
        assertDamaged(helper, false, waterCauldron.setValue(LayeredCauldronBlock.LEVEL, 1),
                Blocks.CAULDRON.defaultBlockState(), "a cauldron that the rain fills");
        assertDamaged(helper, false, Blocks.CAULDRON.defaultBlockState(), waterCauldron, "a cauldron with no water");
        assertDamaged(helper, false, Blocks.POWDER_SNOW_CAULDRON.defaultBlockState(),
                Blocks.CAULDRON.defaultBlockState(), "a cauldron that the snow fills");
        assertDamaged(helper, true, Blocks.LAVA_CAULDRON.defaultBlockState(), waterCauldron,
                "a lava cauldron for a water cauldron");
        assertDamaged(helper, false, Blocks.MELON_STEM.defaultBlockState().setValue(StemBlock.AGE, StemBlock.MAX_AGE),
                Blocks.ATTACHED_MELON_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING, Direction.NORTH),
                "a melon stem after the harvest");
        assertDamaged(helper, false, Blocks.PUMPKIN_STEM.defaultBlockState().setValue(StemBlock.AGE,
                StemBlock.MAX_AGE), Blocks.ATTACHED_PUMPKIN_STEM.defaultBlockState().setValue(AttachedStemBlock.FACING,
                Direction.EAST), "a pumpkin stem after the harvest");
        assertDamaged(helper, false, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.SOUTH_EAST),
                Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, RailShape.NORTH_SOUTH),
                "a rail that connects to a new rail");
        assertDamaged(helper, false, Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE,
                RailShape.ASCENDING_NORTH), Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE,
                RailShape.NORTH_SOUTH), "a powered rail that connects to a new rail");
        assertDamaged(helper, false, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, 3),
                Blocks.CAKE.defaultBlockState(), "an eaten cake");
        assertDamaged(helper, true, Blocks.CAKE.defaultBlockState().setValue(CakeBlock.BITES, 1),
                Blocks.CANDLE_CAKE.defaultBlockState(), "a candle cake that becomes a cake");
        assertDamaged(helper, true, Blocks.CHIPPED_ANVIL.defaultBlockState(), Blocks.ANVIL.defaultBlockState(),
                "a chipped anvil");
        assertDamaged(helper, true, Blocks.POTTED_POPPY.defaultBlockState(), Blocks.FLOWER_POT.defaultBlockState(),
                "a flower pot with a plant");
        helper.succeed();
    }

    /**
     * A repair of one part of a quest board with another facing: the plan changes the state of the part with no
     * removal, and the 8 other parts stay.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_repair_parts")
    public static void stateRepairKeepsTheOtherParts(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        QuestBoardBlock board = ModBlocks.QUEST_BOARD.get();
        BlockPos base = helper.absolutePos(new BlockPos(8, 1, 8));
        board.place(level, base, Direction.NORTH, Block.UPDATE_ALL);
        BlockPos middle = QuestBoardBlock.partPos(base, Direction.NORTH, 1, 1);
        BlockState expected = board.part(Direction.NORTH, 1, 1);
        level.setBlock(middle, board.part(Direction.EAST, 1, 1), Block.UPDATE_ALL);

        repairState(helper, middle, expected);

        for (int column = 0; column < QuestBoardBlock.SIZE; column++) {
            for (int row = 0; row < QuestBoardBlock.SIZE; row++) {
                BlockPos pos = QuestBoardBlock.partPos(base, Direction.NORTH, column, row);
                helper.assertTrue(board.isPart(level.getBlockState(pos), Direction.NORTH, column, row),
                        "The part " + column + "," + row + " of the board must stay: " + level.getBlockState(pos));
            }
        }
        helper.succeed();
    }

    /**
     * A repair of a chest with items and another facing: the plan changes the state with no removal, and the chest
     * keeps its items.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_repair_parts")
    public static void stateRepairKeepsTheItemsOfAChest(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockState expected = Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
        level.setBlock(pos, expected.setValue(ChestBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof ChestBlockEntity chest) {
            chest.setItem(0, new ItemStack(Items.BREAD, 5));
        }

        repairState(helper, pos, expected);

        helper.assertTrue(level.getBlockState(pos).equals(expected), "The chest must get the reference facing: "
                + level.getBlockState(pos));
        helper.assertTrue(level.getBlockEntity(pos) instanceof ChestBlockEntity chest
                && chest.getItem(0).is(Items.BREAD) && chest.getItem(0).getCount() == 5,
                "The chest must keep its items");
        helper.succeed();
    }

    /**
     * A repair of a lectern with a book and another facing: the lectern gets the reference facing, and keeps its book
     * and the value of the level for {@code has_book}.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_repair_parts")
    public static void stateRepairKeepsTheBookOfALectern(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(new BlockPos(8, 1, 8));
        BlockState expected = Blocks.LECTERN.defaultBlockState().setValue(LecternBlock.FACING, Direction.NORTH)
                .setValue(LecternBlock.HAS_BOOK, false);
        level.setBlock(pos, expected.setValue(LecternBlock.FACING, Direction.EAST), Block.UPDATE_ALL);
        LecternBlock.tryPlaceBook(null, level, pos, level.getBlockState(pos), new ItemStack(Items.WRITABLE_BOOK));
        helper.assertTrue(level.getBlockState(pos).getValue(LecternBlock.HAS_BOOK), "The lectern must have a book");

        repairState(helper, pos, expected);

        BlockState state = level.getBlockState(pos);
        helper.assertTrue(state.getValue(LecternBlock.FACING) == Direction.NORTH && state.getValue(
                LecternBlock.HAS_BOOK), "The lectern must get the reference facing and keep has_book: " + state);
        helper.assertTrue(level.getBlockEntity(pos) instanceof LecternBlockEntity lectern
                && lectern.getBook().is(Items.WRITABLE_BOOK), "The lectern must keep its book");
        helper.succeed();
    }

    /** Plans and applies the repair of one position: the plan must change the state with no removal. */
    private static void repairState(GameTestHelper helper, BlockPos pos, BlockState expected) {
        ServerLevel level = helper.getLevel();
        Target target = new Target(Map.of(BlockKinds.toBuildPos(pos),
                BlockKinds.describe(pos, expected, Optional.empty())));
        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), Reach.DEFAULT);
        helper.assertTrue(plan.count(StepType.REMOVE) == 0 && plan.count(StepType.PLACE) == 1,
                "The plan must change the state with no removal: " + plan.steps());
        new ConstructionRun(level, plan, 1).finish();
    }

    /**
     * A player places a block in a village: the repairs keep it. The player breaks it: the repairs do not keep it any
     * more.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_repair_player")
    public static void repairsKeepTheBlocksOfThePlayers(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos heartRelative = new BlockPos(8, 1, 8);
        helper.setBlock(heartRelative, ModBlocks.VILLAGE_HEART.get());
        BlockPos heart = helper.absolutePos(heartRelative);
        VillageHearts.register(level, heart);
        BlockPos pos = helper.absolutePos(new BlockPos(4, 1, 4));
        helper.setBlock(new BlockPos(4, 1, 4), Blocks.STONE);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        BlockSnapshot snapshot = BlockSnapshot.create(level.dimension(), level, pos);

        NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(snapshot, Blocks.AIR.defaultBlockState(), player));
        boolean kept = VillageRepairData.get(level).keeps(heart, pos);
        NeoForge.EVENT_BUS.post(new BlockEvent.BreakEvent(level, pos, Blocks.STONE.defaultBlockState(), player));
        boolean keptAfterBreak = VillageRepairData.get(level).keeps(heart, pos);
        helper.setBlock(heartRelative, Blocks.AIR);

        helper.assertTrue(kept, "The repairs must keep the block of the player");
        helper.assertFalse(keptAfterBreak, "The repairs must not keep a block that the player broke");
        helper.succeed();
    }

    /**
     * The builders repair a part of the palisade of a village and its torch. The repairs keep a block of a player,
     * the grass on the plaza and a torch in the box of a construction site. At the end, the village has no damaged
     * blocks, no fences of the site and no scaffolding.
     */
    @GameTest(template = "village/center", timeoutTicks = 3000, batch = "village_repair")
    public static void buildersRepairThePalisade(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        ServerLevel level = helper.getLevel();
        BlockPos heartPos = heart.getBlockPos();
        BuilderSites.stop(level);
        WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(1);
        VillageHearts.register(level, heartPos);
        helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
        BlockPos origin = templateOrigin(helper, heartPos);
        VillageReference reference = new VillageReference(List.of(new VillageReference.Piece(CENTER, origin,
                Rotation.NONE)));
        heart.setRepairReference(reference);
        long[] time = {TestSupport.NOON};
        helper.onEachTick(() -> level.setDayTime(time[0]));

        Map<BlockPos, BlockState> expected = new HashMap<>();
        for (BlockPos relative : List.of(TORCH, WALL.get(0), WALL.get(1), WALL.get(2), CONSTRUCTION_TORCH)) {
            BlockPos pos = origin.offset(relative);
            expected.put(pos, level.getBlockState(pos));
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        BlockPos playerBlock = origin.offset(PLAYER_BLOCK);
        level.setBlock(playerBlock, Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        VillageRepairData.get(level).addPlayerBlock(heartPos, playerBlock);
        BlockPos grass = origin.offset(GRASS);
        level.setBlock(grass, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
        BlockPos constructionTorch = origin.offset(CONSTRUCTION_TORCH);
        VillageRepairData.get(level).addConstruction(heartPos, BoundingBox.fromCorners(constructionTorch,
                constructionTorch.above()));
        Set<BlockPos> damaged = VillageRepairs.damaged(level, heart, reference);
        Set<BlockPos> wall = WALL.stream().map(origin::offset).collect(Collectors.toSet());
        wall.add(origin.offset(TORCH));
        helper.assertTrue(damaged.equals(wall), "The damaged blocks must be the wall and its torch: "
                + damaged.stream().map(pos -> pos.subtract(origin)).toList());

        List<Villager> builders = new ArrayList<>();
        for (int i = 0; i < BUILDERS; i++) {
            Villager builder = VillageFolk.spawn(level, heartPos, ModProfessions.BUILDER.get());
            helper.assertTrue(builder != null, "The builder must spawn");
            builders.add(builder);
        }
        BuilderSite[] site = new BuilderSite[1];

        helper.startSequence()
                .thenExecute(() -> VillageRepairs.check(level, heart))
                .thenExecute(() -> {
                    List<BuilderSite> sites = BuilderSites.of(level, heartPos);
                    helper.assertTrue(sites.size() == 1 && sites.get(0).isRepair(),
                            "The village must start one repair site: " + sites.size());
                    site[0] = sites.get(0);
                    helper.assertTrue(site[0].repairPositions().equals(wall),
                            "The repair must be the wall and its torch: " + site[0].repairPositions().size());
                })
                .thenWaitUntil(() -> helper.assertTrue(BuilderSites.of(level, heartPos).isEmpty(),
                        "The builders must finish the repair: " + site[0].phase() + " " + site[0].status() + " "
                        + site[0].appliedSteps() + "/" + site[0].totalSteps()))
                .thenExecute(() -> {
                    for (BlockPos pos : wall) {
                        helper.assertFalse(RepairRules.isDamaged(level.getBlockState(pos), expected.get(pos)),
                                "The block at " + pos.subtract(origin) + " must be repaired: " + level.getBlockState(pos));
                    }
                    helper.assertTrue(level.getBlockState(playerBlock).is(Blocks.STONE),
                            "The repair must keep the block of the player");
                    helper.assertTrue(level.getBlockState(grass).is(Blocks.SHORT_GRASS),
                            "The repair must keep the grass on the plaza");
                    helper.assertTrue(level.getBlockState(constructionTorch).isAir(),
                            "The repair must keep the box of the construction site");
                    Set<BlockPos> left = VillageRepairs.damaged(level, heart, reference);
                    helper.assertTrue(left.isEmpty(), "The village must have no damaged blocks after the repair: "
                            + left.stream().map(pos -> pos.subtract(origin) + " " + level.getBlockState(pos))
                            .toList());
                    level.setBlock(constructionTorch, expected.get(constructionTorch),
                            Block.UPDATE_ALL);
                    level.setBlock(playerBlock, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                    level.setBlock(grass, Blocks.AIR.defaultBlockState(),
                            Block.UPDATE_ALL);
                    VillageRepairData.get(level).forget(heartPos);
                    builders.forEach(Entity::discard);
                    // A heart of this template near a later test can take the bank chests of that test.
                    level.setBlock(heartPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(WorldConfig.CONSTRUCTION_BUILDER_TICKS.getDefault());
                })
                .thenSucceed();
    }

    /**
     * A repair asks at once for the materials that the stock does not have: the village offers delivery quests long
     * before the wait of the common materials ({@link WorldConfig#CONSTRUCTION_MATERIAL_QUEST_MINUTES}). When the
     * stock has the materials, the builders repair the wall, and the offers go away.
     */
    @GameTest(template = "village/center", timeoutTicks = 3000, batch = "village_repair_quests")
    public static void repairAsksForItsMaterialsAtOnce(GameTestHelper helper) {
        VillageHeartBlockEntity heart = TestSupport.findHeart(helper);
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        BlockPos heartPos = heart.getBlockPos();
        BuilderSites.stop(level);
        WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(1);
        VillageHearts.register(level, heartPos);
        helper.assertTrue(heart.getLayout().scan(level, heartPos), "The heart must scan the village");
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(server, heart).orElseThrow();
        Settlements.startEconomy(server, settlement);
        TestSupport.emptyStock(settlement);
        BlockPos origin = templateOrigin(helper, heartPos);
        VillageReference reference = new VillageReference(List.of(new VillageReference.Piece(CENTER, origin,
                Rotation.NONE)));
        heart.setRepairReference(reference);
        long[] time = {TestSupport.NOON};
        helper.onEachTick(() -> level.setDayTime(time[0]));
        for (BlockPos relative : WALL) {
            level.setBlock(origin.offset(relative), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
        List<Villager> builders = new ArrayList<>();
        for (int i = 0; i < BUILDERS; i++) {
            Villager builder = VillageFolk.spawn(level, heartPos, ModProfessions.BUILDER.get());
            helper.assertTrue(builder != null, "The builder must spawn");
            builders.add(builder);
        }
        Quests quests = QuestData.get(server).quests();
        List<Quest> offers = new ArrayList<>();
        long[] askedAt = {-1};
        BuilderSite[] site = new BuilderSite[1];

        helper.startSequence()
                .thenExecute(() -> {
                    site[0] = VillageRepairs.check(level, heart).orElse(null);
                    helper.assertTrue(site[0] != null && site[0].isRepair(), "The repair must start");
                })
                .thenWaitUntil(() -> {
                    offers.clear();
                    site[0].materialQuests().values().forEach(id -> quests.byId(id).ifPresent(offers::add));
                    helper.assertTrue(!offers.isEmpty(), "The repair must ask for its materials: "
                            + site[0].phase() + " " + site[0].status());
                    askedAt[0] = helper.getTick();
                })
                .thenExecute(() -> {
                    helper.assertTrue(askedAt[0] < SharedConstants.TICKS_PER_MINUTE,
                            "The repair must not wait for the common materials: tick " + askedAt[0]);
                    for (Quest offer : offers) {
                        helper.assertTrue(offer.kind() == QuestKind.DELIVERY && offer.count() > 0
                                && offer.state() == QuestState.OFFERED, "A delivery of a missing material: " + offer);
                        // The players deliver the materials: they go into the stock of the village.
                        settlement.economy().stock().set(offer.target(), STOCK);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(BuilderSites.of(level, heartPos).isEmpty(),
                        "The builders must finish the repair: " + site[0].phase() + " " + site[0].status() + " "
                        + site[0].appliedSteps() + "/" + site[0].totalSteps()))
                .thenExecute(() -> {
                    boolean offered = offers.stream().anyMatch(offer -> quests.byId(offer.id())
                            .filter(quest -> quest.state() == QuestState.OFFERED).isPresent());
                    builders.forEach(Entity::discard);
                    level.setBlock(heartPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                    WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(WorldConfig.CONSTRUCTION_BUILDER_TICKS.getDefault());
                    helper.assertFalse(offered, "The repair must withdraw its offers when the stock has the items");
                })
                .thenSucceed();
    }

    /** The position of the origin of the template {@code village/center} in the test: from its village heart. */
    private static BlockPos templateOrigin(GameTestHelper helper, BlockPos heartPos) {
        StructureTemplate template = helper.getLevel().getStructureManager().get(CENTER)
                .orElseThrow(() -> new GameTestAssertException("No template " + CENTER));
        Optional<BlockPos> local = ConstructionTargets.templateBlocks(helper.getLevel(), template).stream()
                .filter(block -> block.state().is(ModBlocks.VILLAGE_HEART.get()))
                .map(ConstructionTargets.TemplateBlock::pos).findFirst();
        return heartPos.subtract(local.orElseThrow(() -> new GameTestAssertException("No heart in " + CENTER)));
    }

    private static void assertDamaged(GameTestHelper helper, boolean damaged, BlockState actual, BlockState expected,
            String what) {
        helper.assertTrue(RepairRules.isDamaged(actual, expected) == damaged,
                (damaged ? "The repairs must change " : "The repairs must keep ") + what + ": " + actual + " for "
                + expected);
    }
}
