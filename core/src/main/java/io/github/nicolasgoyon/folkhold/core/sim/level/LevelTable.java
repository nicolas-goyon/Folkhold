package io.github.nicolasgoyon.folkhold.core.sim.level;

import io.github.nicolasgoyon.folkhold.core.sim.garrison.GarrisonTraits;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.SplittableRandom;

/**
 * Levels, tiers and start levels of the settlements. A data file gives the values. {@link #DEFAULT} has the
 * same values as the default data file.
 *
 * <p>Rules:
 * <ul>
 *   <li>The levels go from 1 to {@link #maxLevel()}. Each level has its {@link LevelTraits}.</li>
 *   <li>The tiers cover all levels, with no gap and no overlap.</li>
 *   <li>The start level is a random level from the start chances. The same seed gives the same level.</li>
 *   <li>With 2 tiers or more, no start chance is in the last tier (the capital): only growth or a command gives
 *       these levels.</li>
 * </ul>
 */
public final class LevelTable {
    /**
     * A chance to start at a level in a range.
     *
     * @param minLevel the first level of the range
     * @param maxLevel the last level of the range
     * @param weight   the weight of the range (relative to the other ranges)
     */
    public record StartChance(int minLevel, int maxLevel, int weight) {
        public StartChance {
            if (minLevel < 1 || maxLevel < minLevel || weight < 1) {
                throw new IllegalArgumentException("Invalid start chance: " + minLevel + "-" + maxLevel + " " + weight);
            }
        }
    }

    /** The salt of the seed of a start level (see {@link #startSeed}). */
    private static final long START_SALT = 0x6F6C_6B68_6F6C_6431L;

    public static final LevelTable DEFAULT = defaultTable();

    private final List<LevelTraits> levels;
    private final List<Tier> tiers;
    private final List<StartChance> startChances;
    private final int totalWeight;

    /**
     * Makes a table.
     *
     * @param levels       the traits of each level, from level 1
     * @param tiers        the tiers, in the order of the levels
     * @param startChances the start chances
     * @throws IllegalArgumentException if the tiers do not cover all levels exactly, or if a start chance is
     *                                  outside the levels
     */
    public LevelTable(List<LevelTraits> levels, List<Tier> tiers, List<StartChance> startChances) {
        if (levels.isEmpty() || tiers.isEmpty() || startChances.isEmpty()) {
            throw new IllegalArgumentException("A level table needs levels, tiers and start chances");
        }
        int next = 1;
        for (Tier tier : tiers) {
            if (tier.minLevel() != next) {
                throw new IllegalArgumentException("The tier " + tier.id() + " must start at the level " + next);
            }
            next = tier.maxLevel() + 1;
        }
        if (next != levels.size() + 1) {
            throw new IllegalArgumentException("The tiers must end at the level " + levels.size());
        }
        Tier last = tiers.get(tiers.size() - 1);
        int weight = 0;
        for (StartChance chance : startChances) {
            if (chance.maxLevel() > levels.size()) {
                throw new IllegalArgumentException("Start chance above the last level: " + chance);
            }
            if (tiers.size() > 1 && chance.maxLevel() >= last.minLevel()) {
                throw new IllegalArgumentException("Start chance in the last tier " + last.id()
                        + ": only growth gives the levels " + last.minLevel() + " to " + last.maxLevel() + ": "
                        + chance);
            }
            weight += chance.weight();
        }
        this.levels = List.copyOf(levels);
        this.tiers = List.copyOf(tiers);
        this.startChances = List.copyOf(startChances);
        this.totalWeight = weight;
    }

    public int maxLevel() {
        return levels.size();
    }

    public List<Tier> tiers() {
        return tiers;
    }

    /** Limits a level to the range 1 to {@link #maxLevel()}. */
    public int clamp(int level) {
        return Math.max(1, Math.min(maxLevel(), level));
    }

    /** The traits of a level. A level outside the table uses the nearest level. */
    public LevelTraits traits(int level) {
        return levels.get(clamp(level) - 1);
    }

    /** The tier of a level. A level outside the table uses the nearest level. */
    public Tier tier(int level) {
        int clamped = clamp(level);
        for (Tier tier : tiers) {
            if (tier.contains(clamped)) {
                return tier;
            }
        }
        throw new IllegalStateException("No tier for the level " + clamped);
    }

    /**
     * A random start level.
     *
     * @param seed the seed, for example from the id of the settlement
     * @return the start level
     */
    public int startLevel(long seed) {
        SplittableRandom random = new SplittableRandom(seed);
        int roll = random.nextInt(totalWeight);
        for (StartChance chance : startChances) {
            if (roll < chance.weight()) {
                return chance.minLevel() + random.nextInt(chance.maxLevel() - chance.minLevel() + 1);
            }
            roll -= chance.weight();
        }
        throw new IllegalStateException("No start chance for the roll");
    }

    /**
     * The start level of a village structure. The same world seed and the same start chunk give the same level, thus
     * the structure and the settlement get the same level, also before the settlement exists.
     *
     * @param worldSeed the seed of the world
     * @param chunkX    the chunk X of the structure start
     * @param chunkZ    the chunk Z of the structure start
     * @return the start level
     */
    public int startLevel(long worldSeed, int chunkX, int chunkZ) {
        return startLevel(startSeed(worldSeed, chunkX, chunkZ));
    }

    /**
     * The seed of the start level of a village structure, from the world seed and the start chunk.
     *
     * @param worldSeed the seed of the world
     * @param chunkX    the chunk X of the structure start
     * @param chunkZ    the chunk Z of the structure start
     * @return the seed
     */
    public static long startSeed(long worldSeed, int chunkX, int chunkZ) {
        // Two large odd numbers separate the chunks. The salt separates this seed from the other seeds of the chunk.
        return worldSeed ^ START_SALT ^ (chunkX * 341_873_128_712L + chunkZ * 132_897_987_541L);
    }

    private static LevelTable defaultTable() {
        int[] claimRadius = {9, 9, 9, 9, 13, 16, 16, 18, 20, 22, 24, 26, 27, 27, 27, 27, 30, 31, 33, 33};
        int[] cats = {1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 6, 6, 6, 6};
        List<Tier> tiers = List.of(new Tier("hamlet", 1, 4), new Tier("village", 5, 8), new Tier("town", 9, 12),
                new Tier("city", 13, 16), new Tier("capital", 17, 20));
        // The garrison of each tier: guards, archers, captains, healers, golems and riders. The balance of the raids
        // (see "Raids against the faction villages" in docs/design.md) gives a village the equipment set of a town, and
        // a town the equipment set of a city. The other tiers have the equipment set with the id of the tier.
        int[][] garrisons = {{2, 2, 1, 0, 0, 0}, {5, 3, 1, 1, 0, 0}, {5, 4, 1, 1, 1, 1}, {6, 5, 1, 2, 2, 2},
                {6, 5, 1, 2, 2, 3}};
        String[] equipment = {"hamlet", "town", "city", "city", "capital"};
        List<LevelTraits> levels = new ArrayList<>();
        for (int level = 1; level <= 20; level++) {
            int tier = (level - 1) / 4;
            int[] units = garrisons[tier];
            GarrisonTraits garrison = new GarrisonTraits(Map.of("guard", units[0], "archer", units[1],
                    "captain", units[2], "healer", units[3], "golem", units[4], "rider", units[5]),
                    equipment[tier]);
            levels.add(new LevelTraits(10 + 5 * level, claimRadius[level - 1], 4 + 5 * level, 1 + level,
                    128 * level, 2 + level, cats[level - 1], garrison));
        }
        List<StartChance> chances = List.of(new StartChance(1, 1, 40), new StartChance(2, 4, 30),
                new StartChance(5, 8, 20), new StartChance(9, 12, 8), new StartChance(13, 16, 2));
        return new LevelTable(levels, tiers, chances);
    }
}
