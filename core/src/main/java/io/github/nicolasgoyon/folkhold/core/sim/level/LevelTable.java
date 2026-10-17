package io.github.nicolasgoyon.folkhold.core.sim.level;

import java.util.ArrayList;
import java.util.List;
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
        int weight = 0;
        for (StartChance chance : startChances) {
            if (chance.maxLevel() > levels.size()) {
                throw new IllegalArgumentException("Start chance above the last level: " + chance);
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

    private static LevelTable defaultTable() {
        int[] claimRadius = {9, 9, 9, 9, 13, 16, 16, 18, 20, 22, 24, 26, 27, 27, 27, 27, 30, 31, 33, 33};
        int[] cats = {1, 1, 1, 1, 2, 2, 2, 2, 3, 3, 3, 3, 4, 4, 4, 4, 6, 6, 6, 6};
        List<LevelTraits> levels = new ArrayList<>();
        for (int level = 1; level <= 20; level++) {
            levels.add(new LevelTraits(10 + 5 * level, claimRadius[level - 1], 4 + 5 * level, 1 + level,
                    128 * level, 2 + level, cats[level - 1]));
        }
        List<Tier> tiers = List.of(new Tier("hamlet", 1, 4), new Tier("village", 5, 8), new Tier("town", 9, 12),
                new Tier("city", 13, 16), new Tier("capital", 17, 20));
        List<StartChance> chances = List.of(new StartChance(1, 1, 40), new StartChance(2, 4, 30),
                new StartChance(5, 8, 20), new StartChance(9, 12, 8), new StartChance(13, 16, 2));
        return new LevelTable(levels, tiers, chances);
    }
}
