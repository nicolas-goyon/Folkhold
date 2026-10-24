package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BlockKind;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildBlock;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.ConstructionPlanner;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Step;
import io.github.nicolasgoyon.folkhold.core.sim.construction.StepType;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Target;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.BlockKinds;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRun;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionRuns;
import io.github.nicolasgoyon.folkhold.world.construction.ConstructionTargets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the construction plans: the run of a plan gives the blocks of the target, with no dropped items
 * and no falling blocks. See {@link TestSupport} for the templates.
 *
 * <p>The tests with high blocks have sky access: the barrier ceiling of the arena is not there, so a builder cannot
 * stand on it.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ConstructionGameTests {
    private static final int STEPS_PER_TICK = 8;
    /** The test waits this number of ticks after the last step: the ticks of the falling blocks and the fluids. */
    private static final int SETTLE_TICKS = 10;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    /** The scaffolding stands at this horizontal distance from the blocks of the target or nearer. */
    private static final int SCAFFOLDING_MARGIN = Reach.DEFAULT.horizontal();
    /** The first layer of the roof of the cottage, from the origin of the template. */
    private static final int COTTAGE_ROOF_LAYER = 5;

    private ConstructionGameTests() {
    }

    /**
     * A target with the difficult blocks:
     * <ul>
     *   <li>sand and gravel on a block;</li>
     *   <li>a wall torch, and a lantern under a beam;</li>
     *   <li>a sign with text on a post;</li>
     *   <li>a door and a bed;</li>
     *   <li>a fence line to a block, and a trapdoor on this block;</li>
     *   <li>a waterlogged slab.</li>
     * </ul>
     * The test puts the blocks, captures them as the target, and removes them. The run of the plan gives the target
     * again.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "construction_target")
    public static void runGivesTheTargetWithNoDrops(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(2, 1, 2));
        Map<BlockPos, BlockState> scene = new LinkedHashMap<>();
        BlockState stone = Blocks.STONE.defaultBlockState();
        // Sand and gravel on a block.
        scene.put(base, stone);
        scene.put(base.offset(0, 1, 0), Blocks.SAND.defaultBlockState());
        scene.put(base.offset(0, 2, 0), Blocks.GRAVEL.defaultBlockState());
        // A column with a wall torch on its west side.
        for (int y = 0; y <= 2; y++) {
            scene.put(base.offset(2, y, 0), stone);
        }
        scene.put(base.offset(1, 1, 0), Blocks.WALL_TORCH.defaultBlockState()
                .setValue(WallTorchBlock.FACING, Direction.WEST));
        // A post and a beam, with a lantern under the beam and a sign on the post.
        for (int y = 0; y <= 3; y++) {
            scene.put(base.offset(5, y, 0), stone);
        }
        scene.put(base.offset(4, 3, 0), stone);
        scene.put(base.offset(4, 2, 0), Blocks.LANTERN.defaultBlockState().setValue(LanternBlock.HANGING, true));
        BlockPos sign = base.offset(6, 1, 0);
        scene.put(sign, Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, Direction.EAST));
        // A door, a bed, a fence line to a block, and a trapdoor on this block.
        BlockState door = Blocks.OAK_DOOR.defaultBlockState().setValue(DoorBlock.FACING, Direction.SOUTH);
        scene.put(base.offset(0, 0, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        scene.put(base.offset(0, 1, 3), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
        BlockState bed = Blocks.RED_BED.defaultBlockState().setValue(BedBlock.FACING, Direction.SOUTH);
        scene.put(base.offset(2, 0, 3), bed.setValue(BedBlock.PART, BedPart.FOOT));
        scene.put(base.offset(2, 0, 4), bed.setValue(BedBlock.PART, BedPart.HEAD));
        for (int x = 4; x <= 6; x++) {
            scene.put(base.offset(x, 0, 3), Blocks.OAK_FENCE.defaultBlockState());
        }
        scene.put(base.offset(7, 0, 3), stone);
        scene.put(base.offset(7, 0, 4), Blocks.OAK_TRAPDOOR.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, Direction.SOUTH).setValue(TrapDoorBlock.HALF, Half.TOP));
        // A waterlogged slab in a basin.
        scene.put(base.offset(0, 0, 6), stone);
        scene.put(base.offset(2, 0, 6), stone);
        scene.put(base.offset(1, 0, 5), stone);
        scene.put(base.offset(1, 0, 7), stone);
        scene.put(base.offset(1, 0, 6), Blocks.OAK_SLAB.defaultBlockState().setValue(SlabBlock.WATERLOGGED, true));

        scene.forEach((pos, state) -> level.setBlock(pos, state, Block.UPDATE_ALL));
        scene.keySet().forEach(pos -> {
            BlockState state = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(state, level, pos);
            if (shaped != state) {
                level.setBlock(pos, shaped, Block.UPDATE_ALL);
            }
        });
        if (level.getBlockEntity(sign) instanceof SignBlockEntity signEntity) {
            signEntity.setText(signEntity.getFrontText().setMessage(0, Component.literal("Folkhold")), true);
            signEntity.setChanged();
        }
        BlockPos from = base;
        BlockPos to = base.offset(7, 3, 7);
        Target target = ConstructionTargets.fromLevel(level, from, to);
        helper.assertTrue(target.at(BlockKinds.toBuildPos(sign)).data().orElse("").contains("Folkhold"),
                "The target must keep the text of the sign");
        clear(level, from, to);

        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), Reach.DEFAULT);
        helper.assertTrue(plan.warnings().isEmpty(), "The plan must have no warnings: " + plan.warnings());
        runAndCompare(helper, plan, target, from, to);
    }

    /**
     * A village house loses its door, a wall block with its torch, the plank under a lantern, its bed and some roof
     * stairs. A wall block becomes dirt, and sand falls into the house. The run puts the house back as it was, with
     * scaffolding for the roof.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "construction_house", skyAccess = true)
    public static void runRestoresADamagedHouse(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = cottage(level);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        BlockPos from = origin;
        BlockPos to = origin.offset(template.getSize()).offset(-1, -1, -1);
        Target target = ConstructionTargets.fromLevel(level, from, to);
        Target fromTemplate = ConstructionTargets.fromTemplate(level, template, origin, new StructurePlaceSettings());
        List<String> templateDifferences = templateDifferences(fromTemplate, target);
        helper.assertTrue(templateDifferences.isEmpty() && fromTemplate.blocks().size() == target.blocks().size(),
                "The target of the template must be the placed house: " + templateDifferences);

        // The door goes with no updates. A wall block, the plank under the lantern and the foot of the bed go with
        // updates: the torch, the lantern and the head of the bed break.
        level.setBlock(origin.offset(4, 2, 1), AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(origin.offset(4, 1, 1), AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        level.setBlock(origin.offset(2, 3, 1), AIR, Block.UPDATE_ALL);
        level.setBlock(origin.offset(1, 0, 2), AIR, Block.UPDATE_ALL);
        level.setBlock(origin.offset(2, 1, 6), AIR, Block.UPDATE_ALL);
        level.setBlock(origin.offset(2, 7, 3), AIR, Block.UPDATE_ALL);
        level.setBlock(origin.offset(2, 7, 4), AIR, Block.UPDATE_ALL);
        level.setBlock(origin.offset(6, 7, 4), AIR, Block.UPDATE_ALL);
        level.setBlock(origin.offset(8, 3, 4), Blocks.DIRT.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(origin.offset(4, 1, 5), Blocks.SAND.defaultBlockState(), Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(origin.offset(2, 3, 0)).isAir(), "The torch must break");
        helper.assertTrue(level.getBlockState(origin.offset(1, 1, 2)).isAir(), "The lantern must break");
        helper.assertTrue(level.getBlockState(origin.offset(2, 1, 7)).isAir(), "The head of the bed must break");
        AABB area = AABB.encapsulatingFullBlocks(from, to).inflate(3.0);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(ItemEntity::discard);

        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), Reach.DEFAULT);
        helper.assertTrue(plan.warnings().isEmpty(), "The plan must have no warnings: " + plan.warnings());
        helper.assertTrue(plan.count(StepType.PLACE_SCAFFOLDING) > 0, "The roof must need scaffolding");
        helper.assertTrue(plan.count(StepType.REMOVE) == 2, "The plan must remove the dirt and the sand, not "
                + plan.count(StepType.REMOVE) + " blocks");
        runAndCompare(helper, plan, target, from, to);
    }

    /**
     * A block out of the reach of a builder on the floor: the plan adds a scaffolding column, and removes it at the
     * end. The scaffolding does not fall.
     */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "construction_scaffolding", skyAccess = true)
    public static void scaffoldingGoesAwayAtTheEnd(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos high = helper.absolutePos(new BlockPos(8, 7, 8));
        BlockState stone = Blocks.STONE.defaultBlockState();
        Target target = new Target(Map.of(BlockKinds.toBuildPos(high),
                BlockKinds.describe(high, stone, Optional.empty())));

        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), Reach.DEFAULT);
        helper.assertTrue(plan.warnings().isEmpty(), "The plan must have no warnings: " + plan.warnings());
        helper.assertTrue(plan.count(StepType.PLACE_SCAFFOLDING) > 0, "The block must need scaffolding");
        BlockPos floor = helper.absolutePos(new BlockPos(5, 1, 5));
        runAndCompare(helper, plan, target, floor, high.offset(3, 0, 3));
    }

    /**
     * The plan of a new village house. The builder stands on the ground or on the floor of the house, and not on the
     * top of a new wall. Thus the scaffolding comes before the roof.
     */
    @GameTest(template = "arena", batch = "construction_roof", skyAccess = true)
    public static void newHouseHasScaffoldingForTheRoof(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = cottage(level);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        Target target = ConstructionTargets.fromTemplate(level, template, origin, new StructurePlaceSettings());

        Plan plan = ConstructionPlanner.plan(target, ConstructionTargets.view(level), Reach.DEFAULT);
        helper.assertTrue(plan.warnings().isEmpty(), "The plan must have no warnings: " + plan.warnings());
        Set<BuildPos> scaffolding = new HashSet<>();
        int firstScaffolding = -1;
        int firstRoof = -1;
        List<Step> steps = plan.steps();
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            if (step.type() == StepType.PLACE_SCAFFOLDING) {
                scaffolding.add(step.pos());
                firstScaffolding = firstScaffolding < 0 ? i : firstScaffolding;
            } else if (step.type() == StepType.PLACE) {
                firstRoof = firstRoof < 0 && step.pos().y() >= origin.getY() + COTTAGE_ROOF_LAYER ? i : firstRoof;
                helper.assertTrue(step.stand().isPresent(), "No position to stand for " + step.pos());
                BuildPos floor = step.stand().get().below();
                // The floor of the arena, or the floor of the house.
                boolean ground = floor.y() <= origin.getY();
                helper.assertTrue(ground || scaffolding.contains(floor),
                        "The builder stands on " + floor + " for " + step.pos());
            }
        }
        helper.assertTrue(firstScaffolding >= 0 && firstScaffolding < firstRoof,
                "The scaffolding must come before the roof");
        helper.succeed();
    }

    /**
     * The command {@code build} builds a village house on an empty floor, step by step. The house has the blocks of
     * the template. A connected block can have another shape: it connects to its neighbors.
     */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "construction_build", skyAccess = true)
    public static void buildCommandBuildsAHouse(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        StructureTemplate template = cottage(level);
        BlockPos origin = helper.absolutePos(new BlockPos(3, 1, 3));
        BlockPos to = origin.offset(template.getSize()).offset(-1, -1, -1);
        Target expected = ConstructionTargets.fromTemplate(level, template, origin, new StructurePlaceSettings());
        List<String> problems = watchProblems(helper, origin, to);

        command(level, origin, "folkhold construction build " + FolkholdWorld.MOD_ID + ":village/house_cottage 20");
        helper.assertTrue(ConstructionRuns.count() == 1, "The command must start a construction");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ConstructionRuns.count() == 0, "The construction must end"))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    checkNoScaffolding(level, origin.offset(-SCAFFOLDING_MARGIN, 0, -SCAFFOLDING_MARGIN),
                            to.offset(SCAFFOLDING_MARGIN, 0, SCAFFOLDING_MARGIN), problems);
                    List<String> differences = templateDifferences(expected,
                            ConstructionTargets.fromLevel(level, origin, to));
                    helper.assertTrue(differences.isEmpty(), "Blocks different from the template: " + differences);
                    helper.assertTrue(problems.isEmpty(), "Problems during the construction: " + problems);
                })
                .thenSucceed();
    }

    /**
     * The command {@code capture} keeps the blocks of a box. A block of the box goes, and its torch breaks. The command
     * {@code restore} puts back the block and the torch.
     */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "construction_commands")
    public static void restoreCommandPutsBackTheCapture(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos base = helper.absolutePos(new BlockPos(6, 1, 6));
        BlockPos torch = base.offset(-1, 1, 0);
        for (int y = 0; y <= 2; y++) {
            level.setBlock(base.above(y), Blocks.STONE.defaultBlockState(), Block.UPDATE_ALL);
        }
        level.setBlock(torch, Blocks.WALL_TORCH.defaultBlockState().setValue(WallTorchBlock.FACING, Direction.WEST),
                Block.UPDATE_ALL);
        BlockPos from = base.offset(-2, 0, -2);
        BlockPos to = base.offset(2, 3, 2);
        Target target = ConstructionTargets.fromLevel(level, from, to);
        command(level, base, "folkhold construction capture " + coordinates(from) + " " + coordinates(to));
        level.setBlock(base.above(), AIR, Block.UPDATE_ALL);
        helper.assertTrue(level.getBlockState(torch).isAir(), "The torch must break");
        level.getEntitiesOfClass(ItemEntity.class, AABB.encapsulatingFullBlocks(from, to).inflate(3.0))
                .forEach(ItemEntity::discard);
        List<String> problems = watchProblems(helper, from, to);

        command(level, base, "folkhold construction restore 4");
        helper.assertTrue(ConstructionRuns.count() == 1, "The command must start a construction");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(ConstructionRuns.count() == 0, "The construction must end"))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    List<String> differences = differences(target, ConstructionTargets.fromLevel(level, from, to));
                    helper.assertTrue(differences.isEmpty(), "Blocks different from the capture: " + differences);
                    helper.assertTrue(problems.isEmpty(), "Problems during the construction: " + problems);
                })
                .thenSucceed();
    }

    /**
     * Starts the run of a plan. After the run, checks that the blocks are equal to the target, and that no scaffolding
     * stays in the box. During the run, checks that no item drops and no block falls.
     */
    private static void runAndCompare(GameTestHelper helper, Plan plan, Target target, BlockPos from, BlockPos to) {
        ServerLevel level = helper.getLevel();
        ConstructionRun run = new ConstructionRun(level, plan, STEPS_PER_TICK);
        ConstructionRuns.start(run, finished -> {
        });
        List<String> problems = watchProblems(helper, from, to);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(run.finished(), "The run must apply all its steps"))
                .thenIdle(SETTLE_TICKS)
                .thenExecute(() -> {
                    checkNoScaffolding(level, from, to, problems);
                    List<String> differences = differences(target, ConstructionTargets.fromLevel(level, from, to));
                    helper.assertTrue(differences.isEmpty(), "Blocks different from the target: " + differences);
                    helper.assertTrue(problems.isEmpty(), "Problems during the run: " + problems);
                })
                .thenSucceed();
    }

    /**
     * Watches the dropped items and the falling blocks near a box, at each tick of the test.
     *
     * @return the list of the problems, which the test fills at each tick
     */
    private static List<String> watchProblems(GameTestHelper helper, BlockPos from, BlockPos to) {
        ServerLevel level = helper.getLevel();
        AABB area = AABB.encapsulatingFullBlocks(from, to).inflate(3.0);
        List<String> problems = new ArrayList<>();
        helper.onEachTick(() -> {
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, area)) {
                problems.add("dropped item " + item.getItem() + " at " + item.blockPosition());
                item.discard();
            }
            for (FallingBlockEntity falling : level.getEntitiesOfClass(FallingBlockEntity.class, area)) {
                problems.add("falling block " + falling.getBlockState() + " at " + falling.blockPosition());
                falling.discard();
            }
        });
        return problems;
    }

    private static void checkNoScaffolding(ServerLevel level, BlockPos from, BlockPos to, List<String> problems) {
        for (BlockPos pos : BlockPos.betweenClosed(from, to)) {
            if (level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
                problems.add("scaffolding at " + pos.immutable());
            }
        }
    }

    /** Runs a command as an operator at a position of the level, with no messages. */
    private static void command(ServerLevel level, BlockPos pos, String command) {
        CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level)
                .withPosition(Vec3.atBottomCenterOf(pos)).withPermission(4).withSuppressedOutput();
        level.getServer().getCommands().performPrefixedCommand(source, command);
    }

    private static String coordinates(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    private static StructureTemplate cottage(ServerLevel level) {
        return level.getStructureManager()
                .get(ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/house_cottage"))
                .orElseThrow();
    }

    /** The positions of a target where another target has a different block, with the two states. */
    private static List<String> differences(Target expected, Target actual) {
        List<String> differences = new ArrayList<>();
        for (Map.Entry<BuildPos, BuildBlock> entry : expected.blocks().entrySet()) {
            BuildBlock found = actual.at(entry.getKey());
            if (!found.sameBlock(entry.getValue())) {
                differences.add(entry.getKey() + ": " + found.state() + found.data().orElse("") + " instead of "
                        + entry.getValue().state() + entry.getValue().data().orElse(""));
            }
        }
        return differences;
    }

    /**
     * The positions of the target of a template where another target has a different block. A connected block only
     * needs the same block: the placement gives it its shape from its neighbors, and the template file can have
     * another shape.
     */
    private static List<String> templateDifferences(Target template, Target actual) {
        List<String> differences = new ArrayList<>();
        for (Map.Entry<BuildPos, BuildBlock> entry : template.blocks().entrySet()) {
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

    /** Removes the blocks of a box with no updates and no drops, from the top to the bottom. */
    private static void clear(ServerLevel level, BlockPos from, BlockPos to) {
        for (int y = to.getY(); y >= from.getY(); y--) {
            for (int x = from.getX(); x <= to.getX(); x++) {
                for (int z = from.getZ(); z <= to.getZ(); z++) {
                    level.setBlock(new BlockPos(x, y, z), AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                }
            }
        }
    }
}
