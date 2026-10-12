package io.github.nicolasgoyon.folkhold.core.sim.faction;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * Makes the factions of a world from the world seed. The same seed and the same cultures give the same factions.
 *
 * <p>Rules:
 * <ul>
 *   <li>{@link Settings#minFactions()} to {@link Settings#maxFactions()} factions.</li>
 *   <li>1 or 2 factions are allies of the players, 1 is neutral, the other factions are hostile.</li>
 *   <li>The territory centers are on a ring around the world spawn, at regular angles with a random offset.
 *       The territory of a faction is the area nearer to its center than to the other centers.</li>
 *   <li>Each faction gets one or two traits.</li>
 * </ul>
 */
public final class FactionGenerator {

    /**
     * Settings of the generator.
     *
     * @param minFactions minimum number of factions (at least 3: ally, neutral, hostile)
     * @param maxFactions maximum number of factions
     * @param minRadius   minimum distance in blocks from the spawn to a territory center
     * @param maxRadius   maximum distance in blocks from the spawn to a territory center
     */
    public record Settings(int minFactions, int maxFactions, int minRadius, int maxRadius) {
        public static final Settings DEFAULT = new Settings(5, 7, 600, 2500);

        public Settings {
            if (minFactions < 3 || maxFactions < minFactions) {
                throw new IllegalArgumentException("Need 3 <= minFactions <= maxFactions");
            }
            if (minRadius < 0 || maxRadius < minRadius) {
                throw new IllegalArgumentException("Need 0 <= minRadius <= maxRadius");
            }
        }
    }

    private FactionGenerator() {
    }

    /**
     * Makes the factions.
     *
     * @param seed     the world seed
     * @param cultures the available cultures (at least one)
     * @param spawnX   X coordinate of the world spawn
     * @param spawnZ   Z coordinate of the world spawn
     * @param settings the settings
     * @return the factions, in id order
     */
    public static List<FactionSpec> generate(long seed, List<CultureInfo> cultures, int spawnX, int spawnZ,
            Settings settings) {
        if (cultures.isEmpty()) {
            throw new IllegalArgumentException("At least one culture is necessary");
        }
        Random random = new Random(seed ^ 0x466F6C6B686F6C64L);
        int count = settings.minFactions() + random.nextInt(settings.maxFactions() - settings.minFactions() + 1);

        List<Attitude> attitudes = new ArrayList<>();
        int allies = 1 + random.nextInt(2);
        for (int i = 0; i < count; i++) {
            attitudes.add(i < allies ? Attitude.ALLY : i == allies ? Attitude.NEUTRAL : Attitude.HOSTILE);
        }
        Collections.shuffle(attitudes, random);

        double startAngle = random.nextDouble() * Math.PI * 2.0;
        Set<String> usedNames = new HashSet<>();
        List<FactionSpec> factions = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            CultureInfo culture = cultures.get(i % cultures.size());
            double angle = startAngle + (Math.PI * 2.0 * i / count) + (random.nextDouble() - 0.5) * (Math.PI / count);
            double radius = settings.minRadius() + random.nextDouble() * (settings.maxRadius() - settings.minRadius());
            int centerX = spawnX + (int) Math.round(Math.cos(angle) * radius);
            int centerZ = spawnZ + (int) Math.round(Math.sin(angle) * radius);
            String name = uniqueName(culture, random, usedNames);
            factions.add(new FactionSpec("faction_" + i, culture.id(), name, culture.color(), attitudes.get(i),
                    traits(random), centerX, centerZ));
        }
        return factions;
    }

    private static String uniqueName(CultureInfo culture, Random random, Set<String> used) {
        for (int attempt = 0; attempt < 50; attempt++) {
            String name = capitalize(pick(culture.prefixes(), random) + pick(culture.suffixes(), random));
            if (used.add(name)) {
                return name;
            }
        }
        String name = capitalize(pick(culture.prefixes(), random) + pick(culture.suffixes(), random)) + " " + used.size();
        used.add(name);
        return name;
    }

    private static Set<FactionTrait> traits(Random random) {
        List<FactionTrait> all = new ArrayList<>(List.of(FactionTrait.values()));
        Collections.shuffle(all, random);
        int count = 1 + random.nextInt(2);
        Set<FactionTrait> traits = EnumSet.noneOf(FactionTrait.class);
        for (int i = 0; i < count; i++) {
            traits.add(all.get(i));
        }
        // Merchant and isolationist do not go together.
        if (traits.contains(FactionTrait.MERCHANT) && traits.contains(FactionTrait.ISOLATIONIST)) {
            traits.remove(FactionTrait.ISOLATIONIST);
        }
        return traits;
    }

    private static String pick(List<String> values, Random random) {
        return values.get(random.nextInt(values.size()));
    }

    private static String capitalize(String text) {
        return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    /**
     * Finds the faction that owns a position: the faction with the nearest territory center.
     *
     * @param factions the factions (not empty)
     * @param x        X coordinate in blocks
     * @param z        Z coordinate in blocks
     * @return the owner faction
     */
    public static FactionSpec owner(List<FactionSpec> factions, int x, int z) {
        FactionSpec best = null;
        long bestSqr = Long.MAX_VALUE;
        for (FactionSpec faction : factions) {
            long dx = (long) faction.centerX() - x;
            long dz = (long) faction.centerZ() - z;
            long sqr = dx * dx + dz * dz;
            if (sqr < bestSqr) {
                best = faction;
                bestSqr = sqr;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("No factions");
        }
        return best;
    }
}
