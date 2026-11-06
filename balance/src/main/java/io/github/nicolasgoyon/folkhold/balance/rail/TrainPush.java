package io.github.nicolasgoyon.folkhold.balance.rail;

/**
 * The choice of the driver of a train with no locomotive at the front and no vanilla furnace minecart with a push
 * (see {@link Trains}). This class is pure Java: it has no game types.
 *
 * <p>Each tick, the mod keeps the motion of each minecart after the correction of the chains. In the next tick, the
 * speed gain of a minecart is its horizontal speed minus this kept speed: the change that the vanilla tick gives
 * (a player push, a powered rail, a slope, a rider, a collision). The chains do not cause a gain, because the mod
 * keeps the motion after the correction. The vanilla slowdown and a brake only decrease the speed, and a curve
 * changes only the direction. Thus they do not cause a gain.
 *
 * <p>The rule:
 * <ul>
 *   <li>The minecart with the largest gain drives the train, if this gain is more than {@link #MIN_GAIN}.</li>
 *   <li>Two gains that are equal in the limit {@link #TIE} are a tie. The driver of the last tick wins a tie. If it
 *       is not in the tie, the minecart nearest to the front of the train wins.</li>
 *   <li>If no minecart has a gain, the driver of the last tick drives again. Thus a train that a push started
 *       coasts with the same driver. If the train has no driver of the last tick, the front minecart drives.</li>
 * </ul>
 * Two pushes in opposite directions: the larger push drives the train. The chains then move the other minecart with
 * the train, and its push stops.
 */
public final class TrainPush {
    /** A speed gain of this value or less is not a push, in blocks per tick. A slope gives 0.0078 each tick. */
    public static final double MIN_GAIN = 1.0E-3;
    /** Two speed gains with a difference of this value or less are a tie, in blocks per tick. */
    public static final double TIE = 1.0E-6;

    private TrainPush() {
    }

    /**
     * The index of the driver of a train.
     *
     * @param gains      the speed gain of each minecart in this tick, from the front of the train, in blocks per tick
     * @param lastDriver the index of the driver of the last tick, or -1 if no minecart of the train drove
     * @return the index of the driver, from 0 to {@code gains.length - 1}
     */
    public static int driver(double[] gains, int lastDriver) {
        if (gains.length == 0) {
            throw new IllegalArgumentException("A train has at least one minecart");
        }
        double largest = Double.NEGATIVE_INFINITY;
        for (double gain : gains) {
            largest = Math.max(largest, gain);
        }
        boolean hasLast = lastDriver >= 0 && lastDriver < gains.length;
        if (largest <= MIN_GAIN) {
            return hasLast ? lastDriver : 0;
        }
        if (hasLast && gains[lastDriver] >= largest - TIE) {
            return lastDriver;
        }
        for (int i = 0; i < gains.length; i++) {
            if (gains[i] >= largest - TIE) {
                return i;
            }
        }
        throw new IllegalStateException("No largest gain");
    }
}
