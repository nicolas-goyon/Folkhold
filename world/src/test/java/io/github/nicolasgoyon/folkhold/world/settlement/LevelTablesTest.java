package io.github.nicolasgoyon.folkhold.world.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import org.junit.jupiter.api.Test;

class LevelTablesTest {
    /** The data file of the mod has the same values as the default table of the code. */
    @Test
    void dataFileMatchesTheDefaultTable() throws IOException {
        try (InputStream stream = LevelTablesTest.class.getResourceAsStream(
                "/data/folkhold_world/folkhold/levels.json");
                Reader reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            LevelTable file = LevelTables.parse(JsonParser.parseReader(reader).getAsJsonObject());
            LevelTable code = LevelTable.DEFAULT;

            assertEquals(code.maxLevel(), file.maxLevel());
            for (int level = 1; level <= code.maxLevel(); level++) {
                assertEquals(code.traits(level), file.traits(level), "Level " + level);
                assertEquals(code.tier(level).id(), file.tier(level).id(), "Level " + level);
            }
            for (long seed = 0; seed < 1000; seed++) {
                assertEquals(code.startLevel(seed), file.startLevel(seed), "Seed " + seed);
            }
        }
    }

    /** A level with no garrison uses the garrison of the same level in the default table. */
    @Test
    void levelWithNoGarrisonUsesTheDefaultGarrison() {
        JsonObject json = JsonParser.parseString("""
                {
                  "tiers": [{"id": "hamlet", "min_level": 1, "max_level": 2}],
                  "levels": [
                    {"experience": 15, "claim_radius": 9, "max_population": 9, "job_slots": 2,
                     "stock_capacity": 128, "trade_offers": 3, "cats": 1},
                    {"experience": 20, "claim_radius": 9, "max_population": 14, "job_slots": 3,
                     "stock_capacity": 256, "trade_offers": 4, "cats": 1,
                     "garrison": {"guard": 7}, "equipment": "elite"}
                  ],
                  "start_levels": [{"min_level": 1, "max_level": 1, "weight": 1}]
                }
                """).getAsJsonObject();

        LevelTable table = LevelTables.parse(json);

        assertEquals(LevelTable.DEFAULT.traits(1).garrison(), table.traits(1).garrison());
        assertEquals(7, table.traits(2).garrison().count("guard"));
        assertEquals(0, table.traits(2).garrison().count("archer"));
        assertEquals("elite", table.traits(2).garrison().equipment());
    }
}
