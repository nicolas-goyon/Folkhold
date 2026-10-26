package io.github.nicolasgoyon.folkhold.world.client.map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.territory.TerritoryClaims.Claimant;
import io.github.nicolasgoyon.folkhold.world.client.ClientTerritoryMap;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import java.util.List;
import net.minecraft.world.level.Level;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TerritoryHighlightsTest {
    private static final int COLOR = 0x112233;

    @AfterEach
    void forget() {
        ClientTerritoryMap.clear();
        TerritoryHighlights.setShown(true);
    }

    private static void discover() {
        ClientTerritoryMap.set(new TerritoryMapPayload(List.of(new TerritoryMapPayload.Village(
                new Claimant("northam", 8, 8, 2, 2), 70, "Northam", "Ash", COLOR)), List.of()));
    }

    /** The colors of Xaero's mods have blue, green, red and alpha, from the highest byte. */
    @Test
    void colorFormatOfXaero() {
        assertEquals(0x33221144, TerritoryHighlights.color(COLOR, 0x44));
    }

    /**
     * A chunk of a discovered territory has a light fill. Its sides on the border have a strong color: the order is
     * the center, north, east, south and west. The chunk of the village has no border side. The chunk 2 0 is at the
     * east end of the territory.
     */
    @Test
    void chunkColorsShowTheBorder() {
        discover();
        int fill = TerritoryHighlights.color(COLOR, TerritoryHighlights.FILL_ALPHA);
        int border = TerritoryHighlights.color(COLOR, TerritoryHighlights.BORDER_ALPHA);

        assertArrayEquals(new int[] {fill, fill, fill, fill, fill},
                TerritoryHighlights.colors(Level.OVERWORLD, 0, 0, new int[5]));
        assertArrayEquals(new int[] {fill, border, border, border, fill},
                TerritoryHighlights.colors(Level.OVERWORLD, 2, 0, new int[5]));
        assertNull(TerritoryHighlights.colors(Level.OVERWORLD, 3, 0, new int[5]));
    }

    /** The territories show only in the Overworld, and only when the option of the client config is on. */
    @Test
    void territoriesOnlyInTheOverworld() {
        discover();

        assertTrue(TerritoryHighlights.chunkIsHighlit(Level.OVERWORLD, 0, 0));
        assertFalse(TerritoryHighlights.chunkIsHighlit(Level.NETHER, 0, 0));
        assertEquals(0, TerritoryHighlights.regionHash(Level.NETHER, 0, 0));
        TerritoryHighlights.setShown(false);
        assertFalse(TerritoryHighlights.chunkIsHighlit(Level.OVERWORLD, 0, 0));
        assertEquals(0, TerritoryHighlights.regionHash(Level.OVERWORLD, 0, 0));
    }

    /** The region hash changes with each new map of the server. */
    @Test
    void regionHashChangesWithTheMap() {
        discover();
        int first = TerritoryHighlights.regionHash(Level.OVERWORLD, 0, 0);
        discover();

        assertTrue(first != 0);
        assertTrue(first != TerritoryHighlights.regionHash(Level.OVERWORLD, 0, 0));
    }
}
