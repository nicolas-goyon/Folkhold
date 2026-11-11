package io.github.nicolasgoyon.folkhold.core.sim.village;

import io.github.nicolasgoyon.folkhold.core.sim.village.VillageSize.Wall;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * The size of the village structure of each tier. A data file gives the values. {@link #DEFAULT} has the same
 * values as the default data file.
 *
 * <p>Each tier has one size. The tiers come from the level table: a table must have a size for each tier of the
 * level table (see {@link #requireTiers}).
 */
public final class VillageSizeTable {
    public static final VillageSizeTable DEFAULT = new VillageSizeTable(List.of(
            new VillageSize("hamlet", 14, 0, 0.15, 64, 0, Wall.PALISADE, 9),
            new VillageSize("village", 28, 0, 0.15, 80, 0, Wall.PALISADE, 13),
            new VillageSize("town", 42, 0, 0.15, 96, 1, Wall.PALISADE, 20),
            new VillageSize("city", 53, 0, 0.15, 128, 2, Wall.PALISADE, 27),
            new VillageSize("capital", 66, 0, 0.15, 160, 3, Wall.PALISADE, 30)));

    private final Map<String, VillageSize> sizes;

    /**
     * Makes a table.
     *
     * @param sizes the size of each tier
     * @throws IllegalArgumentException if the list is empty or if two sizes have the same tier
     */
    public VillageSizeTable(List<VillageSize> sizes) {
        if (sizes.isEmpty()) {
            throw new IllegalArgumentException("A village size table needs one tier or more");
        }
        Map<String, VillageSize> byTier = new LinkedHashMap<>();
        for (VillageSize size : sizes) {
            if (byTier.put(size.tier(), size) != null) {
                throw new IllegalArgumentException("The village size table has the tier " + size.tier() + " twice");
            }
        }
        this.sizes = byTier;
    }

    /** The sizes, in the order of the data file. */
    public Collection<VillageSize> sizes() {
        return Collections.unmodifiableCollection(sizes.values());
    }

    /** The size of a tier, if the table has the tier. */
    public Optional<VillageSize> size(String tier) {
        return Optional.ofNullable(sizes.get(tier));
    }

    /**
     * Checks that the table has a size for each tier.
     *
     * @param tiers the ids of the tiers of the level table
     * @return this table
     * @throws IllegalArgumentException if a tier has no size. The message gives the missing tiers.
     */
    public VillageSizeTable requireTiers(Collection<String> tiers) {
        Set<String> missing = new HashSet<>(tiers);
        missing.removeAll(sizes.keySet());
        if (!missing.isEmpty()) {
            throw new IllegalArgumentException("The village size table has no size for the tiers "
                    + missing.stream().sorted().toList());
        }
        return this;
    }
}
