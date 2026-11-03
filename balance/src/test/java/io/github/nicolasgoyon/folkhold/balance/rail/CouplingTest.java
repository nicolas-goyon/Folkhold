package io.github.nicolasgoyon.folkhold.balance.rail;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class CouplingTest {
    private static final double EPSILON = 1.0E-9;
    private static final Coupling COUPLING = Coupling.DEFAULTS;

    @Test
    void slackChainDoesNotPull() {
        assertEquals(0.0, COUPLING.impulse(COUPLING.restDistance(), 0.5), EPSILON);
        assertEquals(0.0, COUPLING.impulse(1.0, 0.5), EPSILON);
    }

    @Test
    void springPullsInProportionToTheStretch() {
        double small = COUPLING.impulse(COUPLING.restDistance() + 0.2, 0.0);
        double large = COUPLING.impulse(COUPLING.restDistance() + 0.4, 0.0);
        assertEquals(COUPLING.stiffness() * 0.2, small, EPSILON);
        assertEquals(2.0 * small, large, EPSILON);
    }

    @Test
    void dampingAddsTheSeparationSpeed() {
        double distance = COUPLING.restDistance() + 0.2;
        double still = COUPLING.impulse(distance, 0.0);
        double separating = COUPLING.impulse(distance, 0.1);
        assertEquals(COUPLING.damping() * 0.1, separating - still, EPSILON);
    }

    @Test
    void stretchedChainNeverPushes() {
        assertEquals(0.0, COUPLING.impulse(COUPLING.restDistance() + 0.1, -1.0), EPSILON);
    }

    @Test
    void slackChainBetweenTheBufferAndTheRestDistanceDoesNothing() {
        double distance = (COUPLING.bufferDistance() + COUPLING.restDistance()) / 2.0;
        assertEquals(0.0, COUPLING.impulse(distance, -0.2), EPSILON);
        assertEquals(0.0, COUPLING.impulse(distance, 0.2), EPSILON);
    }

    @Test
    void belowTheBufferDistanceTheBackMinecartSlowsDown() {
        double distance = COUPLING.bufferDistance() - 0.1;
        double separationSpeed = -0.2;
        double impulse = COUPLING.impulse(distance, separationSpeed);
        assertTrue(impulse < 0.0);
        // The next distance is the distance plus the separation speed minus the impulse.
        assertEquals(COUPLING.bufferDistance(), distance + separationSpeed - impulse, EPSILON);
    }

    @Test
    void belowTheBufferDistanceMinecartsThatSeparateGetNoChange() {
        assertEquals(0.0, COUPLING.impulse(COUPLING.bufferDistance() - 0.1, 0.5), EPSILON);
    }

    @Test
    void aboveTheMaximumDistanceTheChainActsAsABar() {
        double distance = COUPLING.maxDistance() + 0.2;
        double separationSpeed = 0.1;
        double impulse = COUPLING.impulse(distance, separationSpeed);
        // The next distance is the distance plus the separation speed minus the impulse.
        assertTrue(distance + separationSpeed - impulse <= COUPLING.maxDistance() + EPSILON);
    }

    @Test
    void impulseHasAMaximum() {
        assertEquals(Coupling.MAX_IMPULSE, COUPLING.impulse(100.0, 10.0), EPSILON);
        assertEquals(-Coupling.MAX_IMPULSE, COUPLING.impulse(0.0, -10.0), EPSILON);
    }

    @Test
    void breakCounterIncreasesOnlyAboveTheBreakDistance() {
        assertEquals(1, COUPLING.nextStretchTicks(0, COUPLING.breakDistance() + 0.1));
        assertEquals(6, COUPLING.nextStretchTicks(5, COUPLING.breakDistance() + 0.1));
        assertEquals(0, COUPLING.nextStretchTicks(5, COUPLING.breakDistance()));
    }

    @Test
    void linkBreaksAfterTheTolerance() {
        int ticks = 0;
        double distance = COUPLING.breakDistance() + 1.0;
        for (int i = 1; i < COUPLING.breakTicks(); i++) {
            ticks = COUPLING.nextStretchTicks(ticks, distance);
            assertFalse(COUPLING.breaks(ticks), "tick " + i);
        }
        ticks = COUPLING.nextStretchTicks(ticks, distance);
        assertTrue(COUPLING.breaks(ticks));
    }

    @Test
    void shortLagDoesNotBreakTheLink() {
        int ticks = 0;
        for (int i = 0; i < COUPLING.breakTicks() - 1; i++) {
            ticks = COUPLING.nextStretchTicks(ticks, COUPLING.breakDistance() + 1.0);
        }
        ticks = COUPLING.nextStretchTicks(ticks, COUPLING.restDistance());
        assertEquals(0, ticks);
        assertFalse(COUPLING.breaks(ticks));
    }

    /**
     * A front minecart at the speed of a furnace minecart (0.2 blocks per tick) pulls a minecart with the slowdown of
     * an empty vanilla minecart (0.96 each tick). The distance stays below the maximum distance and settles near the
     * rest distance.
     */
    @Test
    void trainSettlesBetweenTheRestAndTheMaximumDistance() {
        double frontSpeed = 0.2;
        double front = COUPLING.restDistance();
        double back = 0.0;
        double backSpeed = 0.0;
        double largest = 0.0;
        double lastBackSpeed = 0.0;
        for (int tick = 0; tick < 600; tick++) {
            double frontMove = frontSpeed;
            double backMove = backSpeed;
            front += frontMove;
            back += backMove;
            backSpeed *= 0.96;
            double distance = front - back;
            largest = Math.max(largest, distance);
            backSpeed += COUPLING.impulse(distance, frontMove - backMove);
            lastBackSpeed = backMove;
        }
        assertTrue(largest <= COUPLING.maxDistance(), "largest distance " + largest);
        double distance = front - back;
        assertTrue(distance > COUPLING.restDistance() && distance < COUPLING.restDistance() + 0.2,
                "final distance " + distance);
        assertEquals(frontSpeed, lastBackSpeed, 1.0E-3);
    }

    /**
     * The front minecart stops at once. The back minecart arrives at the speed of a furnace minecart. It stops at the
     * buffer distance and does not go into the front minecart.
     */
    @Test
    void backMinecartStopsAtTheBufferDistance() {
        double front = COUPLING.restDistance();
        double back = 0.0;
        double backSpeed = 0.2;
        double smallest = Double.MAX_VALUE;
        for (int tick = 0; tick < 100; tick++) {
            double backMove = backSpeed;
            back += backMove;
            backSpeed *= 0.96;
            double distance = front - back;
            smallest = Math.min(smallest, distance);
            backSpeed += COUPLING.impulse(distance, -backMove);
        }
        assertTrue(smallest >= COUPLING.bufferDistance() - 0.2, "smallest distance " + smallest);
        assertTrue(front - back >= COUPLING.bufferDistance() - EPSILON, "final distance " + (front - back));
    }

    @Test
    void distancesMustBeInOrder() {
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.6, 1.5, 2.5, 4.0, 40, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.1, 2.0, 1.5, 4.0, 40, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.1, 1.5, 2.5, 2.0, 40, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.1, 1.5, 2.5, 4.0, 0, 0.1, 0.1));
    }
}
