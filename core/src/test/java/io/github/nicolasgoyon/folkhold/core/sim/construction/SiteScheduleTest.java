package io.github.nicolasgoyon.folkhold.core.sim.construction;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.OptionalInt;
import org.junit.jupiter.api.Test;

class SiteScheduleTest {
    /** Each builder claims the first free step, and keeps its claim until the step applies. */
    @Test
    void buildersClaimTheFirstFreeSteps() {
        SiteSchedule<String> schedule = new SiteSchedule<>(4, true);

        assertEquals(OptionalInt.of(0), schedule.claim("a"));
        assertEquals(OptionalInt.of(1), schedule.claim("b"));
        assertEquals(OptionalInt.of(0), schedule.claim("a"), "A builder keeps its claim");
        assertEquals(Optional.of("b"), schedule.owner(1));
        schedule.markApplied(0);
        assertEquals(OptionalInt.empty(), schedule.claimOf("a"), "The claim ends when the step applies");
        assertEquals(OptionalInt.of(2), schedule.claim("a"));
        assertEquals(1, schedule.next());
    }

    /** In an ordered list, a step applies only after the steps before it. */
    @Test
    void orderedStepsApplyInTheirOrder() {
        SiteSchedule<String> schedule = new SiteSchedule<>(3, true);
        schedule.claim("a");
        schedule.claim("b");

        assertTrue(schedule.canApply(0));
        assertFalse(schedule.canApply(1), "Step 1 waits for step 0");
        schedule.markApplied(0);
        assertTrue(schedule.canApply(1));
        assertFalse(schedule.canApply(0), "An applied step does not apply again");
    }

    /** In a list with no order, each step applies when its builder is ready. */
    @Test
    void unorderedStepsApplyWhenReady() {
        SiteSchedule<String> schedule = new SiteSchedule<>(3, false);

        assertTrue(schedule.canApply(2));
        schedule.markApplied(2);
        assertEquals(0, schedule.next(), "The next step is the first step that is not applied");
        assertEquals(1, schedule.appliedCount());
        schedule.markApplied(0);
        schedule.markApplied(1);
        assertTrue(schedule.finished());
        assertEquals(3, schedule.next());
        assertEquals(OptionalInt.empty(), schedule.claim("a"), "No step is free at the end");
    }

    /**
     * In a list with no order, a builder claims the free step with the lowest cost. In an ordered list, the cost does
     * not change the first free step.
     */
    @Test
    void unorderedClaimTakesTheStepWithTheLowestCost() {
        SiteSchedule<String> unordered = new SiteSchedule<>(5, false);
        SiteSchedule<String> ordered = new SiteSchedule<>(5, true);

        assertEquals(OptionalInt.of(3), unordered.claim("a", index -> Math.abs(index - 3)));
        assertEquals(OptionalInt.of(2), unordered.claim("b", index -> Math.abs(index - 3)),
                "The first step with the lowest cost and no owner");
        assertEquals(OptionalInt.of(3), unordered.claim("a", index -> 0), "A builder keeps its claim");
        assertEquals(OptionalInt.of(0), ordered.claim("a", index -> Math.abs(index - 3)));
    }

    /** A released step goes to the next builder that claims a step. */
    @Test
    void releasedStepGoesToAnotherBuilder() {
        SiteSchedule<String> schedule = new SiteSchedule<>(3, true);
        schedule.claim("a");
        schedule.claim("b");
        schedule.release("a");

        assertEquals(OptionalInt.of(0), schedule.claim("c"));
        assertEquals(Optional.empty(), schedule.owner(2));
    }

    /** A builder takes the next step: the owner of the next step gets the step of this builder. */
    @Test
    void takeNextExchangesTheClaims() {
        SiteSchedule<String> schedule = new SiteSchedule<>(4, true);
        schedule.claim("a");
        schedule.claim("b");

        assertEquals(OptionalInt.of(0), schedule.takeNext("b"));
        assertEquals(OptionalInt.of(1), schedule.claimOf("a"), "The owner of the next step gets the other step");
        assertEquals(Optional.of("b"), schedule.owner(0));
        assertEquals(Optional.of("a"), schedule.owner(1));

        assertEquals(OptionalInt.of(0), schedule.takeNext("c"));
        assertEquals(OptionalInt.empty(), schedule.claimOf("b"), "With no step to give, the owner has no step");
        assertEquals(OptionalInt.of(2), schedule.claim("b"));
    }

    @Test
    void indexMustBeAStep() {
        SiteSchedule<String> schedule = new SiteSchedule<>(2, true);

        assertFalse(schedule.canApply(-1));
        assertFalse(schedule.canApply(2));
        assertThrows(IndexOutOfBoundsException.class, () -> schedule.markApplied(2));
        assertThrows(IllegalArgumentException.class, () -> new SiteSchedule<String>(-1, true));
    }
}
