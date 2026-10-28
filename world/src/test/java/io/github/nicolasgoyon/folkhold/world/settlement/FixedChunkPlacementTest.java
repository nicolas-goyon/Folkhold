package io.github.nicolasgoyon.folkhold.world.settlement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import org.junit.jupiter.api.Test;

class FixedChunkPlacementTest {
    /** The structure set of the faction villages in the data pack of the test world. */
    private static final String TEST_WORLD_SET =
            "/datapacks/test_world/data/folkhold_world/worldgen/structure_set/villages.json";

    /** The data pack of the test world gives the faction villages one start chunk, 6 chunks east of the origin. */
    @Test
    void testWorldVillagesHaveOneStartChunk() throws IOException {
        JsonObject set = readJson(TEST_WORLD_SET);

        StructurePlacement placement = StructurePlacement.CODEC.parse(JsonOps.INSTANCE, set.get("placement"))
                .getOrThrow();

        FixedChunkPlacement fixed = assertInstanceOf(FixedChunkPlacement.class, placement);
        assertEquals(new ChunkPos(6, 0), fixed.chunk());
    }

    /** Only the start chunk is a placement chunk. */
    @Test
    void onlyTheStartChunkIsAPlacementChunk() {
        FixedChunkPlacement placement = placement(5, -2);

        assertTrue(placement.isPlacementChunk(null, 5, -2));
        assertFalse(placement.isPlacementChunk(null, 5, -1));
        assertFalse(placement.isPlacementChunk(null, 4, -2));
    }

    /** A placement keeps its start chunk and its salt after an encode and a decode. */
    @Test
    void codecKeepsTheFields() {
        JsonElement json = StructurePlacement.CODEC.encodeStart(JsonOps.INSTANCE, placement(-3, 7)).getOrThrow();

        StructurePlacement read = StructurePlacement.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow();

        assertEquals(new ChunkPos(-3, 7), assertInstanceOf(FixedChunkPlacement.class, read).chunk());
        assertEquals(42, json.getAsJsonObject().get("salt").getAsInt());
        assertEquals("folkhold_world:fixed_chunk", json.getAsJsonObject().get("type").getAsString());
    }

    private static FixedChunkPlacement placement(int chunkX, int chunkZ) {
        return new FixedChunkPlacement(Vec3i.ZERO, StructurePlacement.FrequencyReductionMethod.DEFAULT, 1.0F, 42,
                Optional.empty(), chunkX, chunkZ);
    }

    private static JsonObject readJson(String path) throws IOException {
        InputStream stream = FixedChunkPlacementTest.class.getResourceAsStream(path);
        assertNotNull(stream, "No resource " + path);
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }
}
