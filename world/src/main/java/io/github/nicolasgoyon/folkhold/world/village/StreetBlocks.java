package io.github.nicolasgoyon.folkhold.world.village;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The surface blocks of the streets of a village: the repairs fill the holes of the streets.
 *
 * <ul>
 *   <li>The heart records the streets once, at its first check of the repairs: for each column of the streets (see
 *       {@link VillageReference#streetColumns()}) in the area of the village, the top block. A column with a top block
 *       that is not a block of a street (air, a fluid, a plant, a fence of a street light) has no surface block.</li>
 *   <li>A hole is a position of the surface with air, a fluid or a replaceable block, for example after an explosion.
 *       A changed block that is not a hole is not damaged: a player can pave a street.</li>
 *   <li>A block that falls (gravel, sand) becomes a bridge block: the planks of the wood of the palisade, else the base
 *       block of the palisade. Thus the repair of a deep hole gives a bridge over the hole.</li>
 * </ul>
 */
public final class StreetBlocks {
    /** The bridge block of a village with no palisade. */
    private static final BlockState DEFAULT_BRIDGE = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final String LOG = "_log";
    private static final String PLANKS = "_planks";

    private StreetBlocks() {
    }

    /**
     * Records the surface blocks of the streets of a village. The chunks of the streets must be ready.
     *
     * @param level     the level
     * @param reference the reference of the village
     * @return the surface block of each recorded position
     */
    public static Map<BlockPos, BlockState> record(ServerLevel level, VillageReference reference) {
        BlockState bridge = bridge(reference);
        Map<BlockPos, BlockState> blocks = new HashMap<>();
        for (BlockPos column : reference.streetColumns()) {
            if (reference.area().isPresent() && !reference.isInArea(column)) {
                continue;
            }
            BlockPos pos = new BlockPos(column.getX(),
                    level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ()) - 1,
                    column.getZ());
            BlockState state = level.getBlockState(pos);
            if (isStreetBlock(level, pos, state)) {
                blocks.put(pos, state.getBlock() instanceof FallingBlock ? bridge : state);
            }
        }
        return blocks;
    }

    /**
     * The holes of the streets of a village.
     *
     * @param level     the level
     * @param reference the reference of the village, with its streets
     * @return the positions of the holes
     */
    public static Set<BlockPos> holes(ServerLevel level, VillageReference reference) {
        Set<BlockPos> holes = new LinkedHashSet<>();
        for (BlockPos pos : reference.streets().keySet()) {
            if (isHole(level.getBlockState(pos))) {
                holes.add(pos);
            }
        }
        return holes;
    }

    /** True if a block is a hole in a street: air, a fluid or a replaceable block. */
    public static boolean isHole(BlockState state) {
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced();
    }

    /** True if a top block can be the surface of a street: a full block, or a block of path. */
    static boolean isStreetBlock(ServerLevel level, BlockPos pos, BlockState state) {
        return !isHole(state) && (state.is(Blocks.DIRT_PATH) || state.isCollisionShapeFullBlock(level, pos));
    }

    /** The bridge block of a village: the planks of the log of its palisade, else the base of its palisade. */
    static BlockState bridge(VillageReference reference) {
        if (reference.palisades().isEmpty()) {
            return DEFAULT_BRIDGE;
        }
        VillageReference.Palisade palisade = reference.palisades().get(0);
        ResourceLocation log = BuiltInRegistries.BLOCK.getKey(palisade.log().getBlock());
        if (log.getPath().endsWith(LOG)) {
            String path = log.getPath().substring(0, log.getPath().length() - LOG.length()) + PLANKS;
            Optional<Block> planks = BuiltInRegistries.BLOCK.getOptional(log.withPath(path));
            if (planks.isPresent()) {
                return planks.get().defaultBlockState();
            }
        }
        return palisade.base().getBlock() instanceof FallingBlock ? DEFAULT_BRIDGE : palisade.base();
    }
}
