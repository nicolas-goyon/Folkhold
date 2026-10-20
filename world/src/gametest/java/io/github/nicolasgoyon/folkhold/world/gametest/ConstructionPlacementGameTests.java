package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BlockKind;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildBlock;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionPlacement;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRuns;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPlacePayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewPayload;
import io.github.nicolasgoyon.folkhold.world.network.ConstructionPreviewPayload.Entry;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.players.ServerOpListEntry;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the construction tool of the operators: the blocks of the ghost, the placement with a turn and a
 * mirror, and the checks of the server.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstructionPlacementGameTests {
    private static final ResourceLocation COTTAGE =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_cottage");
    private static final int STEPS_PER_TICK = 20;
    /** The test waits this number of ticks after the last step. */
    private static final int SETTLE_TICKS = 10;
    /** The scaffolding stands at this horizontal distance from the blocks of the target or nearer. */
    private static final int SCAFFOLDING_MARGIN = 2;

    private ConstructionPlacementGameTests() {
    }

    /**
     * The ghost has the blocks of the template that are not air: the blocks of the house that the template places in
     * the level, at the same positions.
     */
    @GameTest(template = "arena", batch = "construction_place_checks", skyAccess = true)
    public static void previewHasTheBlocksOfTheTemplate(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = cottage(level);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        template.placeInWorld(level, origin, origin,
                new StructurePlaceSettings().addProcessor(JigsawReplacementProcessor.INSTANCE), level.getRandom(),
                Block.UPDATE_CLIENTS);

        ConstructionPreviewPayload preview = ConstructionPlacement.preview(level, COTTAGE, template);
        helper.assertTrue(preview.size().equals(template.getSize()), "The ghost must have the size of the template");
        List<String> problems = new ArrayList<>();
        for (Entry entry : preview.blocks()) {
            BlockPos pos = origin.offset(entry.x(), entry.y(), entry.z());
            BlockState state = preview.palette().get(entry.state());
            if (state.isAir() || !level.getBlockState(pos).is(state.getBlock())) {
                problems.add(pos + ": " + state + " instead of " + level.getBlockState(pos));
            }
        }
        int placed = 0;
        BlockPos last = origin.offset(template.getSize()).offset(-1, -1, -1);
        for (BlockPos pos : BlockPos.betweenClosed(origin, last)) {
            placed += level.getBlockState(pos).isAir() ? 0 : 1;
        }
        helper.assertTrue(problems.isEmpty(), "Blocks of the ghost different from the house: " + problems);
        helper.assertTrue(placed == preview.blocks().size(), "The ghost must have " + placed + " blocks, not "
                + preview.blocks().size());
        helper.succeed();
    }

    /**
     * An operator places the cottage with a turn and a mirror. The construction builds the house of the target with
     * the same turn and mirror, and the scaffolding goes away.
     */
    @GameTest(template = "arena_large", timeoutTicks = 400, batch = "construction_place", skyAccess = true)
    public static void placeBuildsTheGhost(GameTestHelper helper) {
        TestSupport.buildFloor(helper, 32);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = cottage(level);
        ServerPlayer player = operator(helper);
        BlockPos anchor = helper.absolutePos(new BlockPos(16, 1, 16));
        Rotation rotation = Rotation.CLOCKWISE_90;
        Mirror mirror = Mirror.FRONT_BACK;
        BlockPos origin = GhostPlacement.originFor(template.getSize(), rotation, mirror, anchor);
        BoundingBox box = GhostPlacement.box(template.getSize(), rotation, mirror, origin);
        Target expected = ConstructionTargets.fromTemplate(level, template, origin,
                GhostPlacement.settings(rotation, mirror));

        boolean started = ConstructionPlacement.place(player,
                new ConstructionPlacePayload(COTTAGE, origin, rotation, mirror, STEPS_PER_TICK));
        removeOperator(level, player);
        helper.assertTrue(started && ConstructionRuns.count() == 1, "The placement must start a construction");
        BlockPos from = new BlockPos(box.minX(), box.minY(), box.minZ());
        BlockPos to = new BlockPos(box.maxX(), box.maxY(), box.maxZ());
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ConstructionRuns.count() == 0, "The construction must end"))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    List<String> differences = differences(expected, ConstructionTargets.fromLevel(level, from, to));
                    helper.assertTrue(differences.isEmpty(), "Blocks different from the ghost: " + differences);
                    for (BlockPos pos : BlockPos.betweenClosed(from.offset(-SCAFFOLDING_MARGIN, 0,
                            -SCAFFOLDING_MARGIN), to.offset(SCAFFOLDING_MARGIN, 0, SCAFFOLDING_MARGIN))) {
                        helper.assertTrue(!level.getBlockState(pos).is(Blocks.SCAFFOLDING),
                                "Scaffolding at " + pos.immutable());
                    }
                })
                .thenSucceed();
    }

    /** The server refuses a placement from a player who is not an operator. */
    @GameTest(template = "arena", batch = "construction_place_checks")
    public static void placeNeedsAnOperator(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 1, 8))));
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));

        boolean started = ConstructionPlacement.place(player,
                new ConstructionPlacePayload(COTTAGE, origin, Rotation.NONE, Mirror.NONE, STEPS_PER_TICK));
        level.getServer().getPlayerList().remove(player);
        helper.assertFalse(started, "A player who is not an operator must not start a construction");
        helper.assertTrue(ConstructionRuns.count() == 0, "No construction must start");
        helper.succeed();
    }

    /** The server refuses a placement far from the operator. */
    @GameTest(template = "arena", batch = "construction_place_checks")
    public static void placeRefusesAFarStructure(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = operator(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3)).east(ConstructionPlacement.MAX_DISTANCE + 20);

        boolean started = ConstructionPlacement.place(player,
                new ConstructionPlacePayload(COTTAGE, origin, Rotation.NONE, Mirror.NONE, STEPS_PER_TICK));
        removeOperator(level, player);
        helper.assertFalse(started, "A structure far from the operator must not start a construction");
        helper.assertTrue(ConstructionRuns.count() == 0, "No construction must start");
        helper.succeed();
    }

    /** A mock player in the middle of the test, with the permission level of the operators. */
    private static ServerPlayer operator(GameTestHelper helper) {
        ServerPlayer player = TestSupport.mockPlayer(helper);
        player.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(new BlockPos(8, 1, 8))));
        // The operator level of the GameTest server is 0: the entry sets the level of the operators.
        helper.getLevel().getServer().getPlayerList().getOps()
                .add(new ServerOpListEntry(player.getGameProfile(), 2, false));
        return player;
    }

    private static void removeOperator(ServerLevel level, ServerPlayer player) {
        level.getServer().getPlayerList().getOps().remove(player.getGameProfile());
        level.getServer().getPlayerList().remove(player);
    }

    private static StructureTemplate cottage(ServerLevel level) {
        return level.getStructureManager().get(COTTAGE).orElseThrow();
    }

    /**
     * The positions of the target where the level has a different block. A connected block only needs the same
     * block: it takes its shape from its neighbors.
     */
    private static List<String> differences(Target expected, Target actual) {
        List<String> differences = new ArrayList<>();
        for (Map.Entry<BuildPos, BuildBlock> entry : expected.blocks().entrySet()) {
            BuildBlock found = actual.at(entry.getKey());
            boolean same = entry.getValue().kind() == BlockKind.CONNECTED
                    ? blockName(found.state()).equals(blockName(entry.getValue().state()))
                    : found.sameBlock(entry.getValue());
            if (!same) {
                differences.add(entry.getKey() + ": " + found.state() + " instead of " + entry.getValue().state());
            }
        }
        return differences;
    }

    /** The block of a state as text, with no properties. */
    private static String blockName(String state) {
        int properties = state.indexOf('[');
        return properties < 0 ? state : state.substring(0, properties);
    }
}
