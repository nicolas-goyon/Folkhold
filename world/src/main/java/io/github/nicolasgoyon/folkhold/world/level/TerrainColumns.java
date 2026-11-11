package io.github.nicolasgoyon.folkhold.world.level;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import java.util.function.Predicate;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;

/**
 * The terrain of a column of blocks from the terrain noise, before the generation of the chunks, with the rules of the
 * heightmaps of the world generation: the surface ({@code WORLD_SURFACE_WG}, water and lava included) and the floor
 * ({@code OCEAN_FLOOR_WG}, the top block that blocks the motion).
 */
public final class TerrainColumns {
    private TerrainColumns() {
    }

    /**
     * The surface, the floor and the top block of a column of blocks.
     *
     * @param blocks the column of blocks
     * @param minY   the lowest Y of the level
     * @param maxY   the Y above the highest block of the level
     * @return the column. With no block, the surface and the floor are at {@code minY}. With no floor block, the floor
     *         is at {@code minY}.
     */
    public static PlotTerrain.Column column(NoiseColumn blocks, int minY, int maxY) {
        Predicate<BlockState> surfaceBlock = Heightmap.Types.WORLD_SURFACE_WG.isOpaque();
        Predicate<BlockState> floorBlock = Heightmap.Types.OCEAN_FLOOR_WG.isOpaque();
        int surface = minY;
        boolean lava = false;
        boolean surfaceFound = false;
        for (int y = maxY - 1; y >= minY; y--) {
            BlockState state = blocks.getBlock(y);
            if (!surfaceFound && surfaceBlock.test(state)) {
                surface = y + 1;
                lava = state.getFluidState().getType().isSame(Fluids.LAVA);
                surfaceFound = true;
            }
            if (floorBlock.test(state)) {
                return new PlotTerrain.Column(surface, y + 1, lava);
            }
        }
        return new PlotTerrain.Column(surface, minY, lava);
    }
}
