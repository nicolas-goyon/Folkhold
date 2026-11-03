package io.github.nicolasgoyon.folkhold.events.earthquake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class EarthquakeMathTest {

    @Test
    void intensityIsOneAtTheEpicenterAndZeroAtTheEdge() {
        assertEquals(1.0, EarthquakeMath.intensity(0.0, 20), 1e-9);
        assertEquals(0.0, EarthquakeMath.intensity(400.0, 20), 1e-9);
        assertEquals(0.0, EarthquakeMath.intensity(900.0, 20), 1e-9);
        assertEquals(0.75, EarthquakeMath.intensity(100.0, 20), 1e-9);
    }

    @Test
    void intensityGoesDownWithTheDistance() {
        double previous = 2.0;
        for (int distance = 0; distance <= 24; distance++) {
            double intensity = EarthquakeMath.intensity(distance * distance, 24);
            assertTrue(intensity < previous, "distance " + distance);
            previous = intensity;
        }
    }

    @Test
    void chanceStaysBetweenZeroAndOne() {
        assertEquals(0.15, EarthquakeMath.chance(0.3, 0.5, 1.0), 1e-9);
        assertEquals(1.0, EarthquakeMath.chance(0.3, 1.0, 10.0), 1e-9);
        assertEquals(0.0, EarthquakeMath.chance(0.3, 0.0, 2.0), 1e-9);
    }

    @Test
    void fragileBlocksHaveMoreChanceThanResistantBlocks() {
        double fragile = EarthquakeMath.chance(0.3, 0.6, 1.0);
        double resistant = EarthquakeMath.chance(0.03, 0.6, 1.0);
        assertTrue(fragile > 5 * resistant);
    }

    @Test
    void nextDelayIsBetweenTheTwoLimits() {
        assertEquals(15 * EarthquakeMath.TICKS_PER_DAY, EarthquakeMath.nextDelayTicks(0.0, 15, 30));
        assertEquals(30 * EarthquakeMath.TICKS_PER_DAY, EarthquakeMath.nextDelayTicks(1.0, 15, 30));
        assertEquals(22 * EarthquakeMath.TICKS_PER_DAY + 12_000L, EarthquakeMath.nextDelayTicks(0.5, 15, 30));
    }

    @Test
    void nextDelayUsesTheMinimumWhenTheMaximumIsLower() {
        assertEquals(15 * EarthquakeMath.TICKS_PER_DAY, EarthquakeMath.nextDelayTicks(0.7, 15, 3));
    }

    @Test
    void crackIsDeepestAtItsCenter() {
        assertEquals(3, EarthquakeMath.crackDepth(0.0, 10.0, 3));
        assertEquals(1, EarthquakeMath.crackDepth(10.0, 10.0, 3));
        assertEquals(1, EarthquakeMath.crackDepth(-9.0, 10.0, 3));
        assertEquals(0, EarthquakeMath.crackDepth(11.0, 10.0, 3));
    }
}
