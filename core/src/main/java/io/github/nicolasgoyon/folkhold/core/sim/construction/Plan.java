package io.github.nicolasgoyon.folkhold.core.sim.construction;

import java.util.List;

/**
 * A construction plan: the ordered steps, and the problems of the target.
 *
 * @param steps    the steps, in their order
 * @param warnings the problems of the target (for example a falling block above air)
 */
public record Plan(List<Step> steps, List<PlanWarning> warnings) {
    public Plan {
        steps = List.copyOf(steps);
        warnings = List.copyOf(warnings);
    }

    /** The number of steps of a type. */
    public long count(StepType type) {
        return steps.stream().filter(step -> step.type() == type).count();
    }
}
