package io.github.nicolasgoyon.folkhold.world.faction;

import io.github.nicolasgoyon.folkhold.core.sim.faction.CultureRegions;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;

/**
 * The cultures of the regions of a world (see {@link CultureRegions}). The biome at the middle of a region selects the
 * weights of the cultures: a culture has a large weight in the biomes of its tag of affinity
 * ({@link Culture#affinity()}).
 *
 * <p>The map reads the biomes from the biome source of the chunk generator: it does not need the chunks. Thus the
 * generation of the structures and the discovery of the settlements can use it. The map keeps the culture of each
 * region that it calculates.
 */
public final class CultureMap {
    /** The biome of a region is the biome at this height. */
    private static final int BIOME_Y = 64;

    private final long seed;
    private final BiomeSource biomes;
    private final Climate.Sampler sampler;
    private final List<String> ids = Cultures.ALL.stream().map(Culture::id).toList();
    private final Map<Long, String> regions = new ConcurrentHashMap<>();

    /**
     * Makes a map.
     *
     * @param seed    the seed of the world
     * @param biomes  the biome source of the chunk generator
     * @param sampler the climate sampler of the random state of the world
     */
    public CultureMap(long seed, BiomeSource biomes, Climate.Sampler sampler) {
        this.seed = seed;
        this.biomes = biomes;
        this.sampler = sampler;
    }

    /**
     * The map of a level.
     *
     * @param level the level
     * @return the map of the cultures of the level
     */
    public static CultureMap of(ServerLevel level) {
        return new CultureMap(level.getSeed(), level.getChunkSource().getGenerator().getBiomeSource(),
                level.getChunkSource().randomState().sampler());
    }

    /**
     * The culture of the region of a position.
     *
     * @param x the X coordinate of the position, in blocks
     * @param z the Z coordinate of the position, in blocks
     * @return the id of the culture
     */
    public String cultureAt(int x, int z) {
        int regionX = CultureRegions.region(x);
        int regionZ = CultureRegions.region(z);
        return regions.computeIfAbsent(((long) regionX << 32) ^ (regionZ & 0xFFFFFFFFL), key -> {
            Holder<Biome> biome = biomes.getNoiseBiome(QuartPos.fromBlock(CultureRegions.middle(regionX)),
                    QuartPos.fromBlock(BIOME_Y), QuartPos.fromBlock(CultureRegions.middle(regionZ)), sampler);
            return CultureRegions.culture(seed, regionX, regionZ, ids,
                    id -> Cultures.byId(id).map(culture -> biome.is(culture.affinity())).orElse(false));
        });
    }
}
