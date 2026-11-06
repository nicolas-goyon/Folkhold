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
        assertEquals(0.0, COUPLING.impulse(COUPLING.restGap(), 0.5), EPSILON);
        assertEquals(0.0, COUPLING.impulse(COUPLING.bufferGap(), 0.5), EPSILON);
    }

    @Test
    void springPullsInProportionToTheStretch() {
        double small = COUPLING.impulse(COUPLING.restGap() + 0.2, 0.0);
        double large = COUPLING.impulse(COUPLING.restGap() + 0.4, 0.0);
        assertEquals(COUPLING.stiffness() * 0.2, small, EPSILON);
        assertEquals(2.0 * small, large, EPSILON);
    }

    @Test
    void dampingAddsTheSeparationSpeed() {
        double gap = COUPLING.restGap() + 0.2;
        double still = COUPLING.impulse(gap, 0.0);
        double separating = COUPLING.impulse(gap, 0.1);
        assertEquals(COUPLING.damping() * 0.1, separating - still, EPSILON);
    }

    @Test
    void stretchedChainNeverPushes() {
        // The gap of the next tick stays above the buffer gap: only the spring acts.
        assertEquals(0.0, COUPLING.impulse(COUPLING.restGap() + 0.1, -0.1), EPSILON);
    }

    @Test
    void slackChainBetweenTheBufferAndTheRestGapDoesNothing() {
        double gap = (COUPLING.bufferGap() + COUPLING.restGap()) / 2.0;
        assertEquals(0.0, COUPLING.impulse(gap, -0.02), EPSILON);
        assertEquals(0.0, COUPLING.impulse(gap, 0.2), EPSILON);
    }

    @Test
    void fastLeaderInTheSlackChainMovesTheFollowerAway() {
        // The leader comes nearer fast: without a change, the gap of the next tick goes below the buffer gap.
        double gap = (COUPLING.bufferGap() + COUPLING.restGap()) / 2.0;
        double separationSpeed = -0.2;
        double impulse = COUPLING.impulse(gap, separationSpeed);
        assertTrue(impulse < 0.0);
        assertEquals(COUPLING.bufferGap(), gap + separationSpeed - impulse, EPSILON);
    }

    @Test
    void belowTheBufferGapTheBackMinecartSlowsDown() {
        double gap = COUPLING.bufferGap() - 0.1;
        double separationSpeed = -0.2;
        double impulse = COUPLING.impulse(gap, separationSpeed);
        assertTrue(impulse < 0.0);
        // The next gap is the gap plus the separation speed minus the impulse.
        assertEquals(COUPLING.bufferGap(), gap + separationSpeed - impulse, EPSILON);
    }

    @Test
    void belowTheBufferGapMinecartsThatSeparateGetNoChange() {
        assertEquals(0.0, COUPLING.impulse(COUPLING.bufferGap() - 0.1, 0.5), EPSILON);
    }

    @Test
    void aboveTheMaximumGapTheChainActsAsABar() {
        double gap = COUPLING.maxGap() + 0.2;
        double separationSpeed = 0.1;
        double impulse = COUPLING.impulse(gap, separationSpeed);
        // The next gap is the gap plus the separation speed minus the impulse.
        assertTrue(gap + separationSpeed - impulse <= COUPLING.maxGap() + EPSILON);
    }

    @Test
    void impulseHasAMaximum() {
        assertEquals(Coupling.MAX_IMPULSE, COUPLING.impulse(100.0, 10.0), EPSILON);
        assertEquals(-Coupling.MAX_IMPULSE, COUPLING.impulse(0.0, -10.0), EPSILON);
    }

    @Test
    void breakCounterIncreasesOnlyAboveTheBreakGap() {
        assertEquals(1, COUPLING.nextStretchTicks(0, COUPLING.breakGap() + 0.1));
        assertEquals(6, COUPLING.nextStretchTicks(5, COUPLING.breakGap() + 0.1));
        assertEquals(0, COUPLING.nextStretchTicks(5, COUPLING.breakGap()));
    }

    @Test
    void linkBreaksAfterTheTolerance() {
        int ticks = 0;
        double gap = COUPLING.breakGap() + 1.0;
        for (int i = 1; i < COUPLING.breakTicks(); i++) {
            ticks = COUPLING.nextStretchTicks(ticks, gap);
            assertFalse(COUPLING.breaks(ticks), "tick " + i);
        }
        ticks = COUPLING.nextStretchTicks(ticks, gap);
        assertTrue(COUPLING.breaks(ticks));
    }

    @Test
    void shortLagDoesNotBreakTheLink() {
        int ticks = 0;
        for (int i = 0; i < COUPLING.breakTicks() - 1; i++) {
            ticks = COUPLING.nextStretchTicks(ticks, COUPLING.breakGap() + 1.0);
        }
        ticks = COUPLING.nextStretchTicks(ticks, COUPLING.restGap());
        assertEquals(0, ticks);
        assertFalse(COUPLING.breaks(ticks));
    }

    /**
     * A front minecart at the speed of a furnace minecart (0.2 blocks per tick) pulls a minecart with the slowdown of
     * an empty vanilla minecart (0.96 each tick). The gap stays below the maximum gap and settles near the
     * rest gap.
     */
    @Test
    void trainSettlesBetweenTheRestAndTheMaximumGap() {
        double frontSpeed = 0.2;
        double front = COUPLING.restGap();
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
            double gap = front - back;
            largest = Math.max(largest, gap);
            backSpeed += COUPLING.impulse(gap, frontMove - backMove);
            lastBackSpeed = backMove;
        }
        assertTrue(largest <= COUPLING.maxGap(), "largest gap " + largest);
        double gap = front - back;
        assertTrue(gap > COUPLING.restGap() && gap < COUPLING.restGap() + 0.2,
                "final gap " + gap);
        assertEquals(frontSpeed, lastBackSpeed, 1.0E-3);
    }

    /**
     * The front minecart stops at once. The back minecart arrives at the speed of a furnace minecart. It stops at the
     * buffer gap and does not go into the front minecart.
     */
    @Test
    void backMinecartStopsAtTheBufferGap() {
        double front = COUPLING.restGap();
        double back = 0.0;
        double backSpeed = 0.2;
        double smallest = Double.MAX_VALUE;
        for (int tick = 0; tick < 100; tick++) {
            double backMove = backSpeed;
            back += backMove;
            backSpeed *= 0.96;
            double gap = front - back;
            smallest = Math.min(smallest, gap);
            backSpeed += COUPLING.impulse(gap, -backMove);
        }
        assertTrue(smallest >= COUPLING.bufferGap() - 0.2, "smallest gap " + smallest);
        assertTrue(front - back >= COUPLING.bufferGap() - EPSILON, "final gap " + (front - back));
    }

    @Test
    void gapsMustBeInOrder() {
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.6, 1.5, 2.5, 4.0, 40, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.1, 2.0, 1.5, 4.0, 40, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.1, 1.5, 2.5, 2.0, 40, 0.1, 0.1));
        assertThrows(IllegalArgumentException.class, () -> new Coupling(1.1, 1.5, 2.5, 4.0, 0, 0.1, 0.1));
    }
}
