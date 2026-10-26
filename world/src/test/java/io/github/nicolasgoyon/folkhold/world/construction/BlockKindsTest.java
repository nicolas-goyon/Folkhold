package io.github.nicolasgoyon.folkhold.world.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.construction.Walk;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LiquidBlock;
import org.junit.jupiter.api.Test;

/** How a builder walks through the blocks or on them, on its way to a position to stand. */
class BlockKindsTest {
    /**
     * A builder walks through a block with no collision, a carpet and water, as the path finder of a villager. It
     * walks through a wooden door: a villager opens it.
     */
    @Test
    void builderWalksThroughOpenBlocksWaterAndWoodenDoors() {
        assertEquals(Walk.THROUGH, BlockKinds.walk(Blocks.AIR.defaultBlockState()));
        assertEquals(Walk.THROUGH, BlockKinds.walk(Blocks.TORCH.defaultBlockState()));
        assertEquals(Walk.THROUGH, BlockKinds.walk(Blocks.WHITE_CARPET.defaultBlockState()));
        assertEquals(Walk.THROUGH, BlockKinds.walk(Blocks.WATER.defaultBlockState()));
        assertEquals(Walk.THROUGH, BlockKinds.walk(Blocks.OAK_DOOR.defaultBlockState()));
        assertEquals(Walk.THROUGH,
                BlockKinds.walk(Blocks.OAK_FENCE_GATE.defaultBlockState().setValue(FenceGateBlock.OPEN, true)));
    }

    /** A builder walks on a block with its top at 1 block or lower. A villager does not open an iron door. */
    @Test
    void builderWalksOnBlocksOfOneBlockOrLower() {
        assertEquals(Walk.ON, BlockKinds.walk(Blocks.STONE.defaultBlockState()));
        assertEquals(Walk.ON, BlockKinds.walk(Blocks.OAK_SLAB.defaultBlockState()));
        assertEquals(Walk.ON, BlockKinds.walk(Blocks.OAK_STAIRS.defaultBlockState()));
        assertEquals(Walk.ON, BlockKinds.walk(Blocks.CHEST.defaultBlockState()));
        assertEquals(Walk.ON, BlockKinds.walk(Blocks.IRON_DOOR.defaultBlockState()));
    }

    /** A fence, a wall, a closed fence gate and lava stop a builder. */
    @Test
    void fencesWallsAndLavaStopTheBuilder() {
        assertEquals(Walk.BLOCKED, BlockKinds.walk(Blocks.OAK_FENCE.defaultBlockState()));
        assertEquals(Walk.BLOCKED, BlockKinds.walk(Blocks.COBBLESTONE_WALL.defaultBlockState()));
        assertEquals(Walk.BLOCKED, BlockKinds.walk(Blocks.OAK_FENCE_GATE.defaultBlockState()));
        assertEquals(Walk.BLOCKED, BlockKinds.walk(Blocks.LAVA.defaultBlockState()));
    }

    /** The description of a block for a plan has its walk, also for a flowing fluid. */
    @Test
    void descriptionHasTheWalk() {
        BlockPos pos = BlockPos.ZERO;
        assertEquals(Walk.BLOCKED, BlockKinds.describe(pos, Blocks.OAK_FENCE.defaultBlockState(), Optional.empty())
                .walk());
        assertEquals(Walk.THROUGH, BlockKinds.describe(pos,
                Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 2), Optional.empty()).walk());
        assertEquals(Walk.BLOCKED, BlockKinds.describe(pos,
                Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 2), Optional.empty()).walk());
    }
}
