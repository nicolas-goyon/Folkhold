package io.github.nicolasgoyon.folkhold.world.village;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

class VillageLifeTest {
    private static final String ASSETS = "/assets/" + FolkholdWorld.MOD_ID + "/";
    private static final String KEY = "screen.folkhold_world.village_heart.";

    /** Each market state has its own text, in English and in French. */
    @Test
    void eachMarketStateHasAText() throws IOException {
        Set<String> keys = new HashSet<>();
        for (VillageMarket.State state : VillageMarket.State.values()) {
            String key = VillageLife.marketStateKey(state);
            assertTrue(keys.add(key), "Two states have the text " + key);
            assertTranslated(key);
        }
        assertEquals(KEY + "market.no_space", VillageLife.marketStateKey(VillageMarket.State.NO_SPACE));
        assertEquals(KEY + "market.unknown", VillageLife.marketStateKey(null));
        assertTranslated(VillageLife.marketStateKey(null));
        assertTrue(VillageLife.isBad(VillageMarket.State.NO_SPACE) && VillageLife.isBad(VillageMarket.State.BLOCKED));
        assertFalse(VillageLife.isBad(VillageMarket.State.BUILT) || VillageLife.isBad(VillageMarket.State.HAMLET)
                || VillageLife.isBad(VillageMarket.State.MISSING) || VillageLife.isBad(VillageMarket.State.BUILDING));
    }

    /** A post with a job that is not valid, a valid post with no inhabitant, and an active post. */
    @Test
    void postStateGivesItsText() throws IOException {
        assertEquals(KEY + "post.not_valid", VillageLife.postStateKey(false, 0));
        assertEquals(KEY + "post.not_valid", VillageLife.postStateKey(false, 2));
        assertEquals(KEY + "post.empty", VillageLife.postStateKey(true, 0));
        assertEquals(KEY + "post.active", VillageLife.postStateKey(true, 1));
        for (String key : List.of(VillageLife.postStateKey(false, 0), VillageLife.postStateKey(true, 0),
                VillageLife.postStateKey(true, 1))) {
            assertTranslated(key);
        }
    }

    /** The market day: today, tomorrow, or in some days. The market hours: open or closed. */
    @Test
    void marketDayAndHoursGiveTheirText() throws IOException {
        assertEquals(KEY + "market_day.today", VillageLife.marketDayKey(0));
        assertEquals(KEY + "market_day.tomorrow", VillageLife.marketDayKey(1));
        assertEquals(KEY + "market_day.days", VillageLife.marketDayKey(2));
        assertEquals(KEY + "market_day.days", VillageLife.marketDayKey(6));
        assertEquals(KEY + "market_hours.open", VillageLife.marketHoursKey(true));
        assertEquals(KEY + "market_hours.closed", VillageLife.marketHoursKey(false));
        for (String key : List.of(VillageLife.marketDayKey(0), VillageLife.marketDayKey(1),
                VillageLife.marketDayKey(2), VillageLife.marketHoursKey(true), VillageLife.marketHoursKey(false),
                KEY + "new_home.no_space", KEY + "cottages", KEY + "legacy_jobs", KEY + "active_jobs.legacy")) {
            assertTranslated(key);
        }
    }

    /** The market day shows the extra offers: one offer, more offers, and no count with no extra offers. */
    @Test
    void marketDayShowsTheExtraOffers() throws IOException {
        Component none = VillageLife.marketDayText(1, 0);
        assertTrue(none.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(KEY + "market_day.tomorrow"), "Expected no count: " + none);
        Component one = VillageLife.marketDayText(0, 1);
        assertTrue(one.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(KEY + "market_day.offer") && contents.getArgs()[1].equals(1)
                && contents.getArgs()[0] instanceof Component day && day.getContents() instanceof TranslatableContents
                        dayContents && dayContents.getKey().equals(KEY + "market_day.today"),
                "Expected today with 1 offer: " + one);
        Component two = VillageLife.marketDayText(3, 2);
        assertTrue(two.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(KEY + "market_day.offers") && contents.getArgs()[1].equals(2),
                "Expected 2 offers: " + two);
        assertTranslated(VillageLife.marketDayOffersKey(1));
        assertTranslated(VillageLife.marketDayOffersKey(2));
    }

    /** Each job file of the mod has a name in English and in French. */
    @Test
    void eachJobHasAName() throws IOException, URISyntaxException {
        URL folder = VillageLifeTest.class.getResource("/data/" + FolkholdWorld.MOD_ID + "/folkhold/jobs");
        assertNotNull(folder, "No folder of the job files");
        Path jobs = Path.of(folder.toURI());
        assertTrue(Files.isDirectory(jobs), "No job files in " + jobs);
        try (Stream<Path> files = Files.list(jobs)) {
            List<String> ids = files.map(file -> file.getFileName().toString())
                    .filter(name -> name.endsWith(".json"))
                    .map(name -> FolkholdWorld.MOD_ID + ":" + name.substring(0, name.length() - 5))
                    .toList();
            assertFalse(ids.isEmpty(), "No job files");
            for (String id : ids) {
                assertTranslated(VillageLife.jobKey(id));
            }
        }
        assertEquals("job.folkhold_world.farmer", VillageLife.jobKey("folkhold_world:farmer"));
    }

    /** A blank job sign (an empty job id) gives the text "No job", not an empty name. */
    @Test
    void postWithNoJobHasAName() throws IOException {
        Component name = VillageLife.jobName("");
        assertTrue(name.getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(KEY + "post.no_job"), "Expected the text for no job: " + name);
        assertTranslated(KEY + "post.no_job");
        assertTrue(VillageLife.jobName("folkhold_world:farmer").getContents() instanceof TranslatableContents farmer
                && farmer.getKey().equals("job.folkhold_world.farmer") && farmer.getFallback().equals("farmer"),
                "Expected the key and the fallback of the farmer");
    }

    private static void assertTranslated(String key) throws IOException {
        assertTrue(read(ASSETS + "lang/en_us.json").has(key), "No English text " + key);
        assertTrue(read(ASSETS + "lang/fr_fr.json").has(key), "No French text " + key);
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream stream = VillageLifeTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "No resource " + path);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
