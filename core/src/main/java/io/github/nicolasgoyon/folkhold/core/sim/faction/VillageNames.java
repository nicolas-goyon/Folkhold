package io.github.nicolasgoyon.folkhold.core.sim.faction;

import java.util.Random;

/**
 * Names of the villages. A village name is one prefix and one suffix of the culture of its faction. The same seed
 * gives the same name.
 */
public final class VillageNames {

    private VillageNames() {
    }

    /**
     * The name of a village.
     *
     * @param culture the culture of the faction of the village
     * @param seed    the seed of the village, for example from the village id
     * @return the name, with a capital first letter
     */
    public static String name(CultureInfo culture, long seed) {
        return name(culture, new Random(seed));
    }

    /**
     * The next name from a random source: one prefix and one suffix of the culture.
     *
     * @param culture the culture
     * @param random  the random source
     * @return the name, with a capital first letter
     */
    public static String name(CultureInfo culture, Random random) {
        String prefix = culture.prefixes().get(random.nextInt(culture.prefixes().size()));
        String suffix = culture.suffixes().get(random.nextInt(culture.suffixes().size()));
        String name = prefix + suffix;
        return name.isEmpty() ? name : Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }
}
