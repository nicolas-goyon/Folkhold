package io.github.nicolasgoyon.folkhold.world.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
}
