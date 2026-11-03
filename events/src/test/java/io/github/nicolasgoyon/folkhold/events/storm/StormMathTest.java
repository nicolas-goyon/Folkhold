package io.github.nicolasgoyon.folkhold.events.storm;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class StormMathTest {

    @Test
    void windAngleZeroBlowsToTheEast() {
        assertEquals(1.0, StormMath.windX(0.0), 1e-9);
        assertEquals(0.0, StormMath.windZ(0.0), 1e-9);
        assertEquals(1.0, StormMath.windZ(90.0), 1e-9);
    }

    @Test
    void frontGoesFromTheUpwindEdgeToTheDownwindEdge() {
        assertEquals(0.0, StormMath.front(-20.0, 0.0, 1.0, 0.0, 20), 1e-9);
        assertEquals(0.5, StormMath.front(0.0, 7.0, 1.0, 0.0, 20), 1e-9);
        assertEquals(1.0, StormMath.front(20.0, 0.0, 1.0, 0.0, 20), 1e-9);
        assertEquals(1.0, StormMath.front(40.0, 0.0, 1.0, 0.0, 20), 1e-9);
    }

    @Test
    void onlyTheUpwindFacesFaceTheWind() {
        // Wind to the east: the west face of a block looks into the wind.
        assertTrue(StormMath.facesWind(-1, 0, 1.0, 0.0, 0.3));
        assertFalse(StormMath.facesWind(1, 0, 1.0, 0.0, 0.3));
        assertFalse(StormMath.facesWind(0, -1, 1.0, 0.0, 0.3));
        // Wind to the southeast: the west and north faces look into the wind.
        double x = StormMath.windX(45.0);
        double z = StormMath.windZ(45.0);
        assertTrue(StormMath.facesWind(-1, 0, x, z, 0.3));
        assertTrue(StormMath.facesWind(0, -1, x, z, 0.3));
        assertFalse(StormMath.facesWind(0, 1, x, z, 0.3));
    }

    @Test
    void compassNamesTheDirectionOfTheWind() {
        assertEquals("east", StormMath.compass(0.0));
        assertEquals("south", StormMath.compass(90.0));
        assertEquals("west", StormMath.compass(180.0));
        assertEquals("north", StormMath.compass(270.0));
        assertEquals("east", StormMath.compass(350.0));
        assertEquals("northeast", StormMath.compass(-45.0));
    }

    @Test
    void progressStaysBetweenZeroAndOne() {
        assertEquals(0.0, StormMath.progress(-5L, 100L), 1e-9);
        assertEquals(0.5, StormMath.progress(50L, 100L), 1e-9);
        assertEquals(1.0, StormMath.progress(500L, 100L), 1e-9);
        assertEquals(1.0, StormMath.progress(0L, 0L), 1e-9);
    }
}
