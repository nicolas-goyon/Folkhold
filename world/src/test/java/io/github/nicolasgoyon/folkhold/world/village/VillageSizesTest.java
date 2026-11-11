package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import io.github.nicolasgoyon.folkhold.core.sim.level.Tier;
import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSizeTable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class VillageSizesTest {
    /** The data file of the mod has the same values as the default table of the code, and a size for each tier. */
    @Test
    void dataFileMatchesTheDefaultTable() throws IOException {
        try (InputStream stream = VillageSizesTest.class.getResourceAsStream(
                "/data/folkhold_world/folkhold/village_sizes.json");
                Reader reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            VillageSizeTable file = VillageSizes.parse(JsonParser.parseReader(reader).getAsJsonObject());

            assertEquals(List.copyOf(VillageSizeTable.DEFAULT.sizes()), List.copyOf(file.sizes()));
            file.requireTiers(LevelTable.DEFAULT.tiers().stream().map(Tier::id).toList());
        }
    }

    /** The beds rule replaces the plot target. */
    @Test
    void bedsPerPlotGivesTheBedsRule() {
        VillageSizeTable table = VillageSizes.parse(JsonParser.parseString("""
                {"sizes": [{"tier": "hamlet", "beds_per_plot": 3, "spare_share": 0.1, "max_radius": 48,
                            "rings": 0, "wall": "none", "min_territory_radius": 9}]}
                """).getAsJsonObject());

        assertEquals(0, table.size("hamlet").orElseThrow().plots());
        assertEquals(8, table.size("hamlet").orElseThrow().plots(24, 3));
    }

    /** A bad file gives an error that names the tier and the bad value. */
    @Test
    void badFileGivesAClearError() {
        RuntimeException wall = assertThrows(RuntimeException.class, () -> VillageSizes.parse(JsonParser.parseString("""
                {"sizes": [{"tier": "town", "plots": 42, "spare_share": 0.15, "max_radius": 96, "rings": 1,
                            "wall": "moat", "min_territory_radius": 22}]}
                """).getAsJsonObject()));
        assertTrue(wall.getMessage().contains("tier town") && wall.getMessage().contains("moat"), wall.getMessage());

        RuntimeException spare = assertThrows(RuntimeException.class, () -> VillageSizes.parse(JsonParser.parseString("""
                {"sizes": [{"tier": "city", "plots": 53, "spare_share": 0.05, "max_radius": 128, "rings": 2,
                            "wall": "palisade", "min_territory_radius": 27}]}
                """).getAsJsonObject()));
        assertTrue(spare.getMessage().contains("tier city") && spare.getMessage().contains("0.05"),
                spare.getMessage());

        RuntimeException missing = assertThrows(RuntimeException.class, () -> VillageSizes.parse(
                JsonParser.parseString("{\"sizes\": [{\"tier\": \"city\", \"plots\": 53}]}").getAsJsonObject()));
        assertTrue(missing.getMessage().contains("tier city") && missing.getMessage().contains("wall"),
                missing.getMessage());
    }

    /** The apply step checks the tiers of the level table: a table with a missing tier gives the default table. */
    @Test
    void tableWithAMissingTierGivesTheDefaultTable() {
        VillageSizeTable hamletOnly = new VillageSizeTable(
                List.of(VillageSizeTable.DEFAULT.size("hamlet").orElseThrow()));

        assertSame(VillageSizeTable.DEFAULT, VillageSizes.checked(hamletOnly, LevelTable.DEFAULT));
        VillageSizeTable full = new VillageSizeTable(List.copyOf(VillageSizeTable.DEFAULT.sizes()));
        assertSame(full, VillageSizes.checked(full, LevelTable.DEFAULT));
    }
}
