package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the stairs at the gates of a village ({@link VillageExits}). The heart is a position far from
 * the test area, so the gates of the test are gates of the palisade. See {@link TestSupport}.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GateStairsGameTests {
    /** The gate line: 3 fence gates side by side, from x = 6 to x = 8, at z = 2. Outside is +z. */
    private static final int GATE_Z = 2;

    private GateStairsGameTests() {
    }

    /** The fence gates of the test, at a height, facing south (out of the village). */
    private static List<BlockPos> gates(GameTestHelper helper, int y) {
        List<BlockPos> gates = new ArrayList<>();
        for (int x = 6; x <= 8; x++) {
            BlockPos pos = new BlockPos(x, y, GATE_Z);
            helper.setBlock(pos, Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                    .setValue(FenceGateBlock.FACING, Direction.SOUTH));
            gates.add(helper.absolutePos(pos));
        }
        return gates;
    }

    /** A heart far inside the village, on the north side of the gates. */
    private static BlockPos heart(GameTestHelper helper) {
        return helper.absolutePos(new BlockPos(7, 1, GATE_Z - VillageExits.WALL_DISTANCE - 2));
    }

    private static boolean isStep(GameTestHelper helper, BlockPos pos, Direction high) {
        BlockState state = helper.getLevel().getBlockState(helper.absolutePos(pos));
        return state.is(Blocks.COBBLESTONE_STAIRS) && state.getValue(StairBlock.FACING) == high;
    }

    /** The village floor is 2 blocks above the terrain: 2 steps go down, with a support under the first step. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "gate_stairs_down")
    public static void lowerTerrainGetsStairsDown(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        // The village floor: stone at y = 2 up to the gate line. The gates stand at y = 3.
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z <= GATE_Z; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.STONE);
            }
        }
        List<BlockPos> gates = gates(helper, 3);

        int stairs = VillageExits.build(helper.getLevel(), heart(helper), gates);

        helper.assertTrue(stairs == 6, "Expected 2 steps for 3 columns, found " + stairs + " stair blocks");
        for (int x = 6; x <= 8; x++) {
            helper.assertTrue(isStep(helper, new BlockPos(x, 2, GATE_Z + 1), Direction.NORTH)
                    && isStep(helper, new BlockPos(x, 1, GATE_Z + 2), Direction.NORTH),
                    "The steps must go down from the gate, with the high side to the gate");
            helper.assertBlockPresent(Blocks.COBBLESTONE, new BlockPos(x, 1, GATE_Z + 1));
        }
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(7, 1, GATE_Z + 3));
        helper.succeed();
    }

    /** The terrain is 2 blocks above the village floor: 2 steps go up, and the terrain above them goes away. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "gate_stairs_up")
    public static void higherTerrainGetsStairsUp(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int x = 0; x < 16; x++) {
            helper.setBlock(new BlockPos(x, 1, GATE_Z + 1), Blocks.DIRT);
            for (int z = GATE_Z + 2; z < 16; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.DIRT);
            }
        }
        List<BlockPos> gates = gates(helper, 1);

        int stairs = VillageExits.build(helper.getLevel(), heart(helper), gates);

        helper.assertTrue(stairs == 6, "Expected 2 steps for 3 columns, found " + stairs + " stair blocks");
        for (int x = 6; x <= 8; x++) {
            helper.assertTrue(isStep(helper, new BlockPos(x, 1, GATE_Z + 1), Direction.SOUTH)
                    && isStep(helper, new BlockPos(x, 2, GATE_Z + 2), Direction.SOUTH),
                    "The steps must go up from the gate, with the high side out of the village");
        }
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(7, 2, GATE_Z + 3));
        helper.succeed();
    }

    /** A fence gate near the heart is not a gate of the palisade: it gets no stairs. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "gate_stairs_inner")
    public static void innerGateGetsNoStairs(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z <= GATE_Z; z++) {
                helper.setBlock(new BlockPos(x, 1, z), Blocks.STONE);
            }
        }
        List<BlockPos> gates = gates(helper, 2);

        int stairs = VillageExits.build(helper.getLevel(), helper.absolutePos(new BlockPos(7, 1, 0)), gates);

        helper.assertTrue(stairs == 0, "A gate near the heart must get no stairs: " + stairs);
        helper.succeed();
    }
}
