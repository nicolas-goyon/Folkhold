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
 * A straight path of a village: an arm from a gate to the plaza, through the streets on its axis. Each row of the path
 * has a level: the Y of its top block (see {@link PathRamp}). Thus the path goes up or down by 1 block at most from a
 * row to the next one, and a villager walks on it.
 *
 * <p>A row has 3 blocks of path in the middle. A path with edges also has 1 block of terrain on each side: the width of
 * a street. On each block of a row:
 *
 * <ul>
 *   <li>The blocks of the terrain, the blocks of the streets and the plants above the level go away: all of them in
 *       the {@link TerrainPiece#CLEARANCE} blocks above the level, and the blocks of the terrain and of the streets up
 *       to the surface of the terrain.</li>
 *   <li>A hole under the level gets dirt.</li>
 *   <li>The top block of the middle becomes a dirt path, and a block of a street there stays. The top block of an edge
 *       becomes the surface block of the terrain of its column (grass, sand).</li>
 * </ul>
 *
 * <p>The path does not change deep water, and it does not change the blocks of the other pieces.
 */
public final class VillagePathPiece extends StructurePiece implements PieceBeardifierModifier {
    /** The search of the ground goes this number of blocks below the level of a row. */
    public static final int SEARCH = 16;
    /** A row fills water with dirt when the floor of the water is less than this number of blocks under the row. */
    public static final int FILL_DEPTH = 3;
    /** The number of blocks of path on each side of the middle. */
    private static final int HALF_WIDTH = 1;
    /** The number of blocks on each side of the middle of a path with edges: the half width of a street. */
    private static final int EDGE_HALF_WIDTH = 2;

    private final int startX;
    private final int startZ;
    private final int stepX;
    private final int stepZ;
    private final int length;
    /** The level of each row: the Y of its top block. */
    private final int[] levels;
    private final boolean edges;

    /**
     * Makes a path with no edges.
     *
     * @param startX the X of the middle of the first row
     * @param startZ the Z of the middle of the first row
     * @param stepX  the X step from a row to the next one: -1, 0 or 1
     * @param stepZ  the Z step from a row to the next one: -1, 0 or 1
     * @param levels the level of each row: the Y of its top block, at least one row
     */
    public VillagePathPiece(int startX, int startZ, int stepX, int stepZ, int[] levels) {
        this(startX, startZ, stepX, stepZ, levels, false);
    }

    /**
     * Makes a path.
     *
     * @param startX the X of the middle of the first row
     * @param startZ the Z of the middle of the first row
     * @param stepX  the X step from a row to the next one: -1, 0 or 1
     * @param stepZ  the Z step from a row to the next one: -1, 0 or 1
     * @param levels the level of each row: the Y of its top block, at least one row
     * @param edges  true for a path with an edge of terrain on each side: the width of a street
     */
    public VillagePathPiece(int startX, int startZ, int stepX, int stepZ, int[] levels, boolean edges) {
        super(ModStructures.VILLAGE_PATH.get(), 0, boundingBox(startX, startZ, stepX, stepZ, levels, edges));
        this.startX = startX;
        this.startZ = startZ;
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.length = levels.length;
        this.levels = levels.clone();
        this.edges = edges;
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
        this.edges = tag.getBoolean("Edges");
    }

    /** The level of each row: the Y of its top block. */
    public int[] levels() {
        return levels.clone();
    }

    /** True for a path with an edge of terrain on each side. */
    public boolean edges() {
        return edges;
    }

    private static BoundingBox boundingBox(int startX, int startZ, int stepX, int stepZ, int[] levels,
            boolean edges) {
        int length = levels.length;
        if (length < 1) {
            throw new IllegalArgumentException("A path needs at least one row");
        }
        int minY = Arrays.stream(levels).min().orElseThrow() - SEARCH;
        int maxY = Arrays.stream(levels).max().orElseThrow() + SEARCH;
        int endX = startX + stepX * (length - 1);
        int endZ = startZ + stepZ * (length - 1);
        // The width goes across the direction of the path.
        int half = edges ? EDGE_HALF_WIDTH : HALF_WIDTH;
        int wideX = stepZ != 0 ? half : 0;
        int wideZ = stepX != 0 ? half : 0;
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
        tag.putBoolean("Edges", edges);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int acrossX = stepZ != 0 ? 1 : 0;
        int acrossZ = stepX != 0 ? 1 : 0;
        int half = edges ? EDGE_HALF_WIDTH : HALF_WIDTH;
        for (int row = 0; row < length; row++) {
            for (int side = -half; side <= half; side++) {
                int x = startX + stepX * row + acrossX * side;
                int z = startZ + stepZ * row + acrossZ * side;
                if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
                    if (levels.length == length) {
                        placeRow(level, pos, x, z, levels[row], Math.abs(side) > HALF_WIDTH);
                    } else {
                        placePath(level, pos, x, z);
                    }
                }
            }
        }
    }

    /**
     * Places a block of a row at its level: the space above it is clear up to the surface of the terrain, a hole under
     * it gets dirt, and its top block becomes a block of path or the surface block of the terrain. Shallow water at the
     * level of the row gets dirt down to its floor. Deep water and water above the level of the row do not change.
     *
     * @param edge true for a block of an edge: its top block is the surface block of the terrain
     */
    private static void placeRow(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top,
            boolean edge) {
        if (!level.getFluidState(pos.set(x, top + 1, z)).isEmpty()) {
            return;
        }
        if (!level.getFluidState(pos.set(x, top, z)).isEmpty() && !fillWater(level, pos, x, z, top)) {
            return;
        }
        BlockState surface = null;
        for (int y = top + 1; y < top + SEARCH; y++) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            boolean ground = TerrainPiece.isTerrain(state) || isStreet(state);
            if (TerrainPiece.isTerrain(state)) {
                surface = state;
            }
            if (ground || (y <= top + TerrainPiece.CLEARANCE && TerrainPiece.isPlant(state))) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            } else if (y > top + TerrainPiece.CLEARANCE) {
                break;
            }
        }
        for (int y = top - 1; y > top - SEARCH; y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!(state.isAir() || TerrainPiece.isPlant(state))) {
                if (surface == null && TerrainPiece.isTerrain(state)) {
                    surface = state;
                }
                break;
            }
            level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        BlockState state = level.getBlockState(pos.set(x, top, z));
        if (!(state.isAir() || TerrainPiece.isPlant(state) || TerrainPiece.isTerrain(state) || isStreet(state))) {
            return;
        }
        if (edge) {
            if (surface != null && !surface.is(state.getBlock())) {
                level.setBlock(pos, surface, Block.UPDATE_CLIENTS);
            } else if (!TerrainPiece.isTerrain(state)) {
                level.setBlock(pos, Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        } else if (!isStreet(state)) {
            level.setBlock(pos, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** True for a block of the path of a street on land: a dirt path, gravel or coarse dirt. */
    static boolean isStreet(BlockState state) {
        return state.is(Blocks.DIRT_PATH) || state.is(Blocks.GRAVEL) || state.is(Blocks.COARSE_DIRT);
    }

    /**
     * Fills shallow water under the top block of a row with dirt, down to the floor of the water. Water is shallow when
     * its floor is less than {@link #FILL_DEPTH} blocks under the row.
     *
     * @return true if the water is shallow: the top block of the row is dirt
     */
    private static boolean fillWater(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top) {
        int floor = top - 1;
        while (floor > top - FILL_DEPTH && !level.getFluidState(pos.set(x, floor, z)).isEmpty()) {
            floor--;
        }
        if (!level.getFluidState(pos.set(x, floor, z)).isEmpty()) {
            return false;
        }
        for (int y = floor + 1; y <= top; y++) {
            level.setBlock(pos.set(x, y, z), Blocks.DIRT.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
        return true;
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
