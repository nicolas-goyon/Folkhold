package io.github.nicolasgoyon.folkhold.balance.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TractionTest {
    private static final double EPSILON = 1.0E-9;
    private static final Traction TRACTION = Traction.DEFAULTS;
    private static final LocomotiveStats FURNACE = LocomotiveStats.FURNACE_DEFAULTS;
    private static final double FULL = TRACTION.wagonMass(1.0);
    private static final double EMPTY = TRACTION.wagonMass(0.0);

    @Test
    void wagonMassGrowsWithTheCargo() {
        assertEquals(TRACTION.wagonMass(), EMPTY, EPSILON);
        assertEquals(TRACTION.wagonMass() + TRACTION.cargoMass(), FULL, EPSILON);
        assertEquals(TRACTION.wagonMass() + TRACTION.cargoMass() / 2.0, TRACTION.wagonMass(0.5), EPSILON);
        assertEquals(FULL, TRACTION.wagonMass(3.0), EPSILON);
        assertEquals(EMPTY, TRACTION.wagonMass(Double.NaN), EPSILON);
    }

    @Test
    void pullDecreasesToZeroAtTheMaximumSpeed() {
        assertEquals(FURNACE.force(), FURNACE.pull(0.0), EPSILON);
        assertEquals(FURNACE.force() / 2.0, FURNACE.pull(FURNACE.maxSpeed() / 2.0), EPSILON);
        assertEquals(0.0, FURNACE.pull(FURNACE.maxSpeed()), EPSILON);
        assertEquals(0.0, FURNACE.pull(1.0), EPSILON);
    }

    @Test
    void railsChangeTheResistance() {
        double flat = TRACTION.resistance(0.0, Traction.RailPower.NONE);
        assertEquals(TRACTION.friction(), flat, EPSILON);
        assertEquals(flat + TRACTION.slopeForce(), TRACTION.resistance(1.0, Traction.RailPower.NONE), EPSILON);
        assertEquals(flat - TRACTION.slopeForce(), TRACTION.resistance(-1.0, Traction.RailPower.NONE), EPSILON);
        assertEquals(flat - TRACTION.poweredRailForce(), TRACTION.resistance(0.0, Traction.RailPower.POWERED),
                EPSILON);
        assertEquals(flat + TRACTION.brakeForce(), TRACTION.resistance(0.0, Traction.RailPower.UNPOWERED), EPSILON);
    }

    @Test
    void speedIsBounded() {
        assertEquals(0.01, Traction.nextSpeed(0.0, 0.1, 0.0, 10.0, 0.4), EPSILON);
        assertEquals(0.4, Traction.nextSpeed(0.39, 10.0, 0.0, 1.0, 0.4), EPSILON);
        // A train that is too heavy for the slope stops and does not slide back.
        assertEquals(0.0, Traction.nextSpeed(0.0, 0.1, 5.0, 10.0, 0.4), EPSILON);
        assertEquals(0.0, Traction.nextSpeed(Double.NaN, 0.1, 0.0, 10.0, 0.4), EPSILON);
        assertEquals(0.0, Traction.nextSpeed(0.1, Double.POSITIVE_INFINITY, 0.0, 10.0, 0.4), EPSILON);
        assertEquals(0.4, Traction.nextSpeed(0.0, 1.0, 0.0, 0.0, 0.4), EPSILON);
    }

    @Test
    void twoFullWagonsClimbAndTwelveFullWagonsStop() {
        double two = FURNACE.mass() + 2 * FULL;
        double slope = TRACTION.resistance(1.0, Traction.RailPower.NONE);
        assertTrue(FURNACE.pull(0.0) > two * slope, "2 full wagons do not climb a slope");

        // The locomotive and 3 wagons on the slope, 9 full wagons on the flat rails behind.
        double flat = TRACTION.resistance(0.0, Traction.RailPower.NONE);
        double twelve = (FURNACE.mass() + 3 * FULL) * slope + 9 * FULL * flat;
        assertTrue(FURNACE.pull(0.0) < twelve, "12 full wagons climb a slope");
    }

    @Test
    void emptyTrainIsFasterThanFullTrain() {
        assertTrue(topSpeed(8, EMPTY) > topSpeed(8, FULL) + 0.05);
        assertTrue(topSpeed(8, FULL) <= FURNACE.maxSpeed());
    }

    /** The speed of a train on flat rails after 2000 ticks. */
    private static double topSpeed(int wagons, double wagonMass) {
        double mass = FURNACE.mass() + wagons * wagonMass;
        double resistance = mass * TRACTION.resistance(0.0, Traction.RailPower.NONE);
        double speed = 0.0;
        for (int tick = 0; tick < 2000; tick++) {
            speed = Traction.nextSpeed(speed, FURNACE.pull(speed), resistance, mass, FURNACE.maxSpeed());
        }
        return speed;
    }
}
