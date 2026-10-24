package io.github.nicolasgoyon.folkhold.world.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.registry.ModSounds.UnitVoice;
import io.github.nicolasgoyon.folkhold.world.village.GarrisonRole;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class ModSoundsTest {
    private static final String ASSETS = "/assets/" + FolkholdWorld.MOD_ID + "/";

    /**
     * Each sound event of the mod has its sounds and a subtitle in English and in French. Each vanilla event that the
     * file of the sounds uses exists.
     */
    @Test
    void eachSoundEventHasSoundsAndSubtitles() throws IOException {
        JsonObject sounds = read(ASSETS + "sounds.json");
        JsonObject english = read(ASSETS + "lang/en_us.json");
        JsonObject french = read(ASSETS + "lang/fr_fr.json");
        List<ResourceLocation> events = BuiltInRegistries.SOUND_EVENT.keySet().stream()
                .filter(id -> id.getNamespace().equals(FolkholdWorld.MOD_ID))
                .toList();

        assertFalse(events.isEmpty(), "The mod must register sound events");
        for (ResourceLocation id : events) {
            JsonObject entry = sounds.getAsJsonObject(id.getPath());
            assertNotNull(entry, "No sounds for " + id);
            String subtitle = entry.get("subtitle").getAsString();
            assertTrue(english.has(subtitle), "No English subtitle " + subtitle);
            assertTrue(french.has(subtitle), "No French subtitle " + subtitle);
            assertFalse(entry.getAsJsonArray("sounds").isEmpty(), "No sounds for " + id);
            for (JsonElement element : entry.getAsJsonArray("sounds")) {
                JsonObject sound = element.getAsJsonObject();
                if (sound.has("type") && sound.get("type").getAsString().equals("event")) {
                    ResourceLocation event = ResourceLocation.parse(sound.get("name").getAsString());
                    assertTrue(BuiltInRegistries.SOUND_EVENT.containsKey(event), "Unknown event " + event);
                }
            }
        }
    }

    /** Each role has the five voices, except the golem: it has the sounds of an iron golem. */
    @Test
    void eachRoleExceptTheGolemHasAVoice() {
        for (GarrisonRole role : GarrisonRole.values()) {
            for (UnitVoice voice : UnitVoice.values()) {
                assertEquals(role != GarrisonRole.GOLEM, ModSounds.unitVoice(role, voice).isPresent(),
                        role + " " + voice);
            }
        }
    }

    private static JsonObject read(String path) throws IOException {
        InputStream stream = ModSoundsTest.class.getResourceAsStream(path);
        assertNotNull(stream, "No file " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
