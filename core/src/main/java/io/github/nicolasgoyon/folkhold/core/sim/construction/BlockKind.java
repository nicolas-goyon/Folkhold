package io.github.nicolasgoyon.folkhold.core.sim.construction;

/**
 * The kind of a block for the order of the construction (see {@link ConstructionPlanner}).
 */
public enum BlockKind {
    /** No block. */
    AIR,
    /** A block that stays in place alone. The construction places it after the block below it. */
    SOLID,
    /** A block that falls with no block below it (sand, gravel, concrete powder, anvil). */
    FALLING,
    /**
     * A block on a support (torch, lantern, ladder, button, sign, carpet, rail). The block comes after its support. A
     * block with an optional support (trapdoor, chain, flower pot) also comes after the block next to it.
     */
    ATTACHED,
    /** One part of a block of two parts (door, bed, tall plant). One step places the two parts. */
    TWO_PART,
    /** A block with a shape from its neighbors (fence, wall, glass pane, stairs, chest). */
    CONNECTED,
    /** A source of a fluid (water, lava). The construction adds the fluids at the end. */
    FLUID
}
