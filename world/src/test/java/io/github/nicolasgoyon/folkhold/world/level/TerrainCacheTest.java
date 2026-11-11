package io.github.nicolasgoyon.folkhold.world.level;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.village.PlotTerrain;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.registries.VanillaRegistries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.MultiNoiseBiomeSource;
import net.minecraft.world.level.biome.MultiNoiseBiomeSourceParameterLists;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseGeneratorSettings;
import net.minecraft.world.level.levelgen.RandomState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

/**
 * The columns of {@link TerrainCache} are the columns of the chunk generator: the vanilla Overworld noise with a fixed
 * seed, on land and on water, at positive and negative coordinates.
 */
class TerrainCacheTest {
    private static final LevelHeightAccessor HEIGHT = LevelHeightAccessor.create(-64, 384);
    /** Chunks on land, on a coast and in the ocean of the seed 783. */
    private static final List<ChunkPos> CHUNKS = List.of(new ChunkPos(0, 0), new ChunkPos(-7, 3),
            new ChunkPos(9, -12), new ChunkPos(-40, -40), new ChunkPos(25, 70));

    private static NoiseBasedChunkGenerator generator;
    private static RandomState random;

    @BeforeAll
    static void noise() {
        HolderLookup.Provider registries = VanillaRegistries.createLookup();
        Holder<NoiseGeneratorSettings> settings = registries.lookupOrThrow(Registries.NOISE_SETTINGS)
                .getOrThrow(NoiseGeneratorSettings.OVERWORLD);
        generator = new NoiseBasedChunkGenerator(MultiNoiseBiomeSource.createFromPreset(
                registries.lookupOrThrow(Registries.MULTI_NOISE_BIOME_SOURCE_PARAMETER_LIST)
                        .getOrThrow(MultiNoiseBiomeSourceParameterLists.OVERWORLD)), settings);
        random = RandomState.create(settings.value(), registries.lookupOrThrow(Registries.NOISE), 783L);
    }

    @Test
    void gridColumnsAreTheColumnsOfTheGenerator() {
        TerrainCache cache = TerrainCache.of(generator, random, HEIGHT);
        int water = 0;
        for (ChunkPos chunk : CHUNKS) {
            for (int dx = 0; dx < 16; dx += TerrainCache.GRID) {
                for (int dz = 0; dz < 16; dz += TerrainCache.GRID) {
                    PlotTerrain.Column column = cache.column(chunk.getMinBlockX() + dx, chunk.getMinBlockZ() + dz);
                    assertSameColumn(chunk.getMinBlockX() + dx, chunk.getMinBlockZ() + dz, column);
                    water += column.depth() > 0 ? 1 : 0;
                }
            }
        }
        assertEquals(CHUNKS.size(), cache.chunks());
        assertTrue(water > 0, "No grid column on water");
    }

    @Test
    void otherColumnsAreTheColumnsOfTheGenerator() {
        TerrainCache cache = TerrainCache.of(generator, random, HEIGHT);
        for (ChunkPos chunk : CHUNKS) {
            for (int i = 0; i < 16; i += 3) {
                int x = chunk.getMinBlockX() + i;
                int z = chunk.getMinBlockZ() + 15 - i;
                assertSameColumn(x, z, cache.column(x, z));
            }
            assertSameColumn(chunk.getMinBlockX() + 4, chunk.getMinBlockZ() + 8,
                    cache.point(chunk.getMinBlockX() + 4, chunk.getMinBlockZ() + 8));
        }
        assertEquals(0, cache.chunks());
    }

    @Test
    void oneNoiseObjectReadsOneChunk() {
        TerrainCache cache = TerrainCache.of(generator, random, HEIGHT);
        cache.column(-16, 32);
        cache.column(-4, 44);
        assertEquals(1, cache.chunks());
        cache.column(0, 32);
        assertEquals(2, cache.chunks());
        cache.column(-16, 32);
        assertEquals(2, cache.chunks());
    }

    @Test
    void theDirectCacheGivesTheSameColumns() {
        TerrainCache chunked = TerrainCache.of(generator, random, HEIGHT);
        TerrainCache direct = TerrainCache.direct(generator, random, HEIGHT);
        for (int x = -8; x <= 8; x += 4) {
            assertEquals(chunked.column(x, -x), direct.column(x, -x));
        }
        assertEquals(0, direct.chunks());
    }

    /** Checks a column against the heights and the base column of the generator. */
    private static void assertSameColumn(int x, int z, PlotTerrain.Column column) {
        PlotTerrain.Column expected = TerrainColumns.column(generator.getBaseColumn(x, z, HEIGHT, random),
                HEIGHT.getMinBuildHeight(), HEIGHT.getMaxBuildHeight());
        assertEquals(expected, column, "Column at " + x + ", " + z);
        assertEquals(generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, HEIGHT, random),
                column.surface(), "Surface at " + x + ", " + z);
        assertEquals(generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, HEIGHT, random),
                column.floor(), "Floor at " + x + ", " + z);
    }
}
