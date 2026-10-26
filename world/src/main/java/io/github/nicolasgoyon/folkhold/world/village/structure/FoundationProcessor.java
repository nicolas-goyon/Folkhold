package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureProcessorType;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate.StructureBlockInfo;

/**
 * A foundation under a structure template. Under each block of the lowest layer of the template, the foundation block
 * fills the space down to the ground. A building on a hole, on water or on a slope does not float.
 *
 * <ul>
 *   <li>A block of the lowest layer gets a foundation when it has a collision shape: a floor, a path, a wall.</li>
 *   <li>The foundation fills air, fluids and plants (see {@link PalisadePiece#isGround}), down to
 *       {@code max_depth} blocks at most.</li>
 *   <li>With a stilt block, a block of the lowest layer with water under it gets a stilt, not a full column: only at
 *       the corners of the lowest layer and every {@link #STILT_SPACING} blocks along its edges. The building stands
 *       on stilts over the water.</li>
 *   <li>The foundation is not a part of the template: the repairs of the village do not change it.</li>
 * </ul>
 *
 * <p>Type {@code folkhold_world:foundation}. Fields: {@code block} (the foundation block), {@code max_depth} and
 * {@code stilt} (optional: the block of the stilts over water).
 */
public final class FoundationProcessor extends StructureProcessor {
    /** The largest depth of a foundation. */
    public static final int MAX_DEPTH = 64;
    /** Over water, a stilt goes every this number of blocks along the edges of the lowest layer. */
    public static final int STILT_SPACING = 3;

    public static final MapCodec<FoundationProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockState.CODEC.fieldOf("block").forGetter(FoundationProcessor::block),
            Codec.intRange(1, MAX_DEPTH).fieldOf("max_depth").forGetter(FoundationProcessor::maxDepth),
            BlockState.CODEC.optionalFieldOf("stilt").forGetter(FoundationProcessor::stilt))
            .apply(instance, FoundationProcessor::new));

    private final BlockState block;
    private final int maxDepth;
    private final Optional<BlockState> stilt;

    public FoundationProcessor(BlockState block, int maxDepth, Optional<BlockState> stilt) {
        this.block = block;
        this.maxDepth = maxDepth;
        this.stilt = stilt;
    }

    /** A foundation with no stilts: a full column also over water. */
    public FoundationProcessor(BlockState block, int maxDepth) {
        this(block, maxDepth, Optional.empty());
    }

    /** The foundation block. */
    public BlockState block() {
        return block;
    }

    /** The largest depth of the foundation, in blocks. */
    public int maxDepth() {
        return maxDepth;
    }

    /** The block of the stilts over water, or empty for a full column. */
    public Optional<BlockState> stilt() {
        return stilt;
    }

    /**
     * True if a column of the lowest layer gets a stilt over water: a corner of the layer, or a column on an edge every
     * {@link #STILT_SPACING} blocks from a corner.
     */
    static boolean isStilt(int x, int z, int minX, int minZ, int maxX, int maxZ) {
        boolean edgeX = x == minX || x == maxX;
        boolean edgeZ = z == minZ || z == maxZ;
        boolean stepX = (x - minX) % STILT_SPACING == 0 || x == maxX;
        boolean stepZ = (z - minZ) % STILT_SPACING == 0 || z == maxZ;
        return (edgeX && stepZ) || (edgeZ && stepX);
    }

    /**
     * Adds the blocks of the foundation after the blocks of the template. The original blocks and the processed
     * blocks are in the same order. An original block in the lowest layer of the template gives the position of a
     * column of the foundation. Only the positions in the box of the placement get a foundation. The placement in the
     * other chunks adds the other columns.
     */
    @Override
    public List<StructureBlockInfo> finalizeProcessing(ServerLevelAccessor level, BlockPos offset, BlockPos pos,
            List<StructureBlockInfo> originalBlockInfos, List<StructureBlockInfo> processedBlockInfos,
            StructurePlaceSettings settings) {
        int lowest = Integer.MAX_VALUE;
        for (StructureBlockInfo original : originalBlockInfos) {
            lowest = Math.min(lowest, original.pos().getY());
        }
        int count = Math.min(originalBlockInfos.size(), processedBlockInfos.size());
        // The edges of the lowest layer, for the stilts.
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int i = 0; i < count; i++) {
            if (originalBlockInfos.get(i).pos().getY() == lowest) {
                BlockPos edge = processedBlockInfos.get(i).pos();
                minX = Math.min(minX, edge.getX());
                minZ = Math.min(minZ, edge.getZ());
                maxX = Math.max(maxX, edge.getX());
                maxZ = Math.max(maxZ, edge.getZ());
            }
        }
        BoundingBox box = settings.getBoundingBox();
        List<StructureBlockInfo> blocks = new ArrayList<>(processedBlockInfos);
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        for (int i = 0; i < count; i++) {
            StructureBlockInfo processed = processedBlockInfos.get(i);
            BlockPos column = processed.pos();
            if (originalBlockInfos.get(i).pos().getY() != lowest
                    || processed.state().getCollisionShape(level, column).isEmpty()
                    || (box != null && !box.isInside(column))) {
                continue;
            }
            BlockState fill = block;
            if (stilt.isPresent() && level.getFluidState(column.below()).is(FluidTags.WATER)) {
                if (!isStilt(column.getX(), column.getZ(), minX, minZ, maxX, maxZ)) {
                    continue;
                }
                fill = stilt.get();
            }
            for (int depth = 1; depth <= maxDepth; depth++) {
                below.set(column.getX(), column.getY() - depth, column.getZ());
                if (level.isOutsideBuildHeight(below)
                        || PalisadePiece.isGround(level, below, level.getBlockState(below))) {
                    break;
                }
                blocks.add(new StructureBlockInfo(below.immutable(), fill, null));
            }
        }
        return blocks;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.FOUNDATION.get();
    }
}
