package io.github.nicolasgoyon.folkhold.core.sim.tick;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Runs the abstract ticks of the simulation at a fixed interval of game time, with a time budget.
 *
 * <p>An abstract tick processes each item (for example each settlement) once. When the time of one game tick is
 * more than the budget, the scheduler stops and continues with the remaining items at the next game tick.
 * Each {@code cycleLength}-th abstract tick is a cycle (one game day with the default values).
 *
 * <p>The scheduler counts the game time. When the server stops, the simulation stops. There is no catch-up:
 * after a long pause, the next abstract tick starts one interval after the current game time.
 *
 * <p>An exception from the processor goes to the caller of {@link #step}. The scheduler stays consistent: the
 * item that failed is not processed again, the time of the step counts, and the abstract tick continues with the
 * remaining items at the next step.
 *
 * @param <T> the type of the items
 */
public final class TickScheduler<T> {
    private final Deque<T> pending = new ArrayDeque<>();
    private long tickNumber;
    private long nextTickAt = -1;
    private TickInfo current;
    private long currentNanos;
    private int currentSteps;

    /**
     * Settings of the scheduler.
     *
     * @param intervalTicks game ticks between two abstract ticks (1 or more)
     * @param cycleLength   abstract ticks in one cycle (1 or more)
     * @param budgetNanos   maximum time for each game tick, in nanoseconds (1 or more)
     */
    public record Settings(int intervalTicks, int cycleLength, long budgetNanos) {
        /** 2,400 game ticks (2 minutes), 10 abstract ticks for each cycle, 5 ms. */
        public static final Settings DEFAULT = new Settings(2400, 10, 5_000_000L);

        public Settings {
            if (intervalTicks < 1 || cycleLength < 1 || budgetNanos < 1) {
                throw new IllegalArgumentException("Scheduler settings must be 1 or more: " + intervalTicks + ", "
                        + cycleLength + ", " + budgetNanos);
            }
        }
    }

    /**
     * Information about the current abstract tick.
     *
     * @param number the number of the abstract tick (1 for the first one)
     * @param cycle  true if this abstract tick is also a cycle
     */
    public record TickInfo(long number, boolean cycle) {
    }

    /**
     * Result of one step (one game tick).
     *
     * @param processed number of items processed in this step
     * @param finished  the abstract tick finished in this step, or null if no abstract tick finished
     * @param tickNanos total time of the finished abstract tick, in nanoseconds (0 if none finished)
     * @param tickSteps number of game ticks of the finished abstract tick (0 if none finished)
     */
    public record StepResult(int processed, TickInfo finished, long tickNanos, int tickSteps) {
        static final StepResult IDLE = new StepResult(0, null, 0, 0);
    }

    /** Processes one item in an abstract tick. */
    @FunctionalInterface
    public interface Processor<T> {
        void process(T item, TickInfo tick);
    }

    /**
     * Runs one game tick of the scheduler.
     *
     * @param gameTime  the current game time
     * @param items     gives the items of a new abstract tick
     * @param processor processes one item
     * @param nanoClock gives the current time in nanoseconds (for example {@link System#nanoTime()})
     * @param settings  the settings
     * @return the result of the step
     */
    public StepResult step(long gameTime, Supplier<? extends Collection<? extends T>> items, Processor<T> processor,
            LongSupplier nanoClock, Settings settings) {
        if (current == null) {
            if (nextTickAt < 0 || nextTickAt > gameTime + settings.intervalTicks()) {
                nextTickAt = gameTime + settings.intervalTicks();
            }
            if (gameTime < nextTickAt) {
                return StepResult.IDLE;
            }
            tickNumber++;
            current = new TickInfo(tickNumber, tickNumber % settings.cycleLength() == 0);
            pending.addAll(items.get());
            nextTickAt = gameTime + settings.intervalTicks();
            currentNanos = 0;
            currentSteps = 0;
        }
        long start = nanoClock.getAsLong();
        int processed = 0;
        try {
            while (!pending.isEmpty()) {
                if (processed > 0 && nanoClock.getAsLong() - start >= settings.budgetNanos()) {
                    break;
                }
                processor.process(pending.poll(), current);
                processed++;
            }
        } finally {
            currentNanos += nanoClock.getAsLong() - start;
            currentSteps++;
        }
        if (!pending.isEmpty()) {
            return new StepResult(processed, null, 0, 0);
        }
        TickInfo finished = current;
        current = null;
        return new StepResult(processed, finished, currentNanos, currentSteps);
    }

    /** Number of the last abstract tick that started (0 before the first one). */
    public long tickNumber() {
        return tickNumber;
    }

    /** Game time of the next abstract tick, or -1 before the first step. */
    public long nextTickAt() {
        return nextTickAt;
    }

    /** True if an abstract tick started and did not finish. */
    public boolean isRunning() {
        return current != null;
    }

    /**
     * Sets the saved state. An abstract tick that did not finish before the save does not continue.
     *
     * @param tickNumber the number of the last abstract tick
     * @param nextTickAt the game time of the next abstract tick, or -1
     */
    public void restore(long tickNumber, long nextTickAt) {
        this.tickNumber = tickNumber;
        this.nextTickAt = nextTickAt;
        pending.clear();
        current = null;
    }
}
