package io.github.nicolasgoyon.folkhold.world.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class TerritoryRasterTest {
    /** A discovered village at the block 8 8 (chunk 0 0), with a claim of 3 chunks. */
    private static final TerritoryMapPayload.Village NORTHAM =
            new TerritoryMapPayload.Village(new Claimant("northam", 8, 8, 2, 3), 70, "Northam", "Ash", 0xAA5500);
    /** A settlement that the player did not discover, 4 chunks to the east of Northam. */
    private static final Claimant HIDDEN = new Claimant("hidden", 8 + 4 * 16, 8, 2, 3);

    private static TerritoryMapView view() {
        return new TerritoryMapView(new TerritoryMapPayload(List.of(NORTHAM), List.of(HIDDEN)), 4);
    }

    /** The raster has all the chunks of the discovered village, and no chunk of the other settlement. */
    @Test
    void rasterHasTheChunksOfTheDiscoveredVillages() {
        TerritoryMapView view = view();
        TerritoryRaster raster = TerritoryRaster.of(view);

        int count = 0;
        for (int chunkZ = -8; chunkZ <= 8; chunkZ++) {
            for (int chunkX = -8; chunkX <= 8; chunkX++) {
                boolean discovered = view.villageAt(chunkX, chunkZ).isPresent();
                assertEquals(discovered, raster.at(chunkX, chunkZ).isPresent(), chunkX + " " + chunkZ);
                count += discovered ? 1 : 0;
            }
        }
        assertEquals(count, raster.cells().size());
        assertEquals(Optional.empty(), raster.at(3, 0), "A chunk of the other settlement");
        assertEquals(0, raster.at(0, 0).orElseThrow().village());
        assertEquals(4, raster.version());
    }

    /** A side of a chunk is on a border when the next chunk has another owner, discovered or not, or no owner. */
    @Test
    void bordersAreTheSidesWithAnotherOwner() {
        TerritoryRaster raster = TerritoryRaster.of(view());

        assertEquals(0, raster.at(0, 0).orElseThrow().borders(), "The center has no border");
        int east = raster.at(2, 0).orElseThrow().borders();
        assertTrue((east & TerritoryRaster.EAST) != 0, "Northam and the other settlement");
        assertEquals(0, east & TerritoryRaster.WEST);
        assertTrue((raster.at(0, 3).orElseThrow().borders() & TerritoryRaster.SOUTH) != 0, "Northam and no owner");
        assertTrue((raster.at(0, -2).orElseThrow().borders() & TerritoryRaster.NORTH) != 0, "Northam and no owner");
    }

    /** The chunks are in rows: by Z, then by X. The map draws a row of chunks of a village as one rectangle. */
    @Test
    void chunksAreInRows() {
        List<TerritoryRaster.Cell> cells = TerritoryRaster.of(view()).cells();

        for (int i = 1; i < cells.size(); i++) {
            TerritoryRaster.Cell before = cells.get(i - 1);
            TerritoryRaster.Cell after = cells.get(i);
            assertTrue(before.chunkZ() < after.chunkZ()
                    || (before.chunkZ() == after.chunkZ() && before.chunkX() < after.chunkX()), before + " " + after);
        }
    }

    /** With no discovered village, the raster is empty. */
    @Test
    void noVillageGivesNoChunk() {
        assertEquals(List.of(), TerritoryRaster.of(TerritoryMapView.EMPTY).cells());
    }
}
