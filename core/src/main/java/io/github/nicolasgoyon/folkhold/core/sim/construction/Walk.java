package io.github.nicolasgoyon.folkhold.core.sim.construction;

/**
 * How a builder walks through a block or on it. The plan uses it to find a way from the ground to a position to stand
 * (see {@link WalkSearch}).
 */
public enum Walk {
    /**
     * The builder walks through the block, as the path finder of a villager: a block with no collision, a carpet,
     * water, or a door that a villager opens.
     */
    THROUGH,
    /**
     * The builder walks on the block. The top of the block is 1 block high or lower. Examples: a full block, a slab,
     * stairs, a bed.
     */
    ON,
    /**
     * The block stops the builder: the top of the block is higher than 1 block (a fence, a wall), or a fluid other
     * than water (lava).
     */
    BLOCKED
}
