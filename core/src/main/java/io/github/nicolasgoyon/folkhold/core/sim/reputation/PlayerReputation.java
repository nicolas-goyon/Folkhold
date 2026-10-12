package io.github.nicolasgoyon.folkhold.core.sim.reputation;

import io.github.nicolasgoyon.folkhold.core.sim.faction.Attitude;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.faction.RelationState;
import io.github.nicolasgoyon.folkhold.core.sim.faction.Relations;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Reputation of one player with each faction and each village. Values from {@link #MIN} to {@link #MAX}.
 *
 * <ul>
 *   <li>The start value with a faction comes from its attitude: ally +300, neutral 0, hostile -400.</li>
 *   <li>A village without its own value uses the value of its faction.</li>
 *   <li>Effective reputation = village weight × village value + (1 − village weight) × faction value.</li>
 * </ul>
 */
public final class PlayerReputation {
    public static final int MIN = -1000;
    public static final int MAX = 1000;

    private final Map<String, Integer> factions = new HashMap<>();
    private final Map<String, Integer> villages = new HashMap<>();

    /** Start value with a faction of the given attitude. */
    public static int startValue(Attitude attitude) {
        return switch (attitude) {
            case ALLY -> 300;
            case NEUTRAL -> 0;
            case HOSTILE -> -400;
        };
    }

    private static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    /**
     * The reputation with a faction. The start value applies if the player has no value yet.
     *
     * @param faction the faction
     * @return the value
     */
    public int faction(FactionSpec faction) {
        return factions.getOrDefault(faction.id(), startValue(faction.attitude()));
    }

    /**
     * The reputation with a village. The faction value applies if the village has no value yet.
     *
     * @param faction   the faction of the village
     * @param villageId the village id
     * @return the value
     */
    public int village(FactionSpec faction, String villageId) {
        Integer value = villages.get(villageId);
        return value != null ? value : faction(faction);
    }

    /**
     * The effective reputation in a village: the value that sets the behavior of the units.
     *
     * @param faction   the faction of the village
     * @param villageId the village id, or null for the faction value only
     * @param settings  the settings
     * @return the effective value
     */
    public int effective(FactionSpec faction, String villageId, ReputationSettings settings) {
        int factionValue = faction(faction);
        if (villageId == null) {
            return factionValue;
        }
        double value = settings.villageWeight() * village(faction, villageId)
                + (1.0 - settings.villageWeight()) * factionValue;
        return clamp((int) Math.round(value));
    }

    public void setFaction(String factionId, int value) {
        factions.put(factionId, clamp(value));
    }

    public void setVillage(String villageId, int value) {
        villages.put(villageId, clamp(value));
    }

    /**
     * Applies an action of the player.
     *
     * <ol>
     *   <li>The faction value and the village value (if any) change by {@code delta}.</li>
     *   <li>Factions in alliance with the target faction change by {@code delta × allySpread}.</li>
     *   <li>Factions in tension or in war with the target faction change by {@code −delta × enemySpread}.</li>
     * </ol>
     *
     * @param target    the faction of the action
     * @param villageId the village of the action, or null
     * @param delta     the change
     * @param all       all factions of the world
     * @param relations the relations between the factions
     * @param settings  the settings
     */
    public void apply(FactionSpec target, String villageId, int delta, List<FactionSpec> all, Relations relations,
            ReputationSettings settings) {
        if (villageId != null) {
            setVillage(villageId, village(target, villageId) + delta);
        }
        setFaction(target.id(), faction(target) + delta);
        for (FactionSpec other : all) {
            if (other.id().equals(target.id())) {
                continue;
            }
            RelationState state = relations.state(target.id(), other.id());
            int change = switch (state) {
                case ALLIANCE -> (int) Math.round(delta * settings.allySpread());
                case TENSION, WAR -> (int) Math.round(-delta * settings.enemySpread());
                case PEACE -> 0;
            };
            if (change != 0) {
                setFaction(other.id(), faction(other) + change);
            }
        }
    }

    /** Stored faction values (factions without a stored value use the start value). */
    public Map<String, Integer> factionValues() {
        return Map.copyOf(factions);
    }

    /** Stored village values. */
    public Map<String, Integer> villageValues() {
        return Map.copyOf(villages);
    }
}
