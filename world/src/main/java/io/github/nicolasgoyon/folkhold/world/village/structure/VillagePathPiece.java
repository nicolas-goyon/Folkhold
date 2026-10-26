package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.PathRamp;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.Arrays;
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
 * A straight path of 3 blocks, from a gate of a village to its streets. Each row of the path has a level: the Y of its
 * top block (see {@link PathRamp}). Thus the path goes up or down by 1 block at most from a row to the next one, and a
 * villager walks on it.
 *
 * <p>On each block of a row, the blocks of the terrain and the plants in the {@link TerrainPiece#CLEARANCE} blocks
 * above the level go away. A hole under the level gets dirt, and the top block becomes a dirt path. The path does not
 * change water, and it does not change the blocks of the other pieces.
 */
public final class VillagePathPiece extends StructurePiece implements PieceBeardifierModifier {
    /** The search of the ground goes this number of blocks below the level of a row. */
    public static final int SEARCH = 16;
    /** The number of blocks on each side of the middle of the path. */
    private static final int HALF_WIDTH = 1;

    private final int startX;
    private final int startZ;
    private final int stepX;
    private final int stepZ;
    private final int length;
    /** The level of each row: the Y of its top block. */
    private final int[] levels;

    /**
     * Makes a path.
     *
     * @param startX the X of the middle of the first row
     * @param startZ the Z of the middle of the first row
     * @param stepX  the X step from a row to the next one: -1, 0 or 1
     * @param stepZ  the Z step from a row to the next one: -1, 0 or 1
     * @param levels the level of each row: the Y of its top block, at least one row
     */
    public VillagePathPiece(int startX, int startZ, int stepX, int stepZ, int[] levels) {
        super(ModStructures.VILLAGE_PATH.get(), 0, boundingBox(startX, startZ, stepX, stepZ, levels));
        this.startX = startX;
        this.startZ = startZ;
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.length = levels.length;
        this.levels = levels.clone();
    }

    /** Loads a path. */
    public VillagePathPiece(CompoundTag tag) {
        super(ModStructures.VILLAGE_PATH.get(), tag);
        this.startX = tag.getInt("X");
        this.startZ = tag.getInt("Z");
        this.stepX = tag.getInt("StepX");
        this.stepZ = tag.getInt("StepZ");
        this.length = tag.getInt("Length");
        this.levels = tag.getIntArray("Levels");
    }

    /** The level of each row: the Y of its top block. */
    public int[] levels() {
        return levels.clone();
    }

    private static BoundingBox boundingBox(int startX, int startZ, int stepX, int stepZ, int[] levels) {
        int length = levels.length;
        if (length < 1) {
            throw new IllegalArgumentException("A path needs at least one row");
        }
        int minY = Arrays.stream(levels).min().orElseThrow() - SEARCH;
        int maxY = Arrays.stream(levels).max().orElseThrow() + TerrainPiece.CLEARANCE;
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
        tag.putIntArray("Levels", levels);
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
                    if (levels.length == length) {
                        placeRow(level, pos, x, z, levels[row]);
                    } else {
                        placePath(level, pos, x, z);
                    }
                }
            }
        }
    }

    /**
     * Places a block of a row at its level: the space above it is clear, a hole under it gets dirt, and its top block
     * becomes a dirt path. Water does not change.
     */
    private static void placeRow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top) {
        if (!level.getFluidState(pos.set(x, top, z)).isEmpty()
                || !level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) {
            return;
        }
        for (int y = top + 1; y <= top + TerrainPiece.CLEARANCE; y++) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (TerrainPiece.isTerrain(state) || TerrainPiece.isPlant(state)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }
        for (int y = top - 1; y > top - SEARCH; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!(state.isAir() || TerrainPiece.isPlant(state))) {
                break;
            }
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState state = level.getBlockState(pos.set(x, top, z));
        if (state.isAir() || TerrainPiece.isPlant(state) || TerrainPiece.isTerrain(state)) {
            level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /**
     * Changes the top block of the ground into a dirt path, and removes the plants above it: a block of a path with no
     * levels in its data.
     */
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
        for (int y = ground + 1; y <= ground + TerrainPiece.CLEARANCE; y++) {
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
