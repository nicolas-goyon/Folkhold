package io.github.nicolasgoyon.folkhold.world.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.junit.jupiter.api.Test;

/** The specialties of the cultures in the job files: the jobs that come more often in their settlements. */
class CultureJobsTest {
    private static final Map<String, List<String>> SPECIALTIES = Map.of(
            "valeholm", List.of("fisher", "butcher", "tanner", "miner", "smelter", "lumberjack_spruce",
                    "sawyer_spruce", "shepherd"),
            "ashmark", List.of("farmer", "baker", "blacksmith", "carpenter", "fletcher", "mason", "weaver"),
            "sunreach", List.of("dyer", "gold_smelter", "gardener", "gardener_jungle", "papermaker", "beekeeper",
                    "pastry_cook"));

    private static Job job(String id) throws IOException {
        try (InputStream stream = CultureJobsTest.class.getResourceAsStream(
                "/data/folkhold_world/folkhold/jobs/" + id + ".json");
                Reader reader = new InputStreamReader(Objects.requireNonNull(stream, id), StandardCharsets.UTF_8)) {
            return EconomyData.parseJob(id, JsonParser.parseReader(reader).getAsJsonObject());
        }
    }

    /** Each specialty of a culture has 3 times its weight in the settlements of the culture, and only there. */
    @Test
    void specialtiesHaveTheMultiplierOfTheirCulture() throws IOException {
        for (Map.Entry<String, List<String>> culture : SPECIALTIES.entrySet()) {
            for (String id : culture.getValue()) {
                Job job = job(id);
                assertEquals(Map.of(culture.getKey(), 3), job.cultures(), id);
                assertEquals(job.weight() * 3, job.forCulture(culture.getKey()).weight(), id);
            }
        }
    }

    /** A job with no line "cultures" has the same weight in all the cultures. */
    @Test
    void otherJobsHaveTheSameWeight() throws IOException {
        Job digger = job("digger");
        assertTrue(digger.cultures().isEmpty());
        assertEquals(digger.weight(), digger.forCulture("valeholm").weight());
    }
}
