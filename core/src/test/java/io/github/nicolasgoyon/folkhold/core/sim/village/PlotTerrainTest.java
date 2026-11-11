package io.github.nicolasgoyon.folkhold.core.sim.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain.Column;
import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain.Result;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlotTerrainTest {
    private static final int GROUND = 70;
    private static final Column LAND = new Column(GROUND, GROUND, false);

    /** Flat land and shallow water are good: the buildings stand on stilts over shallow water. */
    @Test
    void flatLandAndShallowWaterAreGood() {
        assertEquals(Result.GOOD, PlotTerrain.check(0, 0, 8, 8, (x, z) -> LAND));
        Column shallow = new Column(GROUND, GROUND - VillageSite.MAX_SHALLOW_DEPTH, false);
        assertEquals(Result.GOOD, PlotTerrain.check(0, 0, 8, 8, (x, z) -> x == 4 ? shallow : LAND));
    }

    /** One point with water deeper than the shallow water makes the plot bad. */
    @Test
    void deepWaterIsBad() {
        Column deep = new Column(GROUND, GROUND - VillageSite.MAX_SHALLOW_DEPTH - 1, false);
        assertEquals(Result.DEEP_WATER, PlotTerrain.check(0, 0, 8, 8, (x, z) -> x == 8 && z == 8 ? deep : LAND));
    }

    /** One point with lava at the surface makes the plot bad, before the other problems. */
    @Test
    void surfaceLavaIsBad() {
        Column lava = new Column(GROUND, GROUND - 10, true);
        assertEquals(Result.LAVA, PlotTerrain.check(0, 0, 8, 8, (x, z) -> x == 0 && z == 4 ? lava : LAND));
    }

    /** A difference of the surfaces of more than {@link PlotTerrain#MAX_DROP} blocks makes the plot steep. */
    @Test
    void steepSlopeIsBad() {
        int drop = PlotTerrain.MAX_DROP;
        assertEquals(Result.GOOD, PlotTerrain.check(0, 0, 8, 8,
                (x, z) -> new Column(GROUND + x * drop / 8, GROUND + x * drop / 8, false)));
        assertEquals(Result.STEEP, PlotTerrain.check(0, 0, 8, 8,
                (x, z) -> new Column(GROUND + x * (drop + 1) / 8, GROUND + x * (drop + 1) / 8, false)));
    }

    /**
     * The points are on the grid of {@link PlotTerrain#STEP} blocks, also for negative coordinates. A box with no point
     * of the grid on an axis uses its middle. A box in a plot reads only points of the plot.
     */
    @Test
    void pointsAreOnTheGrid() {
        assertEquals(List.of(-8, -4, 0), PlotTerrain.axis(-9, 2));
        assertEquals(List.of(4, 8), PlotTerrain.axis(1, 9));
        assertEquals(List.of(6), PlotTerrain.axis(5, 7));
        List<Integer> read = new ArrayList<>();
        PlotTerrain.check(-9, 1, 2, 9, (x, z) -> {
            read.add(x * 1000 + z);
            return LAND;
        });
        List<Integer> inner = new ArrayList<>();
        PlotTerrain.check(-6, 3, 0, 8, (x, z) -> {
            inner.add(x * 1000 + z);
            return LAND;
        });
        assertEquals(6, read.size());
        assertTrue(read.containsAll(inner), read + " " + inner);
    }
}
