package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.github.nicolasgoyon.folkhold.world.registry.ModStructures;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
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
 *   <li>The foundation is not a part of the template: the repairs of the village do not change it.</li>
 * </ul>
 *
 * <p>Type {@code folkhold_world:foundation}. Fields: {@code block} (the foundation block) and {@code max_depth}.
 */
public final class FoundationProcessor extends StructureProcessor {
    /** The largest depth of a foundation. */
    public static final int MAX_DEPTH = 64;

    public static final MapCodec<FoundationProcessor> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BlockState.CODEC.fieldOf("block").forGetter(FoundationProcessor::block),
            Codec.intRange(1, MAX_DEPTH).fieldOf("max_depth").forGetter(FoundationProcessor::maxDepth))
            .apply(instance, FoundationProcessor::new));

    private final BlockState block;
    private final int maxDepth;

    public FoundationProcessor(BlockState block, int maxDepth) {
        this.block = block;
        this.maxDepth = maxDepth;
    }

    /** The foundation block. */
    public BlockState block() {
        return block;
    }

    /** The largest depth of the foundation, in blocks. */
    public int maxDepth() {
        return maxDepth;
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
        BoundingBox box = settings.getBoundingBox();
        List<StructureBlockInfo> blocks = new ArrayList<>(processedBlockInfos);
        BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
        int count = Math.min(originalBlockInfos.size(), processedBlockInfos.size());
        for (int i = 0; i < count; i++) {
            StructureBlockInfo processed = processedBlockInfos.get(i);
            BlockPos column = processed.pos();
            if (originalBlockInfos.get(i).pos().getY() != lowest
                    || processed.state().getCollisionShape(level, column).isEmpty()
                    || (box != null && !box.isInside(column))) {
                continue;
            }
            for (int depth = 1; depth <= maxDepth; depth++) {
                below.set(column.getX(), column.getY() - depth, column.getZ());
                if (level.isOutsideBuildHeight(below)
                        || PalisadePiece.isGround(level, below, level.getBlockState(below))) {
                    break;
                }
                blocks.add(new StructureBlockInfo(below.immutable(), block, null));
            }
        }
        return blocks;
    }

    @Override
    protected StructureProcessorType<?> getType() {
        return ModStructures.FOUNDATION.get();
    }
}
