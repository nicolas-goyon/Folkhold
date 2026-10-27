package io.github.nicolasgoyon.folkhold.core.sim.faction;

import java.util.List;
import java.util.Random;
import java.util.function.Predicate;

/**
 * The regions of the cultures: the world has a grid of square regions, and each region has one culture.
 *
 * <ul>
 *   <li>The seed of the world and the position of a region select its culture. Each culture has a weight: a large
 *       weight ({@link #AFFINITY_WEIGHT}) when the biome at the middle of the region is a biome of affinity of the
 *       culture, else a small weight ({@link #OTHER_WEIGHT}). Thus a culture is frequent in its biomes, and it can be
 *       in all the biomes.</li>
 *   <li>A region belongs to the nearest faction of its culture: the faction with the nearest territory center. A
 *       world with no faction of the culture gives the region to the nearest faction.</li>
 * </ul>
 */
public final class CultureRegions {
    /** The side of a region, in blocks. */
    public static final int REGION = 2048;
    /** The weight of a culture in a region with a biome of affinity of the culture. */
    public static final int AFFINITY_WEIGHT = 8;
    /** The weight of a culture in a region with another biome. */
    public static final int OTHER_WEIGHT = 1;
    /** Changes the random values of the regions from the other random values of the seed. */
    private static final long SALT = 0x43756C7475726573L;

    private CultureRegions() {
    }

    /**
     * The region of a block coordinate.
     *
     * @param block the X or Z coordinate of a block
     * @return the X or Z coordinate of its region
     */
    public static int region(int block) {
        return Math.floorDiv(block, REGION);
    }

    /**
     * The middle of a region: the position that selects the biome of the region.
     *
     * @param region the X or Z coordinate of a region
     * @return the X or Z coordinate of the block at the middle of the region
     */
    public static int middle(int region) {
        return region * REGION + REGION / 2;
    }

    /**
     * The culture of a region.
     *
     * @param seed     the seed of the world
     * @param regionX  the X coordinate of the region
     * @param regionZ  the Z coordinate of the region
     * @param cultures the ids of the cultures, in a fixed order (at least one)
     * @param affinity true for a culture that has an affinity with the biome at the middle of the region
     * @return the id of the culture of the region
     */
    public static String culture(long seed, int regionX, int regionZ, List<String> cultures,
            Predicate<String> affinity) {
        if (cultures.isEmpty()) {
            throw new IllegalArgumentException("At least one culture is necessary");
        }
        int[] weights = new int[cultures.size()];
        int total = 0;
        for (int i = 0; i < weights.length; i++) {
            weights[i] = affinity.test(cultures.get(i)) ? AFFINITY_WEIGHT : OTHER_WEIGHT;
            total += weights[i];
        }
        Random random = new Random(seed ^ SALT ^ (regionX * 341873128712L + regionZ * 132897987541L));
        int pick = random.nextInt(total);
        for (int i = 0; i < weights.length; i++) {
            pick -= weights[i];
            if (pick < 0) {
                return cultures.get(i);
            }
        }
        return cultures.get(cultures.size() - 1);
    }

    /**
     * The faction that owns a position in a region of a culture: the nearest faction of the culture.
     *
     * @param factions the factions (not empty)
     * @param culture  the id of the culture of the region
     * @param x        the X coordinate of the position, in blocks
     * @param z        the Z coordinate of the position, in blocks
     * @return the owner faction
     */
    public static FactionSpec owner(List<FactionSpec> factions, String culture, int x, int z) {
        List<FactionSpec> ofCulture = factions.stream().filter(faction -> faction.cultureId().equals(culture))
                .toList();
        return FactionGenerator.owner(ofCulture.isEmpty() ? factions : ofCulture, x, z);
    }
}
