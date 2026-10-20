package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.Objects;

/**
 * A problem of a target that a construction plan finds. The plan stays usable: the problem only tells where the
 * result can be different from the target.
 *
 * @param problem the problem
 * @param pos     the position of the block with the problem
 */
public record PlanWarning(Problem problem, BuildPos pos) {

    /** The problems that a plan finds. */
    public enum Problem {
        /** A falling block has air or a fluid below it at the end of the construction. */
        FALLING_BLOCK_ABOVE_AIR,
        /** A block that needs a support has air or a fluid on the side of its support at the end of the plan. */
        NO_SUPPORT,
        /** The supports of some blocks make a cycle. The plan places the lowest block of the cycle first. */
        SUPPORT_CYCLE,
        /** The builder has no position to stand in reach of the block, and no scaffolding column is possible. */
        NO_STAND
    }

    public PlanWarning {
        Objects.requireNonNull(problem, "problem");
        Objects.requireNonNull(pos, "pos");
    }

    @Override
    public String toString() {
        return problem + " at " + pos;
    }
}
