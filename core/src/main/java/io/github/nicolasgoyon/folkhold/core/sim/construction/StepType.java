package io.github.nicolasgoyon.folkhold.core.sim.construction;

/** The type of a step of a construction plan. */
public enum StepType {
    /** Removes a block that is not in the target (an obstacle), or a block with a wrong state. */
    REMOVE,
    /** Places a block of the target, with its complete state. */
    PLACE,
    /** Places one block of a scaffolding column. */
    PLACE_SCAFFOLDING,
    /** Removes one block of a scaffolding column. */
    REMOVE_SCAFFOLDING,
    /** Places a block with its fluid: a fluid source, or a waterlogged block. */
    ADD_FLUID
}
