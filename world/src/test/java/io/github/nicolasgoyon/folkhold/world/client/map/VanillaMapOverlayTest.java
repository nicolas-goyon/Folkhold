package io.github.nicolasgoyon.folkhold.world.client.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class VanillaMapOverlayTest {
    private static final int RED = 0xFF0000;
    private static final int BORDER = 0xFF000000 | RED;
    private static final int FILL = VanillaMapOverlay.FILL_ALPHA << 24 | RED;
    private static final int SIZE = VanillaMapOverlay.SIZE;

    private static boolean[] all() {
        boolean[] seen = new boolean[SIZE * SIZE];
        Arrays.fill(seen, true);
        return seen;
    }

    /** A map of scale 0 centered on (64, 64) shows the blocks 0 to 127: one chunk is 16 pixels. */
    private static int[] square(boolean[] seen, List<VanillaMapOverlay.Marker> markers) {
        // The village owns the chunks 2 to 4 on each axis: the blocks 32 to 79.
        return VanillaMapOverlay.colors(64, 64, 0, seen, (x, z) -> x >= 2 && x <= 4 && z >= 2 && z <= 4
                ? Optional.of("village") : Optional.empty(), Map.of("village", RED), markers);
    }

    /** The border is the outer row of pixels of the territory, and the territory has a fill. Outside, the map stays. */
    @Test
    void borderAndFill() {
        int[] colors = square(all(), List.of());
        assertEquals(BORDER, colors[32 * SIZE + 32], "Corner of the territory");
        assertEquals(BORDER, colors[50 * SIZE + 79], "East side of the territory");
        assertEquals(VanillaMapOverlay.NONE, colors[50 * SIZE + 80], "Outside the territory");
        assertEquals(FILL, colors[50 * SIZE + 50], "Inside the territory");
        assertEquals(VanillaMapOverlay.NONE, colors[0], "No territory");
    }

    /** An unexplored pixel has no overlay. A border on an explored pixel shows, also when the inside is unexplored. */
    @Test
    void onlyExploredPixels() {
        boolean[] seen = new boolean[SIZE * SIZE];
        for (int z = 0; z < SIZE; z++) {
            for (int x = 70; x < SIZE; x++) {
                seen[z * SIZE + x] = true;
            }
        }
        int[] colors = square(seen, List.of(new VanillaMapOverlay.Marker(56, 56, RED)));
        assertEquals(BORDER, colors[50 * SIZE + 79], "Explored border");
        assertEquals(FILL, colors[50 * SIZE + 75], "Explored inside");
        assertEquals(VanillaMapOverlay.NONE, colors[50 * SIZE + 50], "Unexplored inside");
        assertEquals(VanillaMapOverlay.NONE, colors[32 * SIZE + 32], "Unexplored border");
        assertEquals(VanillaMapOverlay.NONE, colors[56 * SIZE + 56], "Unexplored village: no marker");
    }

    /** A marker is a square of the color of the faction with a dark outline. */
    @Test
    void markerIsASquareWithAnOutline() {
        int[] colors = square(all(), List.of(new VanillaMapOverlay.Marker(56, 56, RED)));
        assertEquals(BORDER, colors[56 * SIZE + 56]);
        assertEquals(BORDER, colors[57 * SIZE + 55]);
        assertEquals(0xFF202020, colors[58 * SIZE + 56], "Outline");
        assertEquals(FILL, colors[59 * SIZE + 56], "Out of the marker");
    }

    /** At the scale 1, a pixel is 2 blocks: the same territory is half as large. */
    @Test
    void scaleChangesTheSizeOfAPixel() {
        int[] colors = VanillaMapOverlay.colors(128, 128, 1, all(), (x, z) -> x >= 2 && x <= 4 && z >= 2 && z <= 4
                ? Optional.of("village") : Optional.empty(), Map.of("village", RED), List.of());
        assertEquals(BORDER, colors[16 * SIZE + 16]);
        assertEquals(BORDER, colors[20 * SIZE + 39]);
        assertEquals(VanillaMapOverlay.NONE, colors[20 * SIZE + 40]);
    }

    /** The colors of the texture keep their opacity, with the red and the blue in the order of the texture. */
    @Test
    void colorOfATextureIsAbgr() {
        assertEquals(0xFF3322FF, VanillaMapOverlay.abgr(0xFFFF2233));
        assertEquals(0x50332211, VanillaMapOverlay.abgr(0x50112233));
        assertEquals(0, VanillaMapOverlay.abgr(VanillaMapOverlay.NONE));
    }
}
