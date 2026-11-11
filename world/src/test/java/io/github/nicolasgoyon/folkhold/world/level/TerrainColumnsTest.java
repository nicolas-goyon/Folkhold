package io.github.nicolasgoyon.folkhold.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class TerrainColumnsTest {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState WATER = Blocks.WATER.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    /** The lowest Y of the test columns. */
    private static final int MIN_Y = -4;

    /** A column of blocks from {@link #MIN_Y}, the lowest block first. */
    private static PlotTerrain.Column column(BlockState... blocks) {
        return TerrainColumns.column(new NoiseColumn(MIN_Y, blocks), MIN_Y, MIN_Y + blocks.length);
    }

    /** On land, the surface and the floor are above the highest stone. */
    @Test
    void landHasNoDepth() {
        assertEquals(new PlotTerrain.Column(-2, -2, false), column(STONE, STONE, AIR, AIR));
    }

    /** Over water, the surface is above the water and the floor is above the stone: the depth is the water. */
    @Test
    void waterOverStoneHasDepth() {
        PlotTerrain.Column column = column(STONE, WATER, WATER, WATER, AIR);
        assertEquals(new PlotTerrain.Column(0, -3, false), column);
        assertEquals(3, column.depth());
    }

    /** A column with lava as its top block has lava at the surface. */
    @Test
    void lavaAtTheTopIsLava() {
        assertEquals(new PlotTerrain.Column(-1, -3, true), column(STONE, LAVA, LAVA, AIR));
    }

    /** A column of water with no floor block has its floor at the bottom of the level. */
    @Test
    void waterWithNoFloorGoesToTheBottom() {
        PlotTerrain.Column column = column(WATER, WATER, AIR);
        assertEquals(new PlotTerrain.Column(-2, MIN_Y, false), column);
        assertEquals(2, column.depth());
    }

    /** A column with no block has its surface and its floor at the bottom of the level. */
    @Test
    void emptyColumnIsAtTheBottom() {
        assertEquals(new PlotTerrain.Column(MIN_Y, MIN_Y, false), column(AIR, AIR, AIR));
    }
}
