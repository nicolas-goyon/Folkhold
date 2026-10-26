package io.github.nicolasgoyon.folkhold.blocks.block;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.junit.jupiter.api.Test;

class BrazierBlockTest {
    /** The collision box is 1.5 blocks high, as a fence, and the path finders do not go through the brazier. */
    @Test
    void brazierIsAFenceForTheMobs() {
        BlockState state = ModBlocks.BRAZIER.get().defaultBlockState();

        double top = state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, CollisionContext.empty())
                .max(Direction.Axis.Y);

        assertEquals(1.5, top, 1.0E-6);
        assertFalse(state.isPathfindable(PathComputationType.LAND));
    }

    /** A lit brazier gives a light of 15. A brazier that does not burn gives no light. */
    @Test
    void litBrazierGivesLight() {
        BlockState state = ModBlocks.BRAZIER.get().defaultBlockState();

        assertEquals(0, state.getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
        assertEquals(BrazierBlock.LIGHT,
                state.setValue(BrazierBlock.LIT, true).getLightEmission(EmptyBlockGetter.INSTANCE, BlockPos.ZERO));
    }

    /** A brazier can start to burn only if it does not burn and it is not in water. */
    @Test
    void brazierInWaterCannotBurn() {
        BlockState state = ModBlocks.BRAZIER.get().defaultBlockState();
        BlockState inWater = state.setValue(BrazierBlock.WATERLOGGED, true);

        assertTrue(BrazierBlock.canLight(state));
        assertFalse(BrazierBlock.canLight(state.setValue(BrazierBlock.LIT, true)));
        assertFalse(BrazierBlock.canLight(inWater));
        assertTrue(state.getFluidState().isEmpty());
        assertTrue(inWater.getFluidState().is(Fluids.WATER));
    }
}
