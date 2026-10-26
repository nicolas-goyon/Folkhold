package io.github.nicolasgoyon.folkhold.world.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.client.ui.UiRect;
import org.junit.jupiter.api.Test;

class MapViewportTest {
    private static final double EPSILON = 1e-9;
    /** An area of 200 × 100 pixels, with its center at 100 50. */
    private static final UiRect AREA = new UiRect(0, 0, 200, 100);

    /** A zoom keeps the block under the mouse at the same place on the screen. */
    @Test
    void zoomKeepsTheBlockUnderTheMouse() {
        MapViewport viewport = new MapViewport();
        viewport.setScale(0.5);
        MapProjection before = viewport.projection(AREA);
        double blockX = before.blockX(150);
        double blockZ = before.blockZ(20);

        viewport.zoom(2.0, AREA, 150, 20);

        MapProjection after = viewport.projection(AREA);
        assertEquals(1.0, viewport.scale(), EPSILON);
        assertEquals(blockX, after.blockX(150), EPSILON);
        assertEquals(blockZ, after.blockZ(20), EPSILON);
    }

    /** The scale stays between the smallest scale and the largest scale. */
    @Test
    void scaleStaysInItsLimits() {
        MapViewport viewport = new MapViewport();

        viewport.zoom(1000.0, AREA, 100, 50);
        assertEquals(MapViewport.MAX_SCALE, viewport.scale(), EPSILON);
        viewport.zoom(1e-6, AREA, 100, 50);
        assertEquals(MapViewport.MIN_SCALE, viewport.scale(), EPSILON);
    }

    /** A box goes to the center of the map, with the largest scale that shows all the box. */
    @Test
    void fitShowsTheBox() {
        MapViewport viewport = new MapViewport();

        viewport.fit(0, 0, 400, 100, AREA);

        assertEquals(200.0, viewport.centerX(), EPSILON);
        assertEquals(50.0, viewport.centerZ(), EPSILON);
        assertEquals(0.5, viewport.scale(), EPSILON);
    }

    /** The block under the mouse follows the mouse. */
    @Test
    void dragMovesTheMapWithTheMouse() {
        MapViewport viewport = new MapViewport();
        viewport.setScale(0.5);
        viewport.center(10, 20);
        double blockX = viewport.projection(AREA).blockX(60);

        viewport.drag(10, -20);

        assertEquals(-10.0, viewport.centerX(), EPSILON);
        assertEquals(60.0, viewport.centerZ(), EPSILON);
        assertEquals(blockX, viewport.projection(AREA).blockX(70), EPSILON);
    }
}
