package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.Optional;

/**
 * One step of a construction plan.
 *
 * @param type         the type of the step
 * @param pos          the position of the block of the step
 * @param state        the state that the step puts at the position: air for a removal
 * @param partner      the position of the other part, for a block of two parts: the step places or removes the two
 *                     parts
 * @param partnerState the state of the other part
 * @param data         the data of the block entity that comes with the block
 * @param stand        the position of the feet of the builder during the step. Empty for the steps of the
 *                     scaffolding (the builder climbs the column), and for a block with no position to stand
 */
public record Step(StepType type, BuildPos pos, String state, Optional<BuildPos> partner, Optional<String> partnerState,
        Optional<String> data, Optional<BuildPos> stand) {
}
