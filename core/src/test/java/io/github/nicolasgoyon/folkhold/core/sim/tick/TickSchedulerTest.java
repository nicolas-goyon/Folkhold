package io.github.nicolasgoyon.folkhold.core.sim.tick;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.Settings;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.StepResult;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TickSchedulerTest {
    private static final Settings SETTINGS = new Settings(100, 3, 10);

    /** A clock that moves forward by a fixed step at each read. */
    private static final class FakeClock {
        private long now;
        private final long step;

        FakeClock(long step) {
            this.step = step;
        }

        long read() {
            now += step;
            return now;
        }
    }

    @Test
    void firstAbstractTickStartsOneIntervalAfterTheFirstStep() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        List<String> seen = new ArrayList<>();
        FakeClock clock = new FakeClock(0);

        assertEquals(0, scheduler.step(1000, () -> List.of("a"), (item, tick) -> seen.add(item), clock::read,
                SETTINGS).processed());
        assertEquals(0, scheduler.step(1099, () -> List.of("a"), (item, tick) -> seen.add(item), clock::read,
                SETTINGS).processed());
        StepResult result = scheduler.step(1100, () -> List.of("a", "b"), (item, tick) -> seen.add(item),
                clock::read, SETTINGS);

        assertEquals(List.of("a", "b"), seen);
        assertEquals(new TickInfo(1, false), result.finished());
        assertEquals(1200, scheduler.nextTickAt());
    }

    @Test
    void eachThirdAbstractTickIsACycle() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        List<TickInfo> ticks = new ArrayList<>();
        FakeClock clock = new FakeClock(0);
        scheduler.step(0, List::of, (item, tick) -> { }, clock::read, SETTINGS);
        for (long time = 100; time <= 600; time += 100) {
            StepResult result = scheduler.step(time, () -> List.of("x"), (item, tick) -> { }, clock::read, SETTINGS);
            ticks.add(result.finished());
        }

        assertEquals(List.of(false, false, true, false, false, true), ticks.stream().map(TickInfo::cycle).toList());
        assertEquals(6, scheduler.tickNumber());
    }

    @Test
    void budgetSplitsTheAbstractTickOverSeveralGameTicks() {
        TickScheduler<Integer> scheduler = new TickScheduler<>();
        List<Integer> seen = new ArrayList<>();
        // Each clock read moves 4 ns. The budget is 10 ns: 3 items for each game tick.
        FakeClock clock = new FakeClock(4);
        scheduler.step(0, List::of, (item, tick) -> { }, clock::read, SETTINGS);

        StepResult first = scheduler.step(100, () -> List.of(1, 2, 3, 4, 5, 6, 7), (item, tick) -> seen.add(item),
                clock::read, SETTINGS);
        assertTrue(first.processed() > 0 && first.processed() < 7);
        assertNull(first.finished());
        assertTrue(scheduler.isRunning());

        StepResult last = first;
        int steps = 1;
        while (scheduler.isRunning()) {
            last = scheduler.step(100 + steps, List::of, (item, tick) -> seen.add(item), clock::read, SETTINGS);
            steps++;
        }

        assertEquals(List.of(1, 2, 3, 4, 5, 6, 7), seen);
        assertNotNull(last.finished());
        assertEquals(steps, last.tickSteps());
        assertTrue(last.tickNanos() > SETTINGS.budgetNanos());
    }

    @Test
    void failedItemDoesNotStopTheAbstractTick() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        List<String> seen = new ArrayList<>();
        FakeClock clock = new FakeClock(1);
        TickScheduler.Processor<String> processor = (item, tick) -> {
            if (item.equals("bad")) {
                throw new IllegalStateException("broken item");
            }
            seen.add(item);
        };
        scheduler.step(0, List::of, processor, clock::read, SETTINGS);

        assertThrows(IllegalStateException.class,
                () -> scheduler.step(100, () -> List.of("a", "bad", "b"), processor, clock::read, SETTINGS));
        assertTrue(scheduler.isRunning(), "The abstract tick continues at the next step");
        StepResult result = scheduler.step(101, List::of, processor, clock::read, SETTINGS);

        assertEquals(List.of("a", "b"), seen, "The failed item is not processed again");
        assertNotNull(result.finished());
        assertEquals(2, result.tickSteps(), "The step with the failure counts");
        assertTrue(result.tickNanos() > 0);
    }

    @Test
    void oneItemRunsEvenIfItIsSlowerThanTheBudget() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        FakeClock clock = new FakeClock(1000);
        scheduler.step(0, List::of, (item, tick) -> { }, clock::read, SETTINGS);

        StepResult result = scheduler.step(100, () -> List.of("slow", "next"), (item, tick) -> { }, clock::read,
                SETTINGS);

        assertEquals(1, result.processed());
    }

    @Test
    void noCatchUpAfterALongPause() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        List<TickInfo> ticks = new ArrayList<>();
        FakeClock clock = new FakeClock(0);
        scheduler.step(0, List::of, (item, tick) -> { }, clock::read, SETTINGS);

        scheduler.step(10_000, () -> List.of("x"), (item, tick) -> ticks.add(tick), clock::read, SETTINGS);
        scheduler.step(10_001, () -> List.of("x"), (item, tick) -> ticks.add(tick), clock::read, SETTINGS);

        assertEquals(1, ticks.size());
        assertEquals(10_100, scheduler.nextTickAt());
    }

    @Test
    void restoreKeepsTheTickNumberAndDropsTheUnfinishedTick() {
        TickScheduler<Integer> scheduler = new TickScheduler<>();
        FakeClock clock = new FakeClock(20);
        scheduler.step(0, List::of, (item, tick) -> { }, clock::read, SETTINGS);
        scheduler.step(100, () -> List.of(1, 2, 3), (item, tick) -> { }, clock::read, SETTINGS);
        assertTrue(scheduler.isRunning());

        scheduler.restore(41, 500);

        assertFalse(scheduler.isRunning());
        assertEquals(41, scheduler.tickNumber());
        assertEquals(500, scheduler.nextTickAt());
        StepResult result = scheduler.step(500, () -> List.of(9), (item, tick) -> { }, clock::read, SETTINGS);
        assertEquals(new TickInfo(42, true), result.finished());
    }

    @Test
    void savedNextTickTooFarInTheFutureIsMovedToOneInterval() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        scheduler.restore(0, 1_000_000);
        FakeClock clock = new FakeClock(0);

        scheduler.step(50, List::of, (item, tick) -> { }, clock::read, SETTINGS);

        assertEquals(150, scheduler.nextTickAt());
    }

    @Test
    void runNowStartsTheNextTickAtTheNextStep() {
        TickScheduler<String> scheduler = new TickScheduler<>();
        FakeClock clock = new FakeClock(0);
        scheduler.step(0, List::of, (item, tick) -> { }, clock::read, SETTINGS);

        scheduler.runNow(10);
        StepResult result = scheduler.step(10, () -> List.of("x"), (item, tick) -> { }, clock::read, SETTINGS);

        assertNotNull(result.finished());
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> new Settings(0, 10, 1));
        assertThrows(IllegalArgumentException.class, () -> new Settings(10, 0, 1));
        assertThrows(IllegalArgumentException.class, () -> new Settings(10, 10, 0));
    }
}
