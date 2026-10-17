package io.github.nicolasgoyon.folkhold.world.group;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class CaravanRoutesTest {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();

    /** A column of blocks from Y = 0, the lowest block first. */
    private static RouteFinder.Column column(BlockState... blocks) {
        return CaravanRoutes.column(new NoiseColumn(0, blocks), 0, blocks.length);
    }

    /** On land, the floor is the highest solid block, and there is no water. */
    @Test
    void landHasNoWater() {
        assertEquals(new RouteFinder.Column(1, 0), column(STONE, STONE, AIR, AIR));
    }

    /** Over water, the water depth is the number of water blocks above the floor. */
    @Test
    void waterDepthIsAboveTheFloor() {
        assertEquals(new RouteFinder.Column(0, 2), column(STONE, WATER, WATER, AIR));
    }

    /** The highest solid block is the floor, also above air: a cave does not change the floor. */
    @Test
    void floorIsTheHighestSolidBlock() {
        assertEquals(new RouteFinder.Column(2, 0), column(STONE, AIR, STONE, AIR));
    }

    /** With no block, the floor and the surface are at the bottom of the level, as for the heightmaps. */
    @Test
    void emptyColumnIsAtTheBottom() {
        assertEquals(new RouteFinder.Column(-1, 0), column(AIR, AIR, AIR));
    }
}
