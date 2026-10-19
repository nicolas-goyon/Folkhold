package io.github.nicolasgoyon.folkhold.core.sim.garrison;

import java.util.Map;

/**
 * The garrison of a settlement at one level: the number of units of each role, and the equipment set of the units.
 *
 * @param units     the number of units of each role, by role id (for example {@code guard}). A role that is not in
 *                  the map has no unit.
 * @param equipment the id of the equipment set of the units (for example {@code hamlet})
 */
public record GarrisonTraits(Map<String, Integer> units, String equipment) {
    public GarrisonTraits {
        units = Map.copyOf(units);
        for (Map.Entry<String, Integer> entry : units.entrySet()) {
            if (entry.getKey().isBlank() || entry.getValue() < 0) {
                throw new IllegalArgumentException("Invalid garrison count: " + entry);
            }
        }
        if (equipment.isBlank()) {
            throw new IllegalArgumentException("A garrison needs an equipment set");
        }
    }

    /**
     * The number of units of a role.
     *
     * @param role the role id
     * @return the number of units, 0 for a role that is not in the map
     */
    public int count(String role) {
        return units.getOrDefault(role, 0);
    }
}
