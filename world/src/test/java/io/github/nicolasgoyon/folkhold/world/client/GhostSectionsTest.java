package io.github.nicolasgoyon.folkhold.world.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class GhostSectionsTest {
    /** The positions of one cube of 16 blocks are in the same section, and the next position is in another one. */
    @Test
    void sectionIsACubeOf16Blocks() {
        long first = GhostSections.key(new BlockPos(0, 0, 0));
        assertEquals(first, GhostSections.key(new BlockPos(15, 15, 15)));
        assertNotEquals(first, GhostSections.key(new BlockPos(16, 0, 0)));
        assertNotEquals(first, GhostSections.key(new BlockPos(0, 16, 0)));
        assertNotEquals(first, GhostSections.key(new BlockPos(0, 0, 16)));
    }

    /** After a turn, the positions relative to the origin can be below 0: the section starts at -16. */
    @Test
    void negativePositionIsInTheSectionBelowZero() {
        long key = GhostSections.key(new BlockPos(-1, 3, -16));
        assertEquals(key, GhostSections.key(new BlockPos(-16, 0, -1)));
        assertEquals(new BlockPos(-16, 0, -16), GhostSections.origin(key));
    }

    /** Each section gets the indexes of its positions, in the order of the list. */
    @Test
    void groupKeepsTheIndexesOfEachSection() {
        List<BlockPos> relatives = List.of(new BlockPos(1, 0, 0), new BlockPos(20, 0, 0), new BlockPos(2, 5, 3),
                new BlockPos(-3, 0, 0));
        Long2ObjectMap<int[]> sections = GhostSections.group(relatives);
        assertEquals(3, sections.size());
        assertArrayEquals(new int[] {0, 2}, sections.get(GhostSections.key(new BlockPos(0, 0, 0))));
        assertArrayEquals(new int[] {1}, sections.get(GhostSections.key(new BlockPos(16, 0, 0))));
        assertArrayEquals(new int[] {3}, sections.get(GhostSections.key(new BlockPos(-16, 0, 0))));
    }
}
