package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;

/**
 * Positions of the village hearts that are loaded, for each dimension, with the area of their village structure.
 * Server side only.
 *
 * <p>A heart registers itself when its chunk loads and unregisters when it unloads or when the block is
 * removed. A heart in a chunk that is not loaded is not in this list.
 *
 * <p>The area of a village has two parts:
 * <ul>
 *   <li>the village radius ({@link WorldConfig#VILLAGE_RADIUS}) around the heart;</li>
 *   <li>the area of the village structure (see {@link VillageReference#area()}): the box of its wall on the horizontal
 *       plane, {@value #AREA_HEIGHT} blocks up and down from the heart. A heart with no village structure has no
 *       structure area.</li>
 * </ul>
 * Thus the area of a large village follows its structure, and the area of a small village is the village radius.
 */
public final class VillageHearts {
    /** The loaded hearts of each dimension, with the structure area of each heart (null for no structure area). */
    private static final Map<ResourceKey<Level>, Map<BlockPos, BoundingBox>> LOADED = new HashMap<>();

    /** The vertical half-size of the area of a village, in blocks. */
    private static final double AREA_HEIGHT = 32.0;

    private VillageHearts() {
    }

    /**
     * The area of a village: the village radius around its heart and the structure area of the heart, 32 blocks up
     * and down. The entities of the village are in this area.
     *
     * @param level    the level of the heart
     * @param heartPos the position of the heart
     * @return the smallest box that contains the two parts of the area
     */
    public static AABB area(Level level, BlockPos heartPos) {
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        AABB area = new AABB(heartPos).inflate(radius, AREA_HEIGHT, radius);
        BoundingBox structure = structureArea(level, heartPos);
        if (structure == null) {
            return area;
        }
        return area.minmax(new AABB(structure.minX(), heartPos.getY() - AREA_HEIGHT, structure.minZ(),
                structure.maxX() + 1.0, heartPos.getY() + 1.0 + AREA_HEIGHT, structure.maxZ() + 1.0));
    }

    /**
     * Registers a loaded heart. A heart that is already registered keeps its structure area.
     *
     * @param level the level
     * @param pos   the position of the heart
     */
    public static void register(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            LOADED.computeIfAbsent(level.dimension(), key -> new HashMap<>()).putIfAbsent(pos.immutable(), null);
        }
    }

    /**
     * Registers a loaded heart with the area of its village structure.
     *
     * @param level     the level
     * @param pos       the position of the heart
     * @param structure the area of the village structure, or empty for a heart with no village structure
     */
    public static void register(Level level, BlockPos pos, Optional<BoundingBox> structure) {
        if (!level.isClientSide()) {
            LOADED.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(pos.immutable(),
                    structure.orElse(null));
        }
    }

    public static void unregister(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            Map<BlockPos, BoundingBox> hearts = LOADED.get(level.dimension());
            if (hearts != null) {
                hearts.remove(pos);
            }
        }
    }

    /** The structure area of a loaded heart, or null when the heart is not loaded or has no structure area. */
    @Nullable
    private static BoundingBox structureArea(Level level, BlockPos heartPos) {
        Map<BlockPos, BoundingBox> hearts = LOADED.get(level.dimension());
        return hearts == null ? null : hearts.get(heartPos);
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
        Map<BlockPos, BoundingBox> hearts = LOADED.get(level.dimension());
        if (hearts == null) {
            return Optional.empty();
        }
        double maxSqr = radius * radius;
        BlockPos nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (BlockPos heart : hearts.keySet()) {
            double sqr = heart.distSqr(pos);
            if (sqr <= maxSqr && sqr < nearestSqr) {
                nearest = heart;
                nearestSqr = sqr;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /**
     * Finds the village of a position: the nearest loaded heart with the position in its village. A position is in
     * the village of a heart when it is at the village radius from the heart or nearer, or when it is in the structure
     * area of the heart (on the horizontal plane, and {@value #AREA_HEIGHT} blocks up or down from the heart or
     * nearer).
     *
     * @param level the level
     * @param pos   the position
     * @return the position of the heart, or empty if the position is in no village
     */
    public static Optional<BlockPos> findAt(Level level, BlockPos pos) {
        Map<BlockPos, BoundingBox> hearts = LOADED.get(level.dimension());
        if (hearts == null) {
            return Optional.empty();
        }
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        double maxSqr = radius * radius;
        BlockPos nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        for (Map.Entry<BlockPos, BoundingBox> entry : hearts.entrySet()) {
            BlockPos heart = entry.getKey();
            double sqr = heart.distSqr(pos);
            if (sqr < nearestSqr && (sqr <= maxSqr || inStructure(entry.getValue(), heart, pos))) {
                nearest = heart;
                nearestSqr = sqr;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** True if a position is in the structure area of a heart. */
    private static boolean inStructure(@Nullable BoundingBox structure, BlockPos heart, BlockPos pos) {
        return structure != null && pos.getX() >= structure.minX() && pos.getX() <= structure.maxX()
                && pos.getZ() >= structure.minZ() && pos.getZ() <= structure.maxZ()
                && Math.abs(pos.getY() - heart.getY()) <= AREA_HEIGHT;
    }

    /** Removes all entries. The mod calls this when the server stops. */
    public static void clear() {
        LOADED.clear();
    }
}
