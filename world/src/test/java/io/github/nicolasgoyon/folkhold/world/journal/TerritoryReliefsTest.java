package io.github.nicolasgoyon.folkhold.world.journal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Relief;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerritoryReliefsTest {

    private static TerritoryMapPayload.Village village(String id, int x, int z, int radius) {
        return new TerritoryMapPayload.Village(new Claimant(id, x, z, 1, radius), 64, id, "Ash", 0xAA5500);
    }

    /** The area contains the claim and the margin, with its sides on multiples of the snap. */
    @Test
    void areaContainsTheClaimWithAMargin() {
        Optional<TerritoryReliefs.Area> area = TerritoryReliefs.area(List.of(village("northam", 100, -50, 3)));

        // The claim reaches 64 blocks from the village, and the margin adds 64 blocks.
        assertEquals(Optional.of(new TerritoryReliefs.Area(-256, -256, 256, 256)), area);
        assertEquals(8, area.orElseThrow().cell());
        assertEquals(64, area.orElseThrow().width());
        assertEquals(64, area.orElseThrow().height());
    }

    /** No discovered village gives no area. */
    @Test
    void noVillageGivesNoArea() {
        assertEquals(Optional.empty(), TerritoryReliefs.area(List.of()));
    }

    /** A small change of the territories keeps the same area: the server calculates the relief once. */
    @Test
    void smallChangeKeepsTheArea() {
        assertEquals(TerritoryReliefs.area(List.of(village("northam", 100, -50, 3))),
                TerritoryReliefs.area(List.of(village("northam", 110, -40, 3))));
    }

    /** A large area has larger cells: the grid keeps its maximum number of cells on the long side. */
    @Test
    void largeAreaKeepsTheNumberOfCells() {
        TerritoryReliefs.Area area = TerritoryReliefs.area(List.of(village("west", -5000, 0, 4),
                village("east", 5000, 100, 4))).orElseThrow();

        assertTrue(area.width() <= TerritoryReliefs.CELLS, "Too many columns: " + area.width());
        assertTrue(area.height() <= TerritoryReliefs.CELLS, "Too many rows: " + area.height());
        assertTrue(area.width() * area.cell() >= area.maxX() - area.minX(), "The grid must cover the area");
        assertTrue(area.minX() <= -5000 - 80 && area.maxX() >= 5000 + 80, "The area must contain the claims");
    }

    /** The relief has one sample at the center of each cell. */
    @Test
    void reliefSamplesTheCenterOfEachCell() {
        TerritoryReliefs.Area area = new TerritoryReliefs.Area(-256, 0, 0, 256);
        RouteFinder.HeightSource terrain = (x, z) -> new RouteFinder.Column(x / 4, z >= 128 ? 3 : 0);

        Relief relief = TerritoryReliefs.calculate(terrain, area);

        assertEquals(area.cell(), relief.cell());
        assertEquals(-256 + area.cell() / 2, relief.minX());
        assertEquals(area.cell() / 2, relief.minZ());
        assertEquals(area.width(), relief.width());
        assertEquals(area.height(), relief.height());
        assertEquals(relief.minX() / 4, relief.floor(0, 0));
        assertEquals(0, relief.water(0, 0));
        assertEquals(3, relief.water(0, relief.height() - 1));
    }
}
