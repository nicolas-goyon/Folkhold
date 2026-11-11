package io.github.nicolasgoyon.folkhold.world.economy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.Test;

class MerchantsMarketTest {
    private static final String LANG = "/assets/" + FolkholdWorld.MOD_ID + "/lang/";

    /** Each refusal of the market rule and each trade place has its text, in English and in French. */
    @Test
    void refusalsHaveTheirTexts() throws IOException {
        String closed = Merchants.refusalKey(Merchants.MarketRefusal.CLOSED);
        String away = Merchants.refusalKey(Merchants.MarketRefusal.AWAY);
        assertNotEquals(closed, away);
        assertEquals("message.folkhold_world.trade_place.market", Merchants.placeKey(true));
        assertEquals("message.folkhold_world.trade_place.plaza", Merchants.placeKey(false));
        for (String lang : List.of("en_us", "fr_fr")) {
            JsonObject texts = read(LANG + lang + ".json");
            for (String key : List.of(closed, closed + ".title", away, away + ".title",
                    "message.folkhold_world.market_hours", Merchants.placeKey(true), Merchants.placeKey(false))) {
                assertTrue(texts.has(key), "No text " + key + " in " + lang);
            }
        }
    }

    private static JsonObject read(String path) throws IOException {
        try (InputStream stream = MerchantsMarketTest.class.getResourceAsStream(path)) {
            assertNotNull(stream, "No resource " + path);
            try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                return JsonParser.parseReader(reader).getAsJsonObject();
            }
        }
    }
}
