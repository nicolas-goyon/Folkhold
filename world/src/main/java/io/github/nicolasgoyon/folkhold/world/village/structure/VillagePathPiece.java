package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A straight path of 3 blocks on the terrain, from a gate of a village to its streets. On each block, the top block of
 * the ground becomes a dirt path if it is a block of dirt. The plants above it go away. The path follows the
 * terrain: it does not change the terrain before the generation of the chunks.
 */
public final class VillagePathPiece extends StructurePiece implements PieceBeardifierModifier {
    /** The search of the ground goes this number of blocks above and below the terrain of the ends. */
    public static final int SEARCH = 16;
    /** The number of blocks on each side of the middle of the path. */
    private static final int HALF_WIDTH = 1;
    /** The free blocks above the path. */
    private static final int HEADROOM = 2;

    private final int startX;
    private final int startZ;
    private final int stepX;
    private final int stepZ;
    private final int length;

    /**
     * Makes a path.
     *
     * @param startX the X of the middle of the first row
     * @param startZ the Z of the middle of the first row
     * @param stepX  the X step from a row to the next one: -1, 0 or 1
     * @param stepZ  the Z step from a row to the next one: -1, 0 or 1
     * @param length the number of rows, at least one
     * @param minY   the lowest Y of the box of the piece
     * @param maxY   the highest Y of the box of the piece
     */
    public VillagePathPiece(int startX, int startZ, int stepX, int stepZ, int length, int minY, int maxY) {
        super(ModStructures.VILLAGE_PATH.get(), 0, boundingBox(startX, startZ, stepX, stepZ, length, minY, maxY));
        this.startX = startX;
        this.startZ = startZ;
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.length = length;
    }

    /** Loads a path. */
    public VillagePathPiece(CompoundTag tag) {
        super(ModStructures.VILLAGE_PATH.get(), tag);
        this.startX = tag.getInt("X");
        this.startZ = tag.getInt("Z");
        this.stepX = tag.getInt("StepX");
        this.stepZ = tag.getInt("StepZ");
        this.length = tag.getInt("Length");
    }

    private static BoundingBox boundingBox(int startX, int startZ, int stepX, int stepZ, int length, int minY,
            int maxY) {
        if (length < 1) {
            throw new IllegalArgumentException("A path needs at least one row");
        }
        int endX = startX + stepX * (length - 1);
        int endZ = startZ + stepZ * (length - 1);
        // The width goes across the direction of the path.
        int wideX = stepZ != 0 ? HALF_WIDTH : 0;
        int wideZ = stepX != 0 ? HALF_WIDTH : 0;
        return new BoundingBox(Math.min(startX, endX) - wideX, minY, Math.min(startZ, endZ) - wideZ,
                Math.max(startX, endX) + wideX, maxY, Math.max(startZ, endZ) + wideZ);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X", startX);
        tag.putInt("Z", startZ);
        tag.putInt("StepX", stepX);
        tag.putInt("StepZ", stepZ);
        tag.putInt("Length", length);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int acrossX = stepZ != 0 ? 1 : 0;
        int acrossZ = stepX != 0 ? 1 : 0;
        for (int row = 0; row < length; row++) {
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                int x = startX + stepX * row + acrossX * side;
                int z = startZ + stepZ * row + acrossZ * side;
                if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
                    placePath(level, pos, x, z);
                }
            }
        }
    }

    /** Changes the top block of the ground into a dirt path, and removes the plants above it. */
    private static void placePath(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z) {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        int ground = PalisadePiece.groundTop(level, pos, x, z, top, top - SEARCH);
        if (ground < top - SEARCH) {
            return;
        }
        BlockState state = level.getBlockState(pos.set(x, ground, z));
        if (!state.is(BlockTags.DIRT)) {
            return;
        }
        level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);
        for (int y = ground + 1; y <= ground + HEADROOM; y++) {
            BlockState above = level.getBlockState(pos.set(x, y, z));
            if (!above.isAir() && above.canBeReplaced() && above.getFluidState().isEmpty()) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
    }

    @Override
    public BoundingBox getBeardifierBox() {
        return boundingBox;
    }

    @Override
    public TerrainAdjustment getTerrainAdjustment() {
        return TerrainAdjustment.NONE;
    }

    @Override
    public int getGroundLevelDelta() {
        return 0;
    }
}
