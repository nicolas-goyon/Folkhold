package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A piece of a village with no blocks: it changes the shape of the terrain before the generation of the blocks. The
 * terrain near its box goes to the level of its ground. The game adds terrain under this level and removes the terrain
 * above it. The change is smaller far from the box (terrain adaptation {@code beard_thin}).
 *
 * <p>The ground of the piece is the lowest layer of its box: the top block of the terrain is at this level.
 */
public final class TerrainPiece extends StructurePiece implements PieceBeardifierModifier {
    /** The first layer above the ground is 1 block above the lowest layer of the box. */
    private static final int GROUND_LEVEL_DELTA = 1;
    /** The height of the box above the ground. It has no effect on the terrain adaptation. */
    private static final int HEIGHT = 4;

    /**
     * Makes a piece.
     *
     * @param minX   the lowest X of the area
     * @param minZ   the lowest Z of the area
     * @param maxX   the highest X of the area
     * @param maxZ   the highest Z of the area
     * @param ground the Y of the top block of the terrain in the area
     */
    public TerrainPiece(int minX, int minZ, int maxX, int maxZ, int ground) {
        super(ModStructures.TERRAIN.get(), 0, new BoundingBox(minX, ground, minZ, maxX, ground + HEIGHT, maxZ));
    }

    /** Loads a piece. */
    public TerrainPiece(CompoundTag tag) {
        super(ModStructures.TERRAIN.get(), tag);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        // The box is the only data of the piece.
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        // The piece places no blocks.
    }

    /** The Y of the top block of the terrain in the area. */
    public int ground() {
        return boundingBox.minY();
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.BEARD_THIN;
    }

    @Override
    public int getGroundLevelDelta() {
        return GROUND_LEVEL_DELTA;
    }
}
