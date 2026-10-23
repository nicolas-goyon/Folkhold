package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
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

    private VillageRepairGameTests() {
    }

    /**
     * The comparison of the repairs ignores the life of a village: open gates, lit braziers, the colors of the
     * banners, the soil, the connections of the fences, ice for water, the plants on an empty position and the
     * jigsaw blocks. A missing block, a block of another kind and stairs with another facing are damaged.
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
