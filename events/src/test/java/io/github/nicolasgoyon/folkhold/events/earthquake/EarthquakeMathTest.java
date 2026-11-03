package io.github.nicolasgoyon.folkhold.events.earthquake;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class EarthquakeMathTest {

    @Test
    void crackIsDeepestAtItsCenter() {
        assertEquals(3, EarthquakeMath.crackDepth(0.0, 10.0, 3));
        assertEquals(1, EarthquakeMath.crackDepth(10.0, 10.0, 3));
        assertEquals(1, EarthquakeMath.crackDepth(-9.0, 10.0, 3));
        assertEquals(0, EarthquakeMath.crackDepth(11.0, 10.0, 3));
    }
}
