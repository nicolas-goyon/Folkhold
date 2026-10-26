package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The clearing of a village: no tree grows in a village or against its palisade.
 *
 * <p>The game places the trees of the biome after the structures. In a forest, the trees grow between the buildings,
 * on the flat terrain in front of the gates and against the palisade. Before the first activation of a village, its
 * heart removes the blocks of the trees and the cacti in the area of the village, and in a band of {@link #BAND} blocks
 * around it (see {@link #isCleared}). A waterlogged block leaves its water. The clearing keeps:
 *
 * <ul>
 *   <li>the blocks of the structure pieces (see {@link VillageRepairs#expectedBlocks}). In a piece, the clearing removes
 *       a block of a tree only where the piece has air;</li>
 *   <li>the columns of the palisade, also their logs above the reference of the repairs;</li>
 *   <li>the blocks of the players and of the construction sites (see {@link VillageRepairData#keeps}).</li>
 * </ul>
 */
public final class VillageClearing {
    /** The clearing goes this number of blocks around the area of the village, on the horizontal plane. */
    public static final int BAND = 6;
    /** The clearing goes this number of blocks below the area of the village. */
    private static final int BELOW = 8;
    /** The clearing goes this number of blocks above the area of the village: the tall trees. */
    private static final int ABOVE = 40;

    private VillageClearing() {
    }

    /**
     * The box of the clearing of a village: its area, larger by {@link #BAND} blocks on the horizontal plane, from
     * {@link #BELOW} blocks below the area to {@link #ABOVE} blocks above it.
     *
     * @param reference the reference of the village structure
     * @return the box, or empty for a village with no area
     */
    public static Optional<BoundingBox> box(VillageReference reference) {
        return reference.area().map(area -> new BoundingBox(area.minX() - BAND, area.minY() - BELOW,
                area.minZ() - BAND, area.maxX() + BAND, area.maxY() + ABOVE, area.maxZ() + BAND));
    }

    /**
     * Removes the trees of a village. The method does nothing while a chunk of the box of the clearing is not ready.
     *
     * @param level     the level
     * @param heart     the position of the village heart
     * @param reference the reference of the village structure
     * @return the number of removed blocks, or empty if a chunk is not ready
     */
    public static Optional<Integer> clear(ServerLevel level, BlockPos heart, VillageReference reference) {
        Optional<BoundingBox> found = box(reference);
        if (found.isEmpty()) {
            return Optional.of(0);
        }
        BoundingBox box = found.get();
        if (!ReadyChunks.isAreaReady(level, box.minX(), box.minZ(), box.maxX(), box.maxZ())) {
            return Optional.empty();
        }
        Map<BlockPos, BlockState> expected = VillageRepairs.expectedBlocks(level, reference);
        Set<Long> palisade = new HashSet<>();
        for (BlockPos column : reference.palisadeBlocks().keySet()) {
            palisade.add(ChunkPos.asLong(column.getX(), column.getZ()));
        }
        VillageRepairData data = VillageRepairData.get(level);
        int minY = Math.max(box.minY(), level.getMinBuildHeight());
        int maxY = Math.min(box.maxY(), level.getMaxBuildHeight() - 1);
        int removed = 0;
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), minY, box.minZ(), box.maxX(), maxY, box.maxZ())) {
            BlockState state = level.getBlockState(pos);
            if (!isCleared(state) || palisade.contains(ChunkPos.asLong(pos.getX(), pos.getZ()))) {
                continue;
            }
            BlockState wanted = expected.get(pos);
            if ((wanted != null && !wanted.isAir()) || data.keeps(heart, pos)) {
                continue;
            }
            level.setBlock(pos, state.getFluidState().createLegacyBlock(),
                    Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            removed++;
        }
        return Optional.of(removed);
    }

    /**
     * True for a block that the clearing removes: a block of a tree (a log, leaves, a vine, a cocoa pod, a bee nest, the
     * roots or a propagule of a mangrove) or a cactus.
     */
    static boolean isCleared(BlockState state) {
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.VINE)
                || state.is(Blocks.COCOA) || state.is(Blocks.BEE_NEST) || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.MANGROVE_PROPAGULE) || state.is(Blocks.CACTUS);
    }
}
