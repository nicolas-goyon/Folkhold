package io.github.nicolasgoyon.folkhold.core.sim;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class RegionPosTest {

    @Test
    void positiveChunksUseIntegerDivision() {
        assertEquals(new RegionPos(0, 0), RegionPos.ofChunk(0, 15, 16));
        assertEquals(new RegionPos(1, 2), RegionPos.ofChunk(16, 32, 16));
    }

    @Test
    void negativeChunksUseFloorDivision() {
        assertEquals(new RegionPos(-1, -1), RegionPos.ofChunk(-1, -16, 16));
        assertEquals(new RegionPos(-2, 0), RegionPos.ofChunk(-17, 0, 16));
    }

    @Test
    void minChunkIsTheInverseOfOfChunk() {
        RegionPos region = new RegionPos(-3, 5);
        assertEquals(region, RegionPos.ofChunk(region.minChunkX(4), region.minChunkZ(4), 4));
        assertEquals(-12, region.minChunkX(4));
        assertEquals(20, region.minChunkZ(4));
    }

    @Test
    void sizeLessThanOneIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> RegionPos.ofChunk(0, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new RegionPos(0, 0).minChunkX(-1));
    }
}
