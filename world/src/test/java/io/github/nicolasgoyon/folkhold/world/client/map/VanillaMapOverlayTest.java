package io.github.nicolasgoyon.folkhold.world.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VanillaMapOverlayTest {
    private static final int RED = 0xFF0000;
    private static final int SIZE = VanillaMapOverlay.SIZE;

    /** A map of scale 0 centered on (64, 64) shows the blocks 0 to 127: one chunk is 16 pixels. */
    private static int[] square(List<VanillaMapOverlay.Marker> markers) {
        // The village owns the chunks 2 to 4 on each axis: the blocks 32 to 79.
        return VanillaMapOverlay.colors(64, 64, 0, (x, z) -> x >= 2 && x <= 4 && z >= 2 && z <= 4
                ? Optional.of("village") : Optional.empty(), Map.of("village", RED), markers);
    }

    /** The border is the outer row of pixels of the territory. Inside and outside, the map stays. */
    @Test
    void borderIsInsideTheTerritory() {
        int[] colors = square(List.of());
        assertEquals(RED, colors[32 * SIZE + 32], "Corner of the territory");
        assertEquals(RED, colors[50 * SIZE + 79], "East side of the territory");
        assertEquals(VanillaMapOverlay.NONE, colors[50 * SIZE + 80], "Outside the territory");
        assertEquals(VanillaMapOverlay.NONE, colors[50 * SIZE + 50], "Inside the territory");
        assertEquals(VanillaMapOverlay.NONE, colors[0], "No territory");
    }

    /** A marker is a square of the color of the faction with a dark outline. */
    @Test
    void markerIsASquareWithAnOutline() {
        int[] colors = square(List.of(new VanillaMapOverlay.Marker(56, 56, RED)));
        assertEquals(RED, colors[56 * SIZE + 56]);
        assertEquals(RED, colors[57 * SIZE + 55]);
        assertEquals(0x202020, colors[58 * SIZE + 56], "Outline");
        assertEquals(VanillaMapOverlay.NONE, colors[59 * SIZE + 56], "Out of the marker");
    }

    /** At the scale 1, a pixel is 2 blocks: the same territory is half as large. */
    @Test
    void scaleChangesTheSizeOfAPixel() {
        int[] colors = VanillaMapOverlay.colors(128, 128, 1, (x, z) -> x >= 2 && x <= 4 && z >= 2 && z <= 4
                ? Optional.of("village") : Optional.empty(), Map.of("village", RED), List.of());
        assertEquals(RED, colors[16 * SIZE + 16]);
        assertEquals(RED, colors[20 * SIZE + 39]);
        assertEquals(VanillaMapOverlay.NONE, colors[20 * SIZE + 40]);
    }

    @Test
    void colorOfATextureIsAbgr() {
        assertEquals(0xFF3322FF, VanillaMapOverlay.abgr(0xFF2233));
    }
}
