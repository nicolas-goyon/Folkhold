package io.github.nicolasgoyon.folkhold.core.sim.faction;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Relation values between factions, from -100 to +100. A relation is symmetric: the value of (a, b) is the
 * value of (b, a). The relation of a faction with itself is not stored.
 */
public final class Relations {
    public static final int MIN = -100;
    public static final int MAX = 100;

    private final Map<String, Integer> values = new HashMap<>();

    /**
     * Makes the start relations from the attitudes of the factions to the players.
     * <ul>
     *   <li>Two factions with the same attitude: +60 (allies of the players), +20 (hostile factions).</li>
     *   <li>An ally of the players and a hostile faction: -40.</li>
     *   <li>All other pairs: 0.</li>
     * </ul>
     *
     * @param factions the factions
     * @return the start relations
     */
    public static Relations initial(List<FactionSpec> factions) {
        Relations relations = new Relations();
        for (int i = 0; i < factions.size(); i++) {
            for (int j = i + 1; j < factions.size(); j++) {
                FactionSpec a = factions.get(i);
                FactionSpec b = factions.get(j);
                relations.set(a.id(), b.id(), initialValue(a.attitude(), b.attitude()));
            }
        }
        return relations;
    }

    static int initialValue(Attitude a, Attitude b) {
        if (a == b) {
            return switch (a) {
                case ALLY -> 60;
                case HOSTILE -> 20;
                case NEUTRAL -> 0;
            };
        }
        boolean allyAndHostile = (a == Attitude.ALLY && b == Attitude.HOSTILE)
                || (a == Attitude.HOSTILE && b == Attitude.ALLY);
        return allyAndHostile ? -40 : 0;
    }

    private static String key(String a, String b) {
        return a.compareTo(b) <= 0 ? a + "|" + b : b + "|" + a;
    }

    /**
     * The relation value between two factions. 0 if the pair has no value. {@link #MAX} for a faction with itself.
     */
    public int get(String a, String b) {
        if (a.equals(b)) {
            return MAX;
        }
        return values.getOrDefault(key(a, b), 0);
    }

    public RelationState state(String a, String b) {
        return RelationState.of(get(a, b));
    }

    /** Sets the value, clamped to {@link #MIN}..{@link #MAX}. */
    public void set(String a, String b, int value) {
        if (a.equals(b)) {
            return;
        }
        values.put(key(a, b), Math.max(MIN, Math.min(MAX, value)));
    }

    /** Adds a change to the value, clamped to {@link #MIN}..{@link #MAX}. */
    public void change(String a, String b, int delta) {
        set(a, b, get(a, b) + delta);
    }

    /** All stored values. The keys have the form {@code a|b} with a before b in alphabetical order. */
    public Map<String, Integer> asMap() {
        return Map.copyOf(values);
    }

    /** Puts a stored value back, for example from the save data. */
    public void putRaw(String key, int value) {
        values.put(key, Math.max(MIN, Math.min(MAX, value)));
    }
}
