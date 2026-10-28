package io.github.nicolasgoyon.folkhold.core.sim.village;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * The buildings that a village needs for its level, for example a forge from the level 5. The builders of the village
 * build a missing building on a free plot of its streets.
 *
 * <ul>
 *   <li>A requirement gives a kind of building and the minimum level. The same kind can be in more than one
 *       requirement: a village needs one building of this kind for each requirement of its level.</li>
 *   <li>The kind of a building is the kind of its building sign, for example {@code forge}.</li>
 *   <li>The first requirement of the list with a missing building comes first.</li>
 * </ul>
 */
public final class VillageBuildings {
    /**
     * A requirement of a level.
     *
     * @param kind     the kind of building, for example {@code forge}
     * @param minLevel the minimum level of the village for the building
     */
    public record Requirement(String kind, int minLevel) {
        public Requirement {
            Objects.requireNonNull(kind, "kind");
            if (minLevel < 1) {
                throw new IllegalArgumentException("The minimum level must be 1 or more: " + minLevel);
            }
        }
    }

    private VillageBuildings() {
    }

    /**
     * The kind of the first missing building of a village.
     *
     * @param requirements the requirements, in their order
     * @param level        the level of the village
     * @param counts       the number of buildings of each kind in the village
     * @return the kind of the missing building, or empty when the village has all the buildings of its level
     */
    public static Optional<String> missing(List<Requirement> requirements, int level, Map<String, Integer> counts) {
        Map<String, Integer> needed = new HashMap<>();
        for (Requirement requirement : requirements) {
            if (requirement.minLevel() > level) {
                continue;
            }
            int count = needed.merge(requirement.kind(), 1, Integer::sum);
            if (counts.getOrDefault(requirement.kind(), 0) < count) {
                return Optional.of(requirement.kind());
            }
        }
        return Optional.empty();
    }
}
