package io.github.nicolasgoyon.folkhold.balance.rail;

/**
 * The elastic coupling of a chain between two minecarts. This class is pure Java: it has no game types.
 *
 * <p>The mod changes only the speed of the back minecart. Each tick, the back minecart gets a speed change along the
 * chain, toward the front minecart:
 * <ul>
 *   <li>below the buffer distance: at most the negative change that brings the distance back to the buffer
 *       distance in the next tick. The two minecarts do not go into each other;</li>
 *   <li>from the buffer distance to the rest distance: no change (the chain is slack);</li>
 *   <li>from the rest distance to the maximum distance: a spring with damping,
 *       {@code stiffness × stretch + damping × separation speed}. The change is not negative;</li>
 *   <li>above the maximum distance: at least the change that brings the distance back to the maximum distance in
 *       the next tick.</li>
 * </ul>
 * The size of the change is never more than {@link #MAX_IMPULSE}.
 *
 * <p>A link breaks when the distance stays above the break distance for {@code breakTicks} ticks.
 *
 * @param bufferDistance the smallest distance between two linked minecarts, in blocks
 * @param restDistance   the distance of a slack chain, in blocks
 * @param maxDistance    the largest distance of a stretched chain, in blocks
 * @param breakDistance  the distance above which the break counter increases, in blocks
 * @param breakTicks     the number of ticks above the break distance before the link breaks
 * @param stiffness      the speed change for each block of stretch, in blocks per tick
 * @param damping        the part of the separation speed that the back minecart gets back each tick
 */
public record Coupling(double bufferDistance, double restDistance, double maxDistance, double breakDistance,
                       int breakTicks, double stiffness, double damping) {

    /** The default values. The configuration uses them as its defaults. */
    public static final Coupling DEFAULTS = new Coupling(1.1, 1.5, 2.5, 4.0, 40, 0.15, 0.3);

    /** The largest size of a speed change in one tick, in blocks per tick. */
    public static final double MAX_IMPULSE = 0.5;

    public Coupling {
        if (bufferDistance <= 0.0 || restDistance < bufferDistance || maxDistance < restDistance
                || breakDistance < maxDistance) {
            throw new IllegalArgumentException("The distances must be positive and in the order"
                    + " buffer <= rest <= max <= break: " + bufferDistance + ", " + restDistance + ", "
                    + maxDistance + ", " + breakDistance);
        }
        if (breakTicks < 1) {
            throw new IllegalArgumentException("breakTicks must be at least 1: " + breakTicks);
        }
    }

    /**
     * The speed change of the back minecart along the chain. A positive value moves the back minecart toward the
     * front minecart.
     *
     * @param distance        the distance between the two minecarts, in blocks
     * @param separationSpeed the speed of the front minecart minus the speed of the back minecart, along the chain,
     *                        in blocks per tick. A positive value moves the minecarts apart.
     * @return the speed change, from {@code -MAX_IMPULSE} to {@link #MAX_IMPULSE}, in blocks per tick
     */
    public double impulse(double distance, double separationSpeed) {
        if (distance < bufferDistance) {
            // The change that makes the distance of the next tick equal to the buffer distance.
            return Math.clamp(Math.min(0.0, distance - bufferDistance + separationSpeed), -MAX_IMPULSE, 0.0);
        }
        if (distance <= restDistance) {
            return 0.0;
        }
        double impulse = stiffness * (distance - restDistance) + damping * separationSpeed;
        if (distance > maxDistance) {
            // The change that makes the distance of the next tick equal to the maximum distance.
            impulse = Math.max(impulse, distance - maxDistance + separationSpeed);
        }
        return Math.clamp(impulse, 0.0, MAX_IMPULSE);
    }

    /**
     * The break counter after one tick.
     *
     * @param ticks    the counter before the tick
     * @param distance the distance between the two minecarts after the tick, in blocks
     * @return the counter plus one if the distance is above the break distance, else 0
     */
    public int nextStretchTicks(int ticks, double distance) {
        return distance > breakDistance ? ticks + 1 : 0;
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
