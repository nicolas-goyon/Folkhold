package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.structure.FoundationProcessor;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the foundation under the templates of the villages ({@link FoundationProcessor}). The tests build
 * a ground of dirt with a hole, and give the processor the blocks of a small template above it.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class FoundationGameTests {
    private static final BlockState COBBLESTONE = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    /** The lowest layer of the template is at this height in the test. */
    private static final int TEMPLATE_Y = 2;

    private FoundationGameTests() {
    }

    /**
     * Under each block of the lowest layer, the foundation fills the hole down to the ground. A block on the ground,
     * a block above the lowest layer and a block with no collision shape get no foundation.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_foundation")
    public static void foundationFillsTheHoleUnderTheLowestLayer(GameTestHelper helper) {
        buildGround(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(2, TEMPLATE_Y, 2));
        List<StructureBlockInfo> template = List.of(
                new StructureBlockInfo(new BlockPos(0, 0, 0), PLANKS, null),
                new StructureBlockInfo(new BlockPos(1, 0, 0), PLANKS, null),
                new StructureBlockInfo(new BlockPos(0, 1, 0), PLANKS, null),
                new StructureBlockInfo(new BlockPos(0, 0, 1), Blocks.POPPY.defaultBlockState(), null));
        List<StructureBlockInfo> placed = template.stream()
                .map(block -> new StructureBlockInfo(block.pos().offset(origin), block.state(), null)).toList();

        List<StructureBlockInfo> result = new FoundationProcessor(COBBLESTONE, 8)
                .finalizeProcessing(helper.getLevel(), origin, origin, template, placed, new StructurePlaceSettings());

        List<BlockPos> foundation = result.subList(placed.size(), result.size()).stream()
                .peek(block -> helper.assertTrue(block.state() == COBBLESTONE, "The foundation is of cobblestone"))
                .map(StructureBlockInfo::pos).toList();
        helper.assertTrue(result.subList(0, placed.size()).equals(placed), "The blocks of the template stay");
        helper.assertTrue(foundation.equals(List.of(helper.absolutePos(new BlockPos(2, 1, 2)),
                helper.absolutePos(new BlockPos(2, 0, 2)))), "The foundation must fill the hole: " + foundation);
        helper.succeed();
    }

    /** The foundation goes down the maximum depth at most. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_foundation_depth")
    public static void foundationStopsAtTheMaximumDepth(GameTestHelper helper) {
        buildGround(helper);
        BlockPos origin = helper.absolutePos(new BlockPos(2, TEMPLATE_Y, 2));
        List<StructureBlockInfo> template = List.of(new StructureBlockInfo(BlockPos.ZERO, PLANKS, null));
        List<StructureBlockInfo> placed = List.of(new StructureBlockInfo(origin, PLANKS, null));

        List<StructureBlockInfo> result = new FoundationProcessor(COBBLESTONE, 1)
                .finalizeProcessing(helper.getLevel(), origin, origin, template, placed, new StructurePlaceSettings());

        helper.assertTrue(result.size() == 2 && result.get(1).pos().equals(origin.below()),
                "The foundation must have 1 block: " + result);
        helper.succeed();
    }

    /**
     * Over water, a foundation with a stilt block puts stilts at the corners of the lowest layer and every 3 blocks
     * along its edges, down to the ground. The other columns over water get no foundation.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_foundation_stilts")
    public static void buildingOverWaterStandsOnStilts(GameTestHelper helper) {
        int size = 7;
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
            }
        }
        BlockPos origin = helper.absolutePos(new BlockPos(0, TEMPLATE_Y, 0));
        List<StructureBlockInfo> template = new ArrayList<>();
        for (int x = 0; x < size; x++) {
            for (int z = 0; z < size; z++) {
                template.add(new StructureBlockInfo(new BlockPos(x, 0, z), PLANKS, null));
            }
        }
        List<StructureBlockInfo> placed = template.stream()
                .map(block -> new StructureBlockInfo(block.pos().offset(origin), block.state(), null)).toList();
        BlockState log = Blocks.SPRUCE_LOG.defaultBlockState();

        List<StructureBlockInfo> result = new FoundationProcessor(COBBLESTONE, 8, Optional.of(log))
                .finalizeProcessing(helper.getLevel(), origin, origin, template, placed, new StructurePlaceSettings());

        Set<BlockPos> stilts = new HashSet<>();
        for (StructureBlockInfo block : result.subList(placed.size(), result.size())) {
            helper.assertTrue(block.state() == log, "Over water, the foundation is of stilts: " + block);
            stilts.add(block.pos());
        }
        Set<BlockPos> expected = new HashSet<>();
        for (int x : new int[] {0, 3, 6}) {
            for (int z : new int[] {0, 3, 6}) {
                if (x != 3 || z != 3) {
                    expected.add(helper.absolutePos(new BlockPos(x, 1, z)));
                }
            }
        }
        helper.assertTrue(stilts.equals(expected), "Expected the stilts " + expected + ", found " + stilts);
        helper.succeed();
    }

    /** A ground of dirt in the 2 lowest layers of the test, with a hole of 2 blocks at (2, 2). */
    private static void buildGround(GameTestHelper helper) {
        for (int x = 0; x < 6; x++) {
            for (int z = 0; z < 6; z++) {
                for (int y = 0; y < TEMPLATE_Y; y++) {
                    boolean hole = x == 2 && z == 2;
                    helper.setBlock(new BlockPos(x, y, z), hole ? Blocks.AIR : Blocks.DIRT);
                }
            }
        }
    }
}
