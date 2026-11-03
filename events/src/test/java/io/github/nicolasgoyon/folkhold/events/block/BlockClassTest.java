package io.github.nicolasgoyon.folkhold.events.block;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockClassTest {

    @Test
    void blastResistanceGivesTheClassOfABlockWithNoTag() {
        assertEquals(BlockClass.FRAGILE, BlockClass.ofBlastResistance(0.5, 3.0, 6.0));
        assertEquals(BlockClass.FRAGILE, BlockClass.ofBlastResistance(2.0, 3.0, 6.0));
        assertEquals(BlockClass.STURDY, BlockClass.ofBlastResistance(3.0, 3.0, 6.0));
        assertEquals(BlockClass.STURDY, BlockClass.ofBlastResistance(4.2, 3.0, 6.0));
        assertEquals(BlockClass.RESISTANT, BlockClass.ofBlastResistance(6.0, 3.0, 6.0));
        assertEquals(BlockClass.RESISTANT, BlockClass.ofBlastResistance(1200.0, 3.0, 6.0));
    }
}
