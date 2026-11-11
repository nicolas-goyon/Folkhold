package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
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
 *
 * <p>A heart that registers with no known structure area is "not searched". The first {@link #area} or
 * {@link #findAt} that needs its structure area asks the loaded heart for it (see
 * {@link VillageHeartBlockEntity#repairReference}). The heart keeps the result, thus the search occurs one time after
 * each load.
 */
public final class VillageHearts {
    /**
     * The loaded hearts of each dimension, with the structure area of each heart: empty for a heart with no structure
     * area, null for a heart that is not searched.
     */
    private static final Map<ResourceKey<Level>, Map<BlockPos, Optional<BoundingBox>>> LOADED = new HashMap<>();

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
        Optional<BoundingBox> found = structureArea(level, heartPos);
        if (found.isEmpty()) {
            return area;
        }
        BoundingBox structure = found.get();
        return area.minmax(new AABB(structure.minX(), heartPos.getY() - AREA_HEIGHT, structure.minZ(),
                structure.maxX() + 1.0, heartPos.getY() + 1.0 + AREA_HEIGHT, structure.maxZ() + 1.0));
    }

    /**
     * Registers a loaded heart. A heart that is already registered keeps its structure area. A new heart is not
     * searched: its structure area comes from the heart when a search needs it.
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
            LOADED.computeIfAbsent(level.dimension(), key -> new HashMap<>()).put(pos.immutable(), structure);
        }
    }

    public static void unregister(Level level, BlockPos pos) {
        if (!level.isClientSide()) {
            Map<BlockPos, Optional<BoundingBox>> hearts = LOADED.get(level.dimension());
            if (hearts != null) {
                hearts.remove(pos);
            }
        }
    }

    /**
     * True if a heart is registered (see {@link #register}).
     *
     * @param level the level
     * @param pos   the position of the heart
     * @return true for a registered heart
     */
    public static boolean isRegistered(Level level, BlockPos pos) {
        Map<BlockPos, Optional<BoundingBox>> hearts = LOADED.get(level.dimension());
        return hearts != null && hearts.containsKey(pos);
    }

    /** The structure area of a heart. A heart that is not loaded or not searched yet gives empty. */
    private static Optional<BoundingBox> structureArea(Level level, BlockPos heartPos) {
        Map<BlockPos, Optional<BoundingBox>> hearts = LOADED.get(level.dimension());
        return hearts == null || !hearts.containsKey(heartPos) ? Optional.empty()
                : resolve(level, hearts, heartPos);
    }

    /**
     * The structure area of a registered heart. For a heart that is not searched, the heart gives its structure area
     * (see {@link VillageHeartBlockEntity#repairReference}), and the registry keeps it. Only the server thread asks the
     * heart, and only when its chunk is loaded.
     */
    private static Optional<BoundingBox> resolve(Level level, Map<BlockPos, Optional<BoundingBox>> hearts,
            BlockPos heartPos) {
        Optional<BoundingBox> structure = hearts.get(heartPos);
        if (structure != null) {
            return structure;
        }
        if (level instanceof ServerLevel serverLevel && serverLevel.getServer().isSameThread()
                && serverLevel.isLoaded(heartPos)
                && serverLevel.getBlockEntity(heartPos) instanceof VillageHeartBlockEntity heart) {
            Optional<BoundingBox> found = heart.repairReference(serverLevel).flatMap(VillageReference::area);
            hearts.put(heartPos, found);
            return found;
        }
        return Optional.empty();
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
        Map<BlockPos, Optional<BoundingBox>> hearts = LOADED.get(level.dimension());
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
        Map<BlockPos, Optional<BoundingBox>> hearts = LOADED.get(level.dimension());
        if (hearts == null) {
            return Optional.empty();
        }
        double radius = WorldConfig.VILLAGE_RADIUS.get();
        double maxSqr = radius * radius;
        BlockPos nearest = null;
        double nearestSqr = Double.MAX_VALUE;
        // A copy of the keys: the search of a structure area changes the values of the map.
        for (BlockPos heart : List.copyOf(hearts.keySet())) {
            double sqr = heart.distSqr(pos);
            if (sqr < nearestSqr && (sqr <= maxSqr || inStructure(resolve(level, hearts, heart), heart, pos))) {
                nearest = heart;
                nearestSqr = sqr;
            }
        }
        return Optional.ofNullable(nearest);
    }

    /** True if a position is in the structure area of a heart. */
    private static boolean inStructure(Optional<BoundingBox> structure, BlockPos heart, BlockPos pos) {
        return structure.filter(box -> pos.getX() >= box.minX() && pos.getX() <= box.maxX()
                && pos.getZ() >= box.minZ() && pos.getZ() <= box.maxZ()
                && Math.abs(pos.getY() - heart.getY()) <= AREA_HEIGHT).isPresent();
    }

    /** Removes all entries. The mod calls this when the server stops. */
    public static void clear() {
        LOADED.clear();
    }
}
