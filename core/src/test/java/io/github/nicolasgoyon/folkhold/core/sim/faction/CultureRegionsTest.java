package io.github.nicolasgoyon.folkhold.core.sim.faction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

class CultureRegionsTest {
    private static final List<String> CULTURES = List.of("valeholm", "ashmark", "sunreach");

    private static FactionSpec faction(String id, String culture, int x, int z) {
        return new FactionSpec(id, culture, id, 0, Attitude.HOSTILE, Set.of(), x, z);
    }

    /** A block coordinate is in the region that contains it, also for the negative coordinates. */
    @Test
    void regionOfABlock() {
        assertEquals(0, CultureRegions.region(0));
        assertEquals(0, CultureRegions.region(CultureRegions.REGION - 1));
        assertEquals(1, CultureRegions.region(CultureRegions.REGION));
        assertEquals(-1, CultureRegions.region(-1));
        assertEquals(CultureRegions.REGION / 2, CultureRegions.middle(0));
        assertEquals(-CultureRegions.REGION / 2, CultureRegions.middle(-1));
    }

    /** The same seed and the same region give the same culture. */
    @Test
    void cultureIsStable() {
        for (int x = -5; x <= 5; x++) {
            assertEquals(CultureRegions.culture(42L, x, 3, CULTURES, id -> false),
                    CultureRegions.culture(42L, x, 3, CULTURES, id -> false));
        }
    }

    /**
     * A culture with an affinity for the biome of a region gets most of these regions: 8 of 10 in theory. The other
     * cultures also get some regions.
     */
    @Test
    void affinityMakesACultureFrequent() {
        Map<String, Integer> counts = new HashMap<>();
        for (int x = 0; x < 50; x++) {
            for (int z = 0; z < 50; z++) {
                counts.merge(CultureRegions.culture(7L, x, z, CULTURES, "valeholm"::equals), 1, Integer::sum);
            }
        }
        int valeholm = counts.getOrDefault("valeholm", 0);
        assertTrue(valeholm > 1800 && valeholm < 2200, "About 80 % of the regions: " + valeholm);
        assertTrue(counts.getOrDefault("ashmark", 0) > 100, "The other cultures also get regions");
        assertTrue(counts.getOrDefault("sunreach", 0) > 100, "The other cultures also get regions");
    }

    /** A region belongs to the nearest faction of its culture, also when a faction of another culture is nearer. */
    @Test
    void ownerIsTheNearestFactionOfTheCulture() {
        List<FactionSpec> factions = List.of(faction("near", "ashmark", 10, 0), faction("far", "valeholm", 900, 0),
                faction("farther", "valeholm", 2000, 0));

        assertEquals("far", CultureRegions.owner(factions, "valeholm", 0, 0).id());
        assertEquals("near", CultureRegions.owner(factions, "ashmark", 1000, 0).id());
        assertEquals("near", CultureRegions.owner(factions, "sunreach", 0, 0).id(),
                "No faction of the culture: the nearest faction");
    }
}
