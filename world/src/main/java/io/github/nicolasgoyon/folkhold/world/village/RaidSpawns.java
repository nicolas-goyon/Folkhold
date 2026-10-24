package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Arrays;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Pillager;
import net.minecraft.world.level.PathNavigationRegion;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The spawn position of a wave of raiders at a faction village (see {@link VillageRaids}).
 *
 * <p>The method tries random positions in a ring around the heart. The rules of a good position:
 * <ul>
 *   <li>Ground: the position is on the ground of its column. Leaves do not count as ground.</li>
 *   <li>Land: the ground is a block of the tag {@link #GROUND}, natural ground. A roof, a wall or a palisade is not
 *       in the tag. Water is not land.</li>
 *   <li>Sky: the position is under the open sky, not in a cave and not under a tree.</li>
 *   <li>No hole: the terrain around the position is less than {@link #HOLLOW_DEPTH} blocks higher. A ravine is a
 *       hole.</li>
 *   <li>Room: a ravager has room at the position.</li>
 *   <li>Path: a raider can walk to the nearest approach of a gate, or to the heart when the village has no gates.
 *       The path can cross water: the raiders swim.</li>
 * </ul>
 */
public final class RaidSpawns {
    /** The blocks of natural ground where raiders can spawn. */
    public static final TagKey<Block> GROUND = TagKey.create(Registries.BLOCK,
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "raid_spawn_ground"));
    /** The minimum distance from the heart to the spawn position, in blocks. */
    public static final int MIN_DISTANCE = 40;
    /** The maximum distance from the heart to the spawn position, in blocks. */
    public static final int MAX_DISTANCE = 56;
    /** A position this many blocks lower than the terrain around it is in a hole. */
    public static final int HOLLOW_DEPTH = 3;
    /** The number of random positions that the method tries. */
    private static final int TRIES = 24;
    /** The number of paths that the method calculates, at most. A path costs more than the other checks. */
    private static final int MAX_PATHS = 6;
    /** The maximum number of nodes of a path search. */
    private static final int PATH_NODES = 2048;
    /** The extra size of the search area of a path around the direct distance, in blocks. */
    private static final int PATH_MARGIN = 16;
    /** The columns around a position that give the level of the terrain, as offsets in x and z. */
    private static final int[][] AROUND = {{6, 0}, {-6, 0}, {0, 6}, {0, -6}, {4, 4}, {4, -4}, {-4, 4}, {-4, -4}};

    /** The result of the checks of a position, without the path. */
    public enum Check {
        /** A raider can spawn at the position, if it has a path to the village. */
        GOOD,
        /** The chunk of the position does not tick its entities. */
        NOT_TICKING,
        /** The ground or the position is in water or lava. */
        FLUID,
        /** The ground is not in the tag {@link #GROUND}. */
        NOT_GROUND,
        /** The position is not under the open sky. */
        NO_SKY,
        /** A ravager has no room at the position. */
        NO_ROOM,
        /** The position is in a hole or a ravine. */
        HOLLOW
    }

    private RaidSpawns() {
    }

    /**
     * A spawn position in the ring of {@link #MIN_DISTANCE} to {@link #MAX_DISTANCE} blocks around a heart.
     *
     * @param level      the level
     * @param heart      the position of the heart
     * @param approaches the positions outside the gates of the village (see {@link VillageExits#approaches})
     * @param random     the random source
     * @return a good position, or empty if the method finds none
     */
    public static Optional<BlockPos> find(ServerLevel level, BlockPos heart, List<BlockPos> approaches,
            RandomSource random) {
        return find(level, heart, approaches, random, MIN_DISTANCE, MAX_DISTANCE);
    }

    /**
     * A spawn position in a ring around a heart.
     *
     * @param level       the level
     * @param heart       the position of the heart
     * @param approaches  the positions outside the gates of the village
     * @param random      the random source
     * @param minDistance the inner radius of the ring, in blocks
     * @param maxDistance the outer radius of the ring, in blocks
     * @return a good position (the position of the feet), or empty if the method finds none
     */
    public static Optional<BlockPos> find(ServerLevel level, BlockPos heart, List<BlockPos> approaches,
            RandomSource random, int minDistance, int maxDistance) {
        int paths = 0;
        Map<Check, Integer> rejected = new EnumMap<>(Check.class);
        for (int i = 0; i < TRIES && paths < MAX_PATHS; i++) {
            double angle = random.nextDouble() * Math.PI * 2.0;
            double distance = minDistance + random.nextDouble() * (maxDistance - minDistance);
            BlockPos pos = surface(level, heart.getX() + Mth.floor(Math.cos(angle) * distance),
                    heart.getZ() + Mth.floor(Math.sin(angle) * distance));
            Check check = check(level, pos);
            if (check != Check.GOOD) {
                rejected.merge(check, 1, Integer::sum);
                continue;
            }
            BlockPos target = approaches.stream().min(Comparator.comparingDouble(pos::distSqr)).orElse(heart);
            paths++;
            if (canWalk(level, pos, target)) {
                return Optional.of(pos);
            }
        }
        FolkholdWorld.LOGGER.debug("No raid spawn position near the heart {}: {} positions with no path, rejected {}",
                heart, paths, rejected);
        return Optional.empty();
    }

    /** The first position above the ground of a column. Leaves do not count as ground. */
    static BlockPos surface(ServerLevel level, int x, int z) {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z), z);
    }

    /**
     * Checks a position: on land, on natural ground, under the open sky, with room for a ravager, not in a hole. The
     * method does not check the path.
     *
     * @param level the level
     * @param pos   the position of the feet
     * @return {@link Check#GOOD}, or the first rule that the position breaks
     */
    public static Check check(ServerLevel level, BlockPos pos) {
        if (!level.isPositionEntityTicking(pos)) {
            return Check.NOT_TICKING;
        }
        BlockState ground = level.getBlockState(pos.below());
        if (!ground.getFluidState().isEmpty() || !level.getFluidState(pos).isEmpty()) {
            return Check.FLUID;
        }
        if (!ground.is(GROUND)) {
            return Check.NOT_GROUND;
        }
        if (!level.canSeeSky(pos)) {
            return Check.NO_SKY;
        }
        if (!level.noCollision(EntityType.RAVAGER.getSpawnAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5))) {
            return Check.NO_ROOM;
        }
        return isHollow(level, pos) ? Check.HOLLOW : Check.GOOD;
    }

    /** True if the terrain around a position is {@link #HOLLOW_DEPTH} blocks higher or more: a hole or a ravine. */
    static boolean isHollow(ServerLevel level, BlockPos pos) {
        int[] heights = new int[AROUND.length];
        for (int i = 0; i < AROUND.length; i++) {
            heights[i] = surface(level, pos.getX() + AROUND[i][0], pos.getZ() + AROUND[i][1]).getY();
        }
        Arrays.sort(heights);
        int median = heights[heights.length / 2];
        return pos.getY() <= median - HOLLOW_DEPTH;
    }

    /**
     * True if a pillager can walk from a position to a target. The path search uses a larger area than the
     * navigation of a raider, so it finds a path around the obstacles. The path can cross water.
     *
     * @param level the level
     * @param from  the start, the position of the feet
     * @param to    the target
     * @return true if the path reaches the target
     */
    public static boolean canWalk(ServerLevel level, BlockPos from, BlockPos to) {
        Pillager walker = EntityType.PILLAGER.create(level);
        if (walker == null) {
            return false;
        }
        walker.moveTo(from.getX() + 0.5, from.getY(), from.getZ() + 0.5, 0.0F, 0.0F);
        walker.setOnGround(true);
        WalkNodeEvaluator evaluator = new WalkNodeEvaluator();
        evaluator.setCanPassDoors(true);
        evaluator.setCanFloat(true);
        int range = Mth.ceil(Math.sqrt(from.distSqr(to))) + PATH_MARGIN;
        PathNavigationRegion region = new PathNavigationRegion(level, from.offset(-range, -range, -range),
                from.offset(range, range, range));
        Path path = new PathFinder(evaluator, PATH_NODES).findPath(region, walker, Set.of(to), range, 1, 1.0F);
        walker.discard();
        return path != null && path.canReach();
    }
}
