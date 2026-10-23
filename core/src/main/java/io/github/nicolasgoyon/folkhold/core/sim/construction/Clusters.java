package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Groups of near positions. Two positions are in the same group when a chain of positions of the group links them,
 * with a gap of {@code distance} blocks or less between two positions of the chain on each axis.
 */
public final class Clusters {
    private Clusters() {
    }

    /**
     * The groups of positions, the largest group first. Groups of the same size come in the order of their first
     * position in the collection.
     *
     * @param positions the positions
     * @param distance  the largest gap between two positions of a group on each axis, 1 or more
     * @return the groups
     */
    public static List<Set<BuildPos>> of(Collection<BuildPos> positions, int distance) {
        if (distance < 1) {
            throw new IllegalArgumentException("distance must be 1 or more: " + distance);
        }
        Set<BuildPos> remaining = new LinkedHashSet<>(positions);
        List<Set<BuildPos>> groups = new ArrayList<>();
        while (!remaining.isEmpty()) {
            BuildPos first = remaining.iterator().next();
            remaining.remove(first);
            Set<BuildPos> group = new LinkedHashSet<>();
            Deque<BuildPos> open = new ArrayDeque<>();
            open.add(first);
            while (!open.isEmpty()) {
                BuildPos pos = open.poll();
                group.add(pos);
                for (int dx = -distance; dx <= distance; dx++) {
                    for (int dy = -distance; dy <= distance; dy++) {
                        for (int dz = -distance; dz <= distance; dz++) {
                            BuildPos other = pos.offset(dx, dy, dz);
                            if (remaining.remove(other)) {
                                open.add(other);
                            }
                        }
                    }
                }
            }
            groups.add(group);
        }
        groups.sort(Comparator.comparingInt((Set<BuildPos> group) -> group.size()).reversed());
        return groups;
    }
}
