package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.Map;

/**
 * The target of a construction: the block of each position. The construction changes only the positions of the
 * target. A position with air in the target is empty at the end of the construction.
 */
public final class Target {
    private final Map<BuildPos, BuildBlock> blocks;

    public Target(Map<BuildPos, BuildBlock> blocks) {
        this.blocks = Map.copyOf(blocks);
    }

    public Map<BuildPos, BuildBlock> blocks() {
        return blocks;
    }

    public boolean contains(BuildPos pos) {
        return blocks.containsKey(pos);
    }

    /** The block of a position of the target, or air for a position out of the target. */
    public BuildBlock at(BuildPos pos) {
        return blocks.getOrDefault(pos, BuildBlock.AIR);
    }
}
