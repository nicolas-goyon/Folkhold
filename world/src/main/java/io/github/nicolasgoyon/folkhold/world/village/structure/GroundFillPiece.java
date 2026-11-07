package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntArrayTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A piece with no blocks of its own that fills the small gaps under the surface of the terrain near a tower or a gate of
 * a village. The terrain adaptation of the village ({@code beard_thin}) adds the boxes of the pieces together. Near a
 * tower or a gate, these boxes are at different levels, and the terrain can get a gap of air under its top block.
 *
 * <ul>
 *   <li>The box of the piece is the box of the tower or of the gate, with a band of {@link #MARGIN} blocks around it
 *       and {@link #DEPTH} blocks below it. The piece has no terrain adaptation: it does not change the shape of the
 *       terrain.</li>
 *   <li>In each column, the surface is the highest block of the terrain in the box (see
 *       {@link TerrainPiece#isTerrain}). Water above the surface stops the check of the column.</li>
 *   <li>Air under the surface is a gap when a block closes it within {@link #MAX_GAP} blocks: a solid block, or
 *       water or lava under the terrain. The piece fills the air of the gap with the ground block of the surface. Over
 *       water or lava, a ground block that falls (sand, gravel) changes to dirt. A deeper space is a cave: the piece
 *       does not change it.</li>
 *   <li>In the box of a terrain piece that clears its space (the flat terrain of a gate), the check of a column starts
 *       at the ground of this terrain piece: the cleared space above the ground stays open.</li>
 *   <li>The piece does not change the blocks of the other pieces: it changes only air.</li>
 * </ul>
 */
public final class GroundFillPiece extends StructurePiece implements PieceBeardifierModifier {
    /** The band around the box of the tower or of the gate, in blocks. */
    public static final int MARGIN = 4;
    /**
     * The box of the piece goes this number of blocks below the box of the tower or of the gate: the surface near a
     * tower on a slope can be lower than the tower.
     */
    public static final int DEPTH = 16;
    /** A gap has this number of air blocks at most. */
    public static final int MAX_GAP = 8;

    /** The boxes of the terrain pieces that clear their space, with the ground as their lowest Y. */
    private final List<BoundingBox> cleared;

    /**
     * Makes the piece of a tower or of a gate.
     *
     * @param box     the box of the template of the tower or of the gate
     * @param cleared the boxes of the terrain pieces that clear their space; their lowest Y is their ground
     */
    public GroundFillPiece(BoundingBox box, List<BoundingBox> cleared) {
        super(ModStructures.GROUND_FILL.get(), 0, new BoundingBox(box.minX() - MARGIN, box.minY() - DEPTH,
                box.minZ() - MARGIN, box.maxX() + MARGIN, box.maxY(), box.maxZ() + MARGIN));
        this.cleared = cleared.stream().filter(boundingBox::intersects).toList();
    }

    /** Loads a piece. */
    public GroundFillPiece(CompoundTag tag) {
        super(ModStructures.GROUND_FILL.get(), tag);
        List<BoundingBox> boxes = new ArrayList<>();
        for (Tag entry : tag.getList("Cleared", Tag.TAG_INT_ARRAY)) {
            int[] values = ((IntArrayTag) entry).getAsIntArray();
            if (values.length == 6) {
                boxes.add(new BoundingBox(values[0], values[1], values[2], values[3], values[4], values[5]));
            }
        }
        this.cleared = List.copyOf(boxes);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        ListTag list = new ListTag();
        for (BoundingBox box : cleared) {
            list.add(new IntArrayTag(new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(),
                box.maxZ()}));
        }
        tag.put("Cleared", list);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = Math.max(box.minX(), boundingBox.minX()); x <= Math.min(box.maxX(), boundingBox.maxX()); x++) {
            for (int z = Math.max(box.minZ(), boundingBox.minZ()); z <= Math.min(box.maxZ(), boundingBox.maxZ());
                    z++) {
                fillColumn(level, pos, x, z, top(x, z));
            }
        }
    }

    /** The highest Y of the check of a column: the top of the box, or the lowest ground of a cleared box. */
    private int top(int x, int z) {
        int top = boundingBox.maxY();
        for (BoundingBox box : cleared) {
            if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ()) {
                top = Math.min(top, box.minY());
            }
        }
        return top;
    }

    /** Fills the gap under the surface of a column, if the column has one. */
    private void fillColumn(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int z, int top) {
        for (int y = top; y > boundingBox.minY(); y--) {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (!state.getFluidState().isEmpty()) {
                return;
            }
            if (TerrainPiece.isTerrain(state)) {
                fillGap(level, pos, x, y, z, groundOf(state));
                return;
            }
        }
    }

    /** Fills the air under a surface block at (x, surface, z) when a block closes it within the limit. */
    private static void fillGap(WorldGenLevel level, BlockPos.MutableBlockPos pos, int x, int surface, int z,
            BlockState fill) {
        int gap = 0;
        while (gap <= MAX_GAP && level.getBlockState(pos.set(x, surface - 1 - gap, z)).isAir()) {
            gap++;
        }
        if (gap == 0 || gap > MAX_GAP) {
            return;
        }
        BlockState block = fill;
        if (!level.getFluidState(pos).isEmpty() && fill.getBlock() instanceof FallingBlock) {
            block = Blocks.DIRT.defaultBlockState();
        }
        for (int y = surface - gap; y < surface; y++) {
            level.setBlock(pos.set(x, y, z), block, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * The block under a surface block: dirt under a block of dirt or of snow (grass, podzol, a snow block), else the
     * surface block (sand, stone, terracotta).
     */
    static BlockState groundOf(BlockState surface) {
        if (surface.is(BlockTags.DIRT) || surface.is(BlockTags.SNOW)) {
            return Blocks.DIRT.defaultBlockState();
        }
        return surface.getBlock().defaultBlockState();
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
