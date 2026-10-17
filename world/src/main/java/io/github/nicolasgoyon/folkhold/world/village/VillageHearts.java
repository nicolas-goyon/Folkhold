package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;

/**
 * Positions of the village hearts that are loaded, for each dimension. Server side only.
 *
 * <p>A heart registers itself when its chunk loads and unregisters when it unloads or when the block is
 * removed. A heart in a chunk that is not loaded is not in this list.
 */
public final class VillageHearts {
    private static final Map<ResourceKey<Level>, Set<BlockPos>> LOADED = new HashMap<>();

    /** The vertical half-size of the area of a village, in blocks. */
    private static final double AREA_HEIGHT = 32.0;

    private VillageHearts() {
    }

    /**
     * The area of a village: the village radius around its heart, 32 blocks up and down. The entities of the
     * village are in this area.
     *
     * @param heartPos the position of the heart
     * @return the area
     */
    public static AABB area(BlockPos heartPos) {
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        return new AABB(heartPos).inflate(radius, AREA_HEIGHT, radius);
    }

    public static void register(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            LOADED.computeIfAbsent(level.dimension(), key -> new HashSet<>()).add(pos.immutable());
        }
    }

    public static void unregister(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            Set<BlockPos> hearts = LOADED.get(level.dimension());
            if (hearts != null) {
                hearts.remove(pos);
            }
        }
    }

    /**
     * Finds the nearest loaded heart in a radius.
     *
     * @param level  the level
     * @param pos    the center of the search
     * @param radius the search radius in blocks
     * @return the position of the nearest heart, or empty if there is no heart in the radius
     */
    public static Optional<BlockPos> findNearest(Level level, BlockPos pos, double radius) {
        Set<BlockPos> hearts = LOADED.get(level.dimension());
        if (hearts == null) {
            return Optional.empty();
        }
        double maxSqr = radius * radius;
        BlockPos nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (BlockPos heart : hearts) {
            double sqr = heart.distSqr(pos);
            if (sqr <= maxSqr && sqr < nearestSqr) {
                nearest = heart;
                nearestSqr = sqr;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** Removes all entries. The mod calls this when the server stops. */
    public static void clear() {
        LOADED.clear();
    }
}
