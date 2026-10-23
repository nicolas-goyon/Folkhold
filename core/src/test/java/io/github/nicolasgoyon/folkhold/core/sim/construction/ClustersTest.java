package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ClustersTest {
    /** Positions with a gap of the distance or less on each axis make one group. */
    @Test
    void nearPositionsMakeOneGroup() {
        List<BuildPos> positions = List.of(new BuildPos(0, 0, 0), new BuildPos(3, 0, 0), new BuildPos(3, 3, 3));

        List<Set<BuildPos>> groups = Clusters.of(positions, 3);

        assertEquals(1, groups.size(), "A gap of 3 blocks must keep one group: " + groups);
        assertEquals(Set.copyOf(positions), groups.get(0));
    }

    /** A chain links far positions: the first and the last positions are in the same group. */
    @Test
    void chainLinksFarPositions() {
        List<BuildPos> chain = List.of(new BuildPos(0, 0, 0), new BuildPos(2, 0, 0), new BuildPos(4, 0, 0),
                new BuildPos(6, 0, 0));

        assertEquals(1, Clusters.of(chain, 2).size(), "A chain of gaps of 2 blocks must make one group");
    }

    /** A gap larger than the distance on one axis makes two groups, the largest group first. */
    @Test
    void largeGapMakesTwoGroupsTheLargestFirst() {
        List<BuildPos> positions = List.of(new BuildPos(0, 0, 0), new BuildPos(10, 0, 0), new BuildPos(10, 1, 0),
                new BuildPos(11, 1, 0));

        List<Set<BuildPos>> groups = Clusters.of(positions, 3);

        assertEquals(2, groups.size(), "A gap of 10 blocks must make two groups: " + groups);
        assertEquals(Set.of(new BuildPos(10, 0, 0), new BuildPos(10, 1, 0), new BuildPos(11, 1, 0)), groups.get(0));
        assertEquals(Set.of(new BuildPos(0, 0, 0)), groups.get(1));
    }

    /** No positions give no groups, and the distance must be 1 or more. */
    @Test
    void noPositionsAndBadDistance() {
        assertTrue(Clusters.of(List.of(), 3).isEmpty(), "No positions must give no groups");
        assertThrows(IllegalArgumentException.class, () -> Clusters.of(List.of(new BuildPos(0, 0, 0)), 0));
    }
}
