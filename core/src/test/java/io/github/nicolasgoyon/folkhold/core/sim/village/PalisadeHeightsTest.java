package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import java.util.Random;
import org.junit.jupiter.api.Test;

class PalisadeHeightsTest {
    /** On flat terrain, each column stands on the terrain. */
    @Test
    void flatTerrainKeepsTheBases() {
        int[] line = new int[12];
        Arrays.fill(line, 64);

        assertArrayEquals(line, PalisadeHeights.bases(line, line.clone()));
    }

    /** Higher terrain near a block lifts its column: the base is 1 block lower than this terrain, or higher. */
    @Test
    void higherTerrainNearTheLineLiftsTheColumn() {
        int[] line = {64, 64, 64, 64, 64, 64};
        int[] near = {64, 64, 70, 64, 64, 64};

        int[] bases = PalisadeHeights.bases(line, near);

        assertArrayEquals(new int[] {67, 68, 69, 68, 67, 66}, bases);
    }

    /** At a cliff, the palisade goes down by 1 block for each column, and it wraps around the ring. */
    @Test
    void cliffMakesASlopeAroundTheRing() {
        int[] line = {80, 60, 60, 60, 60, 60, 60, 60};

        int[] bases = PalisadeHeights.bases(line, line.clone());

        assertArrayEquals(new int[] {80, 79, 78, 77, 76, 77, 78, 79}, bases);
    }

    /** For many random terrains, the bases obey the 3 rules. */
    @Test
    void randomTerrainsObeyTheRules() {
        Random random = new Random(361);
        for (int run = 0; run < 300; run++) {
            int size = 4 + random.nextInt(200);
            int[] line = new int[size];
            int height = 60 + random.nextInt(20);
            for (int i = 0; i < size; i++) {
                height += random.nextInt(9) - 4;
                line[i] = height;
            }
            int[] inside = line.clone();
            int[] outside = line.clone();
            for (int i = 0; i < size; i++) {
                inside[i] += random.nextInt(7) - 3;
                outside[i] += random.nextInt(7) - 3;
            }
            int[] near = PalisadeHeights.near(1, line, inside, outside);

            int[] bases = PalisadeHeights.bases(line, near);

            for (int i = 0; i < size; i++) {
                int next = bases[(i + 1) % size];
                assertTrue(bases[i] >= line[i], "A base is not under the terrain at " + i);
                assertTrue(bases[i] >= near[i] - PalisadeHeights.NEAR_DROP, "A base is near the terrain at " + i);
                assertTrue(Math.abs(bases[i] - next) <= PalisadeHeights.MAX_STEP, "A small step at " + i + ": "
                        + Arrays.toString(bases));
            }
        }
    }

    /** The near terrain is the highest height in the window of each list, around the ring. */
    @Test
    void nearTerrainIsTheHighestInTheWindow() {
        int[] line = {1, 2, 3, 4, 5};
        int[] inside = {0, 9, 0, 0, 0};

        assertArrayEquals(new int[] {9, 9, 9, 5, 5}, PalisadeHeights.near(1, line, inside));
        assertArrayEquals(line, PalisadeHeights.near(0, line));
    }

    /** The lists of heights need the same size. */
    @Test
    void listsOfDifferentSizesAreRefused() {
        assertThrows(IllegalArgumentException.class, () -> PalisadeHeights.bases(new int[3], new int[4]));
        assertThrows(IllegalArgumentException.class, () -> PalisadeHeights.near(1, new int[3], new int[4]));
        assertThrows(IllegalArgumentException.class, () -> PalisadeHeights.near(1));
    }
}
