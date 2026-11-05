package io.github.nicolasgoyon.folkhold.balance.rail;

/**
 * The elastic coupling of a chain between two minecarts. This class is pure Java: it has no game types.
 *
 * <p>All the distances of this class are gaps (see {@link CartEnds#gap}): the distance between the two minecarts minus
 * the length of one minecart. On a straight rail, the gap is the distance between the two ends that face each other.
 * A negative gap tells that the two minecarts go into each other.
 *
 * <p>In this class, the front minecart is the leader of the link and the back minecart is the follower: the follower
 * is farther from the driver of the train (see {@link Trains}). The mod changes only the speed of the back minecart.
 * Each tick, the back minecart gets a speed change along the chain, toward the front minecart:
 * <ul>
 *   <li>below the buffer gap: at most the negative change that brings the gap back to the buffer gap in the next
 *       tick. The two minecarts do not go into each other;</li>
 *   <li>from the buffer gap to the rest gap: no change (the chain is slack);</li>
 *   <li>from the rest gap to the maximum gap: a spring with damping,
 *       {@code stiffness × stretch + damping × separation speed}. The change is not negative;</li>
 *   <li>above the maximum gap: at least the change that brings the gap back to the maximum gap in the next
 *       tick.</li>
 * </ul>
 * The size of the change is never more than {@link #MAX_IMPULSE}.
 *
 * <p>A link breaks when the gap stays above the break gap for {@code breakTicks} ticks.
 *
 * @param bufferGap  the smallest gap between two linked minecarts, in blocks
 * @param restGap    the gap of a slack chain, in blocks
 * @param maxGap     the largest gap of a stretched chain, in blocks
 * @param breakGap   the gap above which the break counter increases, in blocks
 * @param breakTicks the number of ticks above the break gap before the link breaks
 * @param stiffness  the speed change for each block of stretch, in blocks per tick
 * @param damping    the part of the separation speed that the back minecart gets back each tick
 */
public record Coupling(double bufferGap, double restGap, double maxGap, double breakGap, int breakTicks,
                       double stiffness, double damping) {

    /**
     * The default values. The configuration uses them as its defaults. On a straight rail, the distances between the
     * centers of the two minecarts are 1.35 (buffer), 1.5 (rest), 2.5 (maximum) and 4.0 (break) blocks.
     */
    public static final Coupling DEFAULTS = new Coupling(0.1, 0.25, 1.25, 2.75, 40, 0.15, 0.3);

    /** The largest size of a speed change in one tick, in blocks per tick. */
    public static final double MAX_IMPULSE = 0.5;

    public Coupling {
        if (bufferGap < 0.0 || restGap < bufferGap || maxGap < restGap || breakGap < maxGap) {
            throw new IllegalArgumentException("The gaps must be zero or positive and in the order"
                    + " buffer <= rest <= max <= break: " + bufferGap + ", " + restGap + ", " + maxGap + ", "
                    + breakGap);
        }
        if (breakTicks < 1) {
            throw new IllegalArgumentException("breakTicks must be at least 1: " + breakTicks);
        }
    }

    /**
     * The speed change of the back minecart along the chain. A positive value moves the back minecart toward the
     * front minecart.
     *
     * @param gap             the gap between the two minecarts, in blocks
     * @param separationSpeed the speed of the front minecart minus the speed of the back minecart, along the chain,
     *                        in blocks per tick. A positive value moves the minecarts apart.
     * @return the speed change, from {@code -MAX_IMPULSE} to {@link #MAX_IMPULSE}, in blocks per tick
     */
    public double impulse(double gap, double separationSpeed) {
        if (gap < bufferGap) {
            // The change that makes the gap of the next tick equal to the buffer gap.
            return Math.clamp(Math.min(0.0, gap - bufferGap + separationSpeed), -MAX_IMPULSE, 0.0);
        }
        if (gap <= restGap) {
            return 0.0;
        }
        double impulse = stiffness * (gap - restGap) + damping * separationSpeed;
        if (gap > maxGap) {
            // The change that makes the gap of the next tick equal to the maximum gap.
            impulse = Math.max(impulse, gap - maxGap + separationSpeed);
        }
        return Math.clamp(impulse, 0.0, MAX_IMPULSE);
    }

    /**
     * The break counter after one tick.
     *
     * @param ticks the counter before the tick
     * @param gap   the gap between the two minecarts after the tick, in blocks
     * @return the counter plus one if the gap is above the break gap, else 0
     */
    public int nextStretchTicks(int ticks, double gap) {
        return gap > breakGap ? ticks + 1 : 0;
    }

    /**
     * Tells if a link breaks.
     *
     * @param ticks the break counter, from {@link #nextStretchTicks(int, double)}
     * @return true if the counter reaches {@code breakTicks}
     */
    public boolean breaks(int ticks) {
        return ticks >= breakTicks;
    }
}
