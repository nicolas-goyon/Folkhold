package io.github.nicolasgoyon.folkhold.balance.rail;

/**
 * The load of a train behind a locomotive. This class is pure Java: it has no game types.
 *
 * <p>Each tick, the speed of the locomotive changes by {@code (pull − resistance) / mass}. The mass is the sum of the
 * masses of the locomotive and of the minecarts behind it. The resistance is the sum, for each minecart, of its mass
 * multiplied by the force of its rail: friction, slope, powered rail. The speed is never less than 0, thus a train
 * that is too heavy for a slope stops and does not slide back. The speed is never more than the maximum speed of the
 * locomotive.
 *
 * @param wagonMass        the mass of a wagon with no cargo
 * @param cargoMass        the mass of the cargo of a full wagon
 * @param friction         the friction force for each unit of mass
 * @param slopeForce       the force of a slope of 1 block in 1, for each unit of mass
 * @param poweredRailForce the push of a powered rail that has power, for each unit of mass
 * @param brakeForce       the brake of a powered rail that has no power, for each unit of mass
 */
public record Traction(double wagonMass, double cargoMass, double friction, double slopeForce,
                       double poweredRailForce, double brakeForce) {

    /** The default values. The configuration uses them as its defaults. */
    public static final Traction DEFAULTS = new Traction(1.0, 3.0, 0.001, 0.0078125, 0.06, 0.5);

    /** The effect of a powered rail. */
    public enum RailPower {
        /** Not a powered rail. */
        NONE,
        /** A powered rail with power: it pushes. */
        POWERED,
        /** A powered rail with no power: it brakes. */
        UNPOWERED
    }

    /**
     * The mass of a wagon.
     *
     * @param fill the fill ratio of the slots of the wagon, from 0 (empty) to 1 (full)
     * @return {@code wagonMass + cargoMass × fill}
     */
    public double wagonMass(double fill) {
        double ratio = Double.isFinite(fill) ? Math.clamp(fill, 0.0, 1.0) : 0.0;
        return wagonMass + cargoMass * ratio;
    }

    /**
     * The resistance of the rail of a minecart, for each unit of mass.
     *
     * @param uphill the cosine between the direction of travel and the direction up the slope: 1 up a slope, -1 down
     *               a slope, 0 on a flat rail
     * @param power  the effect of a powered rail
     * @return the force against the movement. A negative value pushes the train.
     */
    public double resistance(double uphill, RailPower power) {
        double force = friction + slopeForce * uphill;
        return switch (power) {
            case NONE -> force;
            case POWERED -> force - poweredRailForce;
            case UNPOWERED -> force + brakeForce;
        };
    }

    /**
     * The speed of the locomotive in the next tick.
     *
     * @param speed      the speed in this tick, in blocks per tick
     * @param pull       the pulling force of the locomotive (see {@link LocomotiveStats#pull(double)})
     * @param resistance the sum of the resistances of the minecarts, each multiplied by its mass
     * @param mass       the mass of the train
     * @param maxSpeed   the largest speed, in blocks per tick
     * @return the speed, from 0 to {@code maxSpeed}. A value that is not finite gives 0.
     */
    public static double nextSpeed(double speed, double pull, double resistance, double mass, double maxSpeed) {
        double next = speed + (pull - resistance) / Math.max(mass, 1.0E-3);
        return Double.isFinite(next) ? Math.clamp(next, 0.0, Math.max(0.0, maxSpeed)) : 0.0;
    }
}
