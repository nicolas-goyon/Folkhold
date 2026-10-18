package io.github.nicolasgoyon.folkhold.blocks.registry;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.github.nicolasgoyon.folkhold.blocks.FolkholdBlocks;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

class LibraryResourcesTest {
    /**
     * Each block of the library has an item, a block state file, an item model, a loot table and a name in each
     * language.
     */
    @Test
    void eachBlockHasItsResources() throws IOException {
        List<ResourceLocation> blocks = BuiltInRegistries.BLOCK.keySet().stream()
                .filter(id -> id.getNamespace().equals(FolkholdBlocks.MOD_ID))
                .toList();
        JsonObject english = lang("en_us");
        JsonObject french = lang("fr_fr");

        assertFalse(blocks.isEmpty(), "The library must register blocks");
        for (ResourceLocation id : blocks) {
            String path = id.getPath();
            assertTrue(BuiltInRegistries.ITEM.containsKey(id), "No item " + id);
            for (String file : List.of("/assets/folkhold_blocks/blockstates/" + path + ".json",
                    "/assets/folkhold_blocks/models/item/" + path + ".json",
                    "/data/folkhold_blocks/loot_table/blocks/" + path + ".json")) {
                assertNotNull(LibraryResourcesTest.class.getResource(file), "No file " + file);
            }
            String key = "block.folkhold_blocks." + path;
            assertTrue(english.has(key), "No English name " + key);
            assertTrue(french.has(key), "No French name " + key);
        }
    }

    private static JsonObject lang(String language) throws IOException {
        try (InputStream stream = LibraryResourcesTest.class.getResourceAsStream(
                "/assets/folkhold_blocks/lang/" + language + ".json");
                Reader reader = new InputStreamReader(Objects.requireNonNull(stream), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
