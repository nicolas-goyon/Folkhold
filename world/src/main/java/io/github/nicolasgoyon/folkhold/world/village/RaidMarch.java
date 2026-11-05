package io.github.nicolasgoyon.folkhold.world.village;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;

/**
 * The route of a raider on its march to a faction village (see {@link VillageRaids}). The palisade of a village is a
 * square around the heart, and the gates are on the sides of this square.
 *
 * <ul>
 *   <li>Outside the palisade, the raider walks to the approach of the nearest gate.</li>
 *   <li>At the approach of an open gate, the raider goes through this gate to the heart. It keeps this gate until it
 *       is inside the palisade.</li>
 *   <li>At the approach of a closed gate, the raider walks to the front of the gate and forces the gate (see
 *       {@code MarchOnVillageGoal}). When the gate opens, the raider goes through it to the heart. A gate that
 *       closes again in front of the raider is forced again.</li>
 *   <li>Inside the palisade, or in a village with no known gates, the raider walks to the heart.</li>
 * </ul>
 */
public final class RaidMarch {
    /** The raider is at an approach at this distance from it, in blocks. */
    public static final double AT_APPROACH = 3.0;

    private RaidMarch() {
    }

    /**
     * One leg of the march.
     *
     * @param target   the position where the raider walks
     * @param entering the approach of the gate that the raider goes through, or null
     * @param forcing  the gate that the raider forces, or null when the gate is open or the raider is not at a gate
     */
    public record Leg(BlockPos target, @Nullable BlockPos entering, @Nullable VillageExits.Approach forcing) {
    }

    /**
     * The next leg of the march.
     *
     * @param heart      the position of the heart
     * @param approaches the approaches of the gates of the village
     * @param isOpen     true for the approach of an open gate
     * @param pos        the position of the raider
     * @param entering   the approach of the gate that the raider goes through, from the previous leg, or null
     * @return the leg
     */
    public static Leg next(BlockPos heart, List<VillageExits.Approach> approaches,
            Predicate<VillageExits.Approach> isOpen, BlockPos pos, @Nullable BlockPos entering) {
        if (approaches.isEmpty() || isInside(heart, approaches, pos)) {
            return new Leg(heart, null, null);
        }
        if (entering != null) {
            for (VillageExits.Approach approach : approaches) {
                if (approach.point().equals(entering)) {
                    return through(heart, approach, isOpen);
                }
            }
        }
        VillageExits.Approach nearest = approaches.stream()
                .min(Comparator.comparingDouble(approach -> approach.point().distSqr(pos)))
                .orElseThrow();
        if (!nearest.point().closerThan(pos, AT_APPROACH)) {
            return new Leg(nearest.point(), null, null);
        }
        return through(heart, nearest, isOpen);
    }

    /** The leg through a gate: to the heart through an open gate, else to the front of the closed gate. */
    private static Leg through(BlockPos heart, VillageExits.Approach approach,
            Predicate<VillageExits.Approach> isOpen) {
        return isOpen.test(approach) ? new Leg(heart, approach.point(), null)
                : new Leg(front(approach), approach.point(), approach);
    }

    /**
     * The position in front of a gate, outside the palisade: one block from the middle fence gate, toward the
     * approach.
     *
     * @param approach the approach of the gate
     * @return the position
     */
    public static BlockPos front(VillageExits.Approach approach) {
        BlockPos middle = approach.gates().get(approach.gates().size() / 2);
        int dx = approach.point().getX() - middle.getX();
        int dz = approach.point().getZ() - middle.getZ();
        return Math.abs(dx) >= Math.abs(dz) ? middle.offset(Integer.signum(dx), 0, 0)
                : middle.offset(0, 0, Integer.signum(dz));
    }

    /**
     * True if a position is inside the palisade: on the square around the heart, nearer than the nearest gate.
     *
     * @param heart      the position of the heart
     * @param approaches the approaches of the gates of the village
     * @param pos        the position
     * @return true inside the palisade, false for a village with no known gates
     */
    public static boolean isInside(BlockPos heart, List<VillageExits.Approach> approaches, BlockPos pos) {
        return !approaches.isEmpty() && squareDistance(heart, pos) < gateDistances(heart, approaches).min().orElse(0);
    }

    /**
     * True if a position is outside the palisade: on the square around the heart, farther than the farthest gate.
     *
     * @param heart      the position of the heart
     * @param approaches the approaches of the gates of the village
     * @param pos        the position
     * @return true outside the palisade, false for a village with no known gates
     */
    public static boolean isOutside(BlockPos heart, List<VillageExits.Approach> approaches, BlockPos pos) {
        return !approaches.isEmpty() && squareDistance(heart, pos) > gateDistances(heart, approaches).max().orElse(0);
    }

    private static IntStream gateDistances(BlockPos heart, List<VillageExits.Approach> approaches) {
        return approaches.stream().flatMap(approach -> approach.gates().stream())
                .mapToInt(gate -> squareDistance(heart, gate));
    }

    /**
     * The distance from a center to a position on the square around the center: the larger of the distances on the
     * X axis and on the Z axis.
     *
     * @param center the center
     * @param pos    the position
     * @return the distance, in blocks
     */
    public static int squareDistance(BlockPos center, BlockPos pos) {
        return Math.max(Math.abs(pos.getX() - center.getX()), Math.abs(pos.getZ() - center.getZ()));
    }
}
