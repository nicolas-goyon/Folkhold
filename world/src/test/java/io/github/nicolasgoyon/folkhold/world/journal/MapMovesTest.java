package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class MapMovesTest {
    private static final int SIZE = MapMoves.SIZE;

    @Test
    void directionsGiveTheSteps() {
        assertEquals(-1, MapMoves.dz(MapMoves.NORTH));
        assertEquals(1, MapMoves.dz(MapMoves.SOUTH));
        assertEquals(-1, MapMoves.dx(MapMoves.WEST));
        assertEquals(1, MapMoves.dx(MapMoves.EAST));
        assertEquals(0, MapMoves.dx(MapMoves.NORTH));
        assertEquals(0, MapMoves.dz(MapMoves.EAST));
    }

    /** A move to the east keeps the east half of the old map in the west half of the new map. */
    @Test
    void moveKeepsTheExploredPixels() {
        byte[] colors = new byte[SIZE * SIZE];
        colors[100 + 10 * SIZE] = 7;
        colors[10 + 10 * SIZE] = 9;
        byte[] moved = MapMoves.shifted(colors, MapMoves.STEP, 0);
        assertEquals(7, moved[36 + 10 * SIZE], "The east pixel goes to the west half");
        assertEquals(0, moved[100 + 10 * SIZE], "The east half of the new map is not explored");
        long explored = 0;
        for (byte color : moved) {
            explored += color != 0 ? 1 : 0;
        }
        assertEquals(1, explored, "The west pixel of the old map is out of the new map");
    }
}
