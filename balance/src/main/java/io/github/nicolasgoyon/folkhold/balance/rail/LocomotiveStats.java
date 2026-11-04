package io.github.nicolasgoyon.folkhold.balance.rail;

/**
 * The stats of a type of locomotive. The configuration gives them. This class is pure Java: it has no game types.
 *
 * @param maxSpeed     the largest speed, in blocks per tick. The vanilla rails do not permit more than 0.4
 * @param force        the pulling force at speed 0. The force decreases in a line to 0 at the maximum speed
 * @param mass         the mass of the locomotive. A wagon with no cargo has a mass of {@code wagonMass} (see
 *                     {@link Traction})
 * @param fuelCapacity the largest quantity of fuel, in ticks of furnace fuel (1 coal = 1600)
 * @param fuelPerBlock the fuel that the locomotive burns for each block that it moves while it runs
 */
public record LocomotiveStats(double maxSpeed, double force, double mass, int fuelCapacity, double fuelPerBlock) {

    /** The default values of the furnace locomotive: about 1 coal for 200 blocks. */
    public static final LocomotiveStats FURNACE_DEFAULTS = new LocomotiveStats(0.4, 0.12, 2.0, 32000, 8.0);

    /**
     * The pulling force at a speed.
     *
     * @param speed the speed of the locomotive, in blocks per tick
     * @return the force, from 0 to {@link #force()}
     */
    public double pull(double speed) {
        if (maxSpeed <= 0.0) {
            return 0.0;
        }
        return Math.max(0.0, force * (1.0 - Math.max(0.0, speed) / maxSpeed));
    }
}
