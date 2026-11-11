package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillagePlan;
import io.github.nicolasgoyon.folkhold.world.level.TerrainColumns;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The terrain of the plots of a village before the generation of the chunks (see {@link PlotTerrain}). The chunk
 * generator calculates each column from the noise of the terrain: one calculation for each point of the grid, kept
 * for the other plots.
 */
public final class PlotTerrains {
    private final Structure.GenerationContext context;
    private final Map<Long, PlotTerrain.Column> columns = new HashMap<>();

    /**
     * Makes the terrain of a context.
     *
     * @param context the context of the generation
     */
    public PlotTerrains(Structure.GenerationContext context) {
        this.context = context;
    }

    /**
     * Checks the terrain of a box in the world, on the horizontal plane.
     *
     * @param box the box
     * @return the result
     */
    public PlotTerrain.Result check(BoundingBox box) {
        return PlotTerrain.check(box.minX(), box.minZ(), box.maxX(), box.maxZ(), this::column);
    }

    /**
     * The terrain test of the plots of a plan: true for a box of the plan with good terrain.
     *
     * @param center   the middle of the plaza: (0, 0) of the plan
     * @param rotation the rotation of the plaza: the plan turns with it
     * @return the test
     */
    Predicate<VillagePlan.Box> plan(BlockPos center, Rotation rotation) {
        return box -> {
            BlockPos first = center.atY(0).offset(new BlockPos(box.minX(), 0, box.minZ()).rotate(rotation));
            BlockPos second = center.atY(0).offset(new BlockPos(box.maxX(), 0, box.maxZ()).rotate(rotation));
            return check(BoundingBox.fromCorners(first, second)) == PlotTerrain.Result.GOOD;
        };
    }

    private PlotTerrain.Column column(int x, int z) {
        return columns.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> {
            LevelHeightAccessor height = context.heightAccessor();
            NoiseColumn column = context.chunkGenerator().getBaseColumn(x, z, height, context.randomState());
            return TerrainColumns.column(column, height.getMinBuildHeight(), height.getMaxBuildHeight());
        });
    }
}
