package io.github.nicolasgoyon.folkhold.world.village.structure;

import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.neoforged.neoforge.common.world.PieceBeardifierModifier;

/**
 * A pier from a water gate of a fortified village: a straight deck of 3 blocks over the water, with rails on its two
 * sides and posts under it.
 *
 * <ul>
 *   <li>The deck is at the level of the gate. A block of the deck replaces the block of the level there: the water,
 *       the air, or the ground of a shore. The 2 blocks above the deck become free: on a shore, the pier goes through
 *       the ground. The pier does not replace a block with a block entity.</li>
 *   <li>A post goes under each side of the deck every {@link #POST_SPACING} rows and under the last row. It goes down
 *       to the floor of the water, {@link #MAX_DEPTH} blocks at most.</li>
 *   <li>The rails are on the two sides of the deck, except on the first row: the gate opens there.</li>
 *   <li>The pier does not change the terrain before the generation of the chunks.</li>
 * </ul>
 */
public final class PierPiece extends StructurePiece implements PieceBeardifierModifier {
    /** The shortest pier, in rows. */
    public static final int MIN_LENGTH = 4;
    /** The longest pier, in rows. */
    public static final int MAX_LENGTH = 12;
    /** A post goes under the deck every this number of rows. */
    public static final int POST_SPACING = 3;
    /** The longest post, in blocks. */
    public static final int MAX_DEPTH = 32;
    /** The number of blocks on each side of the middle of the deck. */
    private static final int HALF_WIDTH = 1;
    /** The free blocks above the deck. */
    private static final int HEADROOM = 2;

    private final int startX;
    private final int startZ;
    private final int stepX;
    private final int stepZ;
    private final int length;
    private final int deckY;
    private final PierBlocks blocks;

    /**
     * Makes a pier.
     *
     * @param startX the X of the middle of the first row
     * @param startZ the Z of the middle of the first row
     * @param stepX  the X step from a row to the next one: -1, 0 or 1
     * @param stepZ  the Z step from a row to the next one: -1, 0 or 1
     * @param length the number of rows, at least one
     * @param deckY  the Y of the deck
     * @param blocks the blocks of the pier
     */
    public PierPiece(int startX, int startZ, int stepX, int stepZ, int length, int deckY, PierBlocks blocks) {
        super(ModStructures.PIER.get(), 0, boundingBox(startX, startZ, stepX, stepZ, length, deckY));
        this.startX = startX;
        this.startZ = startZ;
        this.stepX = stepX;
        this.stepZ = stepZ;
        this.length = length;
        this.deckY = deckY;
        this.blocks = blocks;
    }

    /** Loads a pier. */
    public PierPiece(StructurePieceSerializationContext context, CompoundTag tag) {
        super(ModStructures.PIER.get(), tag);
        this.startX = tag.getInt("X");
        this.startZ = tag.getInt("Z");
        this.stepX = tag.getInt("StepX");
        this.stepZ = tag.getInt("StepZ");
        this.length = tag.getInt("Length");
        this.deckY = tag.getInt("DeckY");
        this.blocks = PierBlocks.load(context.registryAccess().lookupOrThrow(Registries.BLOCK),
                tag.getCompound("Blocks"));
    }

    private static BoundingBox boundingBox(int startX, int startZ, int stepX, int stepZ, int length, int deckY) {
        if (length < 1) {
            throw new IllegalArgumentException("A pier needs at least one row");
        }
        int endX = startX + stepX * (length - 1);
        int endZ = startZ + stepZ * (length - 1);
        // The width goes across the direction of the pier.
        int wideX = stepZ != 0 ? HALF_WIDTH : 0;
        int wideZ = stepX != 0 ? HALF_WIDTH : 0;
        return new BoundingBox(Math.min(startX, endX) - wideX, deckY - MAX_DEPTH, Math.min(startZ, endZ) - wideZ,
                Math.max(startX, endX) + wideX, deckY + HEADROOM, Math.max(startZ, endZ) + wideZ);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag) {
        tag.putInt("X", startX);
        tag.putInt("Z", startZ);
        tag.putInt("StepX", stepX);
        tag.putInt("StepZ", stepZ);
        tag.putInt("Length", length);
        tag.putInt("DeckY", deckY);
        tag.put("Blocks", blocks.save());
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structureManager, ChunkGenerator generator,
            RandomSource random, BoundingBox box, ChunkPos chunkPos, BlockPos pivot) {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int acrossX = stepZ != 0 ? 1 : 0;
        int acrossZ = stepX != 0 ? 1 : 0;
        for (int row = 0; row < length; row++) {
            boolean postRow = row % POST_SPACING == 0 || row == length - 1;
            for (int side = -HALF_WIDTH; side <= HALF_WIDTH; side++) {
                int x = startX + stepX * row + acrossX * side;
                int z = startZ + stepZ * row + acrossZ * side;
                if (x < box.minX() || x > box.maxX() || z < box.minZ() || z > box.maxZ()) {
                    continue;
                }
                place(level, pos.set(x, deckY, z), blocks.deck());
                clear(level, pos.set(x, deckY + HEADROOM, z));
                BlockPos above = new BlockPos(x, deckY + 1, z);
                if (side == 0 || row == 0) {
                    clear(level, above);
                } else if (canReplace(level.getBlockState(above))) {
                    PalisadePiece.placeShaped(level, above, blocks.rail());
                }
                if (side == 0) {
                    continue;
                }
                if (postRow) {
                    for (int depth = 1; depth <= MAX_DEPTH; depth++) {
                        pos.set(x, deckY - depth, z);
                        if (level.isOutsideBuildHeight(pos)
                                || PalisadePiece.isGround(level, pos, level.getBlockState(pos))) {
                            break;
                        }
                        level.setBlock(pos, blocks.post(), Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
    }

    /** Places a block of the pier, if the block of the level there has no block entity. */
    private static void place(WorldGenLevel level, BlockPos pos, BlockState state) {
        if (canReplace(level.getBlockState(pos))) {
            level.setBlock(pos, state, Block.UPDATE_CLIENTS);
        }
    }

    /** Removes the block of the level above the deck: the ground of a shore or a plant. Water and air stay. */
    private static void clear(WorldGenLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.isAir() && state.getFluidState().isEmpty() && canReplace(state)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    /** True if the pier can replace a block: a block with no block entity. */
    private static boolean canReplace(BlockState state) {
        return !state.hasBlockEntity();
    }

    /** The number of rows. */
    public int length() {
        return length;
    }

    /** The Y of the deck. */
    public int deckY() {
        return deckY;
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
