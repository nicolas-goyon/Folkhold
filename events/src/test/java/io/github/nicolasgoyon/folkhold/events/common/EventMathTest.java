package io.github.nicolasgoyon.folkhold.events.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EventMathTest {

    @Test
    void intensityIsOneAtTheCenterAndZeroAtTheEdge() {
        assertEquals(1.0, EventMath.intensity(0.0, 20), 1e-9);
        assertEquals(0.0, EventMath.intensity(400.0, 20), 1e-9);
        assertEquals(0.0, EventMath.intensity(900.0, 20), 1e-9);
        assertEquals(0.75, EventMath.intensity(100.0, 20), 1e-9);
    }

    @Test
    void intensityGoesDownWithTheDistance() {
        double previous = 2.0;
        for (int distance = 0; distance <= 24; distance++) {
            double intensity = EventMath.intensity(distance * distance, 24);
            assertTrue(intensity < previous, "distance " + distance);
            previous = intensity;
        }
    }

    @Test
    void chanceStaysBetweenZeroAndOne() {
        assertEquals(0.15, EventMath.chance(0.3, 0.5, 1.0), 1e-9);
        assertEquals(1.0, EventMath.chance(0.3, 1.0, 10.0), 1e-9);
        assertEquals(0.0, EventMath.chance(0.3, 0.0, 2.0), 1e-9);
    }

    @Test
    void fragileBlocksHaveMoreChanceThanResistantBlocks() {
        double fragile = EventMath.chance(0.3, 0.6, 1.0);
        double resistant = EventMath.chance(0.03, 0.6, 1.0);
        assertTrue(fragile > 5 * resistant);
    }

    @Test
    void nextDelayIsBetweenTheTwoLimits() {
        assertEquals(15 * EventMath.TICKS_PER_DAY, EventMath.nextDelayTicks(0.0, 15, 30));
        assertEquals(30 * EventMath.TICKS_PER_DAY, EventMath.nextDelayTicks(1.0, 15, 30));
        assertEquals(22 * EventMath.TICKS_PER_DAY + 12_000L, EventMath.nextDelayTicks(0.5, 15, 30));
    }

    @Test
    void nextDelayUsesTheMinimumWhenTheMaximumIsLower() {
        assertEquals(15 * EventMath.TICKS_PER_DAY, EventMath.nextDelayTicks(0.7, 15, 3));
    }

    @Test
    void betweenStaysInTheLimits() {
        assertEquals(0.8, EventMath.between(0.0, 0.8, 1.5), 1e-9);
        assertEquals(1.15, EventMath.between(0.5, 0.8, 1.5), 1e-9);
        assertEquals(0.8, EventMath.between(0.9, 0.8, 0.5), 1e-9);
    }
}
