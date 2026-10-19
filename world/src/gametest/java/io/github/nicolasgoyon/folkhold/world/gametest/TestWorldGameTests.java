package io.github.nicolasgoyon.folkhold.world.gametest;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.settlement.FixedChunkPlacement;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementDiscovery;
import io.github.nicolasgoyon.folkhold.world.testworld.TestWorldPack;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.FlatLevelSource;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.presets.WorldPreset;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the data pack of the test world ({@link TestWorldPack}). The GameTest server uses the vanilla
 * flat world and does not get the data pack. The tests read the files of the data pack and check them with the
 * registries of the server.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TestWorldGameTests {
    private static final String DATA = "/datapacks/test_world/data/";
    private static final String PRESET = DATA + "folkhold_world/worldgen/world_preset/test.json";
    private static final String VILLAGES = DATA + "folkhold_world/worldgen/structure_set/villages.json";
    private static final String NORMAL_TYPES = DATA + "minecraft/tags/worldgen/world_preset/normal.json";
    /** The village leaves this free space, in blocks, east of the origin: the spawn point is near the origin. */
    private static final int SPAWN_MARGIN = 32;
    /** The village starts at this distance from the origin, in blocks, or nearer: the player sees it. */
    private static final int VIEW_DISTANCE = 128;

    private TestWorldGameTests() {
    }

    /**
     * The GameTest server turns on all the available packs, so it does not get the data pack: the faction villages
     * of the other tests keep their placement.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "test_world_pack")
    public static void gameTestServerDoesNotGetTheDataPack(GameTestHelper helper) {
        PackRepository packs = helper.getLevel().getServer().getPackRepository();

        helper.assertFalse(packs.getAvailableIds().contains(TestWorldPack.PACK_ID),
                "The GameTest server must not get the data pack " + TestWorldPack.PACK_ID);
        helper.succeed();
    }

    /** The world type is a flat world that generates the faction villages, in the list of the world types. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "test_world_preset")
    public static void worldTypeIsFlatWithTheFactionVillages(GameTestHelper helper) {
        FlatLevelSource generator = overworldGenerator(helper);
        boolean villages = generator.settings().structureOverrides()
                .map(sets -> sets.stream().anyMatch(set -> set.is(SettlementDiscovery.VILLAGE_SET)))
                .orElse(false);
        JsonArray types = readJson(NORMAL_TYPES).getAsJsonArray("values");

        helper.assertTrue(villages, "The flat world must generate the faction villages");
        helper.assertTrue(types.contains(new JsonPrimitive("folkhold_world:test")),
                "The world type must be in the tag minecraft:normal");
        helper.succeed();
    }

    /**
     * In the test world, one faction village starts in the start chunk of the data pack. It leaves free space
     * around the spawn point, and the player sees it from the spawn point.
     */
    @GameTest(template = "empty", timeoutTicks = 100, batch = "test_world_village")
    public static void villageStartsNextToTheSpawnPoint(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        MinecraftServer server = level.getServer();
        RegistryAccess registries = server.registryAccess();
        FlatLevelSource generator = overworldGenerator(helper);
        StructureSet set = StructureSet.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, registries), readJson(VILLAGES)).getOrThrow();
        helper.assertTrue(set.placement() instanceof FixedChunkPlacement,
                "The faction villages must have one start chunk");
        FixedChunkPlacement placement = (FixedChunkPlacement) set.placement();
        Structure village = set.structures().get(0).structure().value();
        long seed = level.getSeed();
        RandomState random = RandomState.create(NoiseGeneratorSettings.dummy(),
                registries.lookupOrThrow(Registries.NOISE), seed);

        StructureStart start = village.generate(registries, generator, generator.getBiomeSource(), random,
                server.getStructureManager(), seed, placement.chunk(), 0, level, village.biomes()::contains);

        helper.assertTrue(start.isValid(), "A village must start in the chunk " + placement.chunk());
        BoundingBox box = start.getBoundingBox();
        helper.assertTrue(box.minX() >= SPAWN_MARGIN,
                "The village must leave free space around the spawn point: " + box);
        helper.assertTrue(box.minX() <= VIEW_DISTANCE, "The player must see the village from the spawn point: " + box);
        helper.succeed();
    }

    /** The generator of the Overworld of the world type. */
    private static FlatLevelSource overworldGenerator(GameTestHelper helper) {
        RegistryAccess registries = helper.getLevel().registryAccess();
        WorldPreset preset = WorldPreset.DIRECT_CODEC
                .parse(RegistryOps.create(JsonOps.INSTANCE, registries), readJson(PRESET)).getOrThrow();
        ChunkGenerator generator = preset.overworld().orElseThrow().generator();
        helper.assertTrue(generator instanceof FlatLevelSource, "The Overworld must be flat: " + generator);
        return (FlatLevelSource) generator;
    }

    private static JsonObject readJson(String path) {
        InputStream stream = TestWorldGameTests.class.getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("No resource " + path);
        }
        try (Reader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
