package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.core.sim.village.PalisadeHeights;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.Arrays;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ProtoChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A segment of the palisade of a village: a straight line of columns, each one with its base.
 *
 * <ul>
 *   <li>A column is the base block at its base, and {@link PalisadeHeights#LOGS} logs above it. Every second column
 *       has a point on top (the sum of X and Z is even).</li>
 *   <li>A foundation fills the space under the base, down to the ground: the column never floats. The foundation goes
 *       down {@link #MAX_FOUNDATION} blocks at most.</li>
 *   <li>The terrain adaptation of a near piece can raise the ground above the base. Then more logs go up: the top
 *       of the column stays {@link PalisadeHeights#LOGS} blocks above the ground.</li>
 * </ul>
 *
 * <p>The piece does not change the terrain before the generation of the chunks: the bases follow the terrain (see
 * {@link VillageWall}).
 */
public final class PalisadePiece extends StructurePiece implements PieceBeardifierModifier {
    /** The foundation goes down this number of blocks at most, under the base. */
    public static final int MAX_FOUNDATION = 48;
    /** The search of the ground starts this number of blocks above the base. */
    public static final int MAX_RAISE = 16;

    private final int startX;
    private final int startZ;
    private final int stepX;
    private final int stepZ;
    private final int[] bases;
    private final PalisadeBlocks blocks;

    /**
     * Makes a segment of palisade.
     *
     * @param startX the X of the first column
     * @param startZ the Z of the first column
     * @param stepX  the X step from a column to the next one: -1, 0 or 1
     * @param stepZ  the Z step from a column to the next one: -1, 0 or 1
     * @param bases  the base of each column, at least one
     * @param blocks the blocks of the palisade
     */
    public PalisadePiece(int startX, int startZ, int stepX, int stepZ, int[] bases, PalisadeBlocks blocks) {
        super(ModStructures.PALISADE.get(), 0, boundingBox(startX, startZ, stepX, stepZ, bases));
        this.startX = startX;
        this.startZ = startZ;
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.bases = bases.clone();
        this.blocks = blocks;
    }

    /** Loads a segment of palisade. */
    public PalisadePiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.PALISADE.get(), tag);
        this.startX = tag.getInt("X");
        this.startZ = tag.getInt("Z");
        this.stepX = tag.getInt("StepX");
        this.stepZ = tag.getInt("StepZ");
        this.bases = tag.getIntArray("Bases");
        this.blocks = PalisadeBlocks.load(context.registryAccess().lookupOrThrow(Registries.BLOCK),
                tag.getCompound("Blocks"));
    }

    private static BoundingBox boundingBox(int startX, int startZ, int stepX, int stepZ, int[] bases) {
        if (bases.length == 0) {
            throw new IllegalArgumentException("A segment of palisade needs at least one column");
        }
        int endX = startX + stepX * (bases.length - 1);
        int endZ = startZ + stepZ * (bases.length - 1);
        int low = Arrays.stream(bases).min().orElseThrow();
        int high = Arrays.stream(bases).max().orElseThrow();
        return new BoundingBox(Math.min(startX, endX), low - MAX_FOUNDATION, Math.min(startZ, endZ),
                Math.max(startX, endX), high + MAX_RAISE + PalisadeHeights.LOGS + 1, Math.max(startZ, endZ));
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X", startX);
        tag.putInt("Z", startZ);
        tag.putInt("StepX", stepX);
        tag.putInt("StepZ", stepZ);
        tag.putIntArray("Bases", bases);
        tag.put("Blocks", blocks.save());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 0; i < bases.length; i++) {
            int x = startX + stepX * i;
            int z = startZ + stepZ * i;
            if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
                placeColumn(level, pos, x, z, bases[i]);
            }
        }
    }

    /** Places one column, with its foundation. */
    private void placeColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int base) {
        int ground = groundTop(level, pos, x, z, base + MAX_RAISE, base - MAX_FOUNDATION);
        for (int y = base - 1; y > ground && y >= base - MAX_FOUNDATION; y--) {
            level.setBlock(pos.set(x, y, z), blocks.foundation(), Block.UPDATE_CLIENTS);
        }
        level.setBlock(pos.set(x, base, z), blocks.base(), Block.UPDATE_CLIENTS);
        int top = Math.max(base, ground) + PalisadeHeights.LOGS;
        for (int y = base + 1; y <= top; y++) {
            level.setBlock(pos.set(x, y, z), blocks.log(), Block.UPDATE_CLIENTS);
        }
        if (isPointed(x, z)) {
            placeShaped(level, pos.set(x, top + 1, z), blocks.point());
        }
    }

    /**
     * Places a block that connects to its neighbors, as a fence. During the generation of a chunk, the game updates
     * its shape when the chunk is complete. In a complete chunk, the shape comes from the neighbors now.
     */
    private static void placeShaped(WorldGenLevel level, BlockPos pos, BlockState state) {
        ChunkAccess chunk = level.getChunk(pos);
        if (chunk instanceof ProtoChunk) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
            chunk.markPosForPostprocessing(pos);
        } else {
            level.setBlock(pos, Block.updateFromNeighbourShapes(state, level, pos), Block.UPDATE_CLIENTS);
        }
    }

    /** True if a column has a point on top: every second column. */
    public static boolean isPointed(int x, int z) {
        return Math.floorMod(x + z, 2) == 0;
    }

    /**
     * The Y of the highest ground block of a column, from a top to a bottom (see {@link #isGround}). If no ground block
     * is there, bottom - 1.
     */
    static int groundTop(BlockGetter level, BlockPos.MutableBlockPos pos, int x, int z, int top, int bottom) {
        for (int y = top; y >= bottom; y--) {
            pos.set(x, y, z);
            if (isGround(level, pos, level.getBlockState(pos))) {
                return y;
            }
        }
        return bottom - 1;
    }

    /**
     * True if a block is a part of the ground: a block with a collision shape and no fluid. The logs and the leaves of
     * a tree are not a part of the ground.
     */
    static boolean isGround(BlockGetter level, BlockPos pos, BlockState state) {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LEAVES)
                && !state.is(BlockTags.LOGS) && !state.getCollisionShape(level, pos).isEmpty();
    }

    /** The X of the first column. */
    public int startX() {
        return startX;
    }

    /** The Z of the first column. */
    public int startZ() {
        return startZ;
    }

    /** The X step from a column to the next one. */
    public int stepX() {
        return stepX;
    }

    /** The Z step from a column to the next one. */
    public int stepZ() {
        return stepZ;
    }

    /** The base of each column. */
    public int[] bases() {
        return bases.clone();
    }

    /** The blocks of the palisade. */
    public PalisadeBlocks blocks() {
        return blocks;
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
