package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SeaWallTest {
    /** The columns of a ring: {@code ~} for water, another character for land. */
    private static boolean[] ring(String cells) {
        boolean[] water = new boolean[cells.length()];
        for (int i = 0; i < water.length; i++) {
            water[i] = cells.charAt(i) == '~';
        }
        return water;
    }

    /** The kept columns: {@code #} for a column that keeps the palisade, {@code .} for the others. */
    private static String kept(boolean[] keep) {
        StringBuilder out = new StringBuilder();
        for (boolean value : keep) {
            out.append(value ? '#' : '.');
        }
        return out.toString();
    }

    /** A land side: the palisade goes 4 columns into the water at each end. */
    @Test
    void palisadeGoesIntoTheWaterAtTheEnds() {
        assertEquals("....############........", kept(SeaWall.keep(ring("~~~~~~~~LLLL~~~~~~~~~~~~"))));
    }

    /** The ring is closed: a land side at the start of the ring goes into the water at the end of the ring. */
    @Test
    void ringIsClosed() {
        assertEquals("######..........####", kept(SeaWall.keep(ring("LL~~~~~~~~~~~~~~~~~~"))));
    }

    /** An island that the village fills: no palisade. A village on land: all the palisade. */
    @Test
    void islandHasNoPalisade() {
        assertEquals("..........", kept(SeaWall.keep(ring("~~~~~~~~~~"))));
        assertEquals("##########", kept(SeaWall.keep(ring("LLLLLLLLLL"))));
    }
}
