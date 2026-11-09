package io.github.nicolasgoyon.folkhold.core.sim.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class DayScheduleTest {
    private static final DaySchedule SCHEDULE = DaySchedule.DEFAULT;
    private static final long DAY = DaySchedule.DAY_TICKS;

    @Test
    void phaseAtEachBound() {
        assertEquals(DayPhase.HOME, SCHEDULE.phaseAt(0));
        assertEquals(DayPhase.HOME, SCHEDULE.phaseAt(1999));
        assertEquals(DayPhase.WORK, SCHEDULE.phaseAt(2000));
        assertEquals(DayPhase.WORK, SCHEDULE.phaseAt(4999));
        assertEquals(DayPhase.MARKET, SCHEDULE.phaseAt(5000));
        assertEquals(DayPhase.MARKET, SCHEDULE.phaseAt(7999));
        assertEquals(DayPhase.WORK, SCHEDULE.phaseAt(8000));
        assertEquals(DayPhase.WORK, SCHEDULE.phaseAt(10999));
        assertEquals(DayPhase.HOME, SCHEDULE.phaseAt(11000));
        assertEquals(DayPhase.HOME, SCHEDULE.phaseAt(11999));
        assertEquals(DayPhase.SLEEP, SCHEDULE.phaseAt(12000));
        assertEquals(DayPhase.SLEEP, SCHEDULE.phaseAt(23999));
    }

    @Test
    void phaseWrapsAtTheEndOfTheDay() {
        assertEquals(DayPhase.SLEEP, SCHEDULE.phaseAt(DAY - 1));
        assertEquals(DayPhase.HOME, SCHEDULE.phaseAt(DAY));
        assertEquals(DayPhase.MARKET, SCHEDULE.phaseAt(5 * DAY + 6500));
    }

    @Test
    void longGameTimes() {
        long dayTime = 9_000_000_000_000L * DAY + 6500;
        assertEquals(9_000_000_000_000L, DaySchedule.day(dayTime));
        assertEquals(6500, DaySchedule.timeOfDay(dayTime));
        assertEquals(DayPhase.MARKET, SCHEDULE.phaseAt(dayTime));
        assertEquals(DaySchedule.timeOfDay(Long.MAX_VALUE), (int) (Long.MAX_VALUE % DAY));
    }

    @Test
    void negativeDayTimeStaysInTheDay() {
        assertEquals(DAY - 1, DaySchedule.timeOfDay(-1));
        assertEquals(-1, DaySchedule.day(-1));
        assertEquals(DayPhase.SLEEP, SCHEDULE.phaseAt(-1));
    }

    @Test
    void timeSetGivesThePhaseOfTheNewTime() {
        // The schedule keeps no state: a jump of the day time, back or forward, gives the phase of the new time.
        long late = 40 * DAY + 13000;
        assertEquals(DayPhase.SLEEP, SCHEDULE.phaseAt(late));
        assertEquals(DayPhase.WORK, SCHEDULE.phaseAt(3000));
        assertEquals(0, DaySchedule.day(3000));
    }

    @Test
    void workerDelayMovesThePhaseChanges() {
        int delay = 300;
        assertEquals(DayPhase.WORK, SCHEDULE.phaseAt(5000 + delay - 1, delay));
        assertEquals(DayPhase.MARKET, SCHEDULE.phaseAt(5000 + delay, delay));
        assertEquals(DayPhase.SLEEP, SCHEDULE.phaseAt(DAY + delay - 1, delay));
        assertEquals(DayPhase.HOME, SCHEDULE.phaseAt(DAY + delay, delay));
    }

    @Test
    void workerDelayIsInTheSpread() {
        for (long seed = -500; seed < 500; seed++) {
            int delay = SCHEDULE.workerDelay(seed);
            assertTrue(delay >= 0 && delay <= SCHEDULE.workerSpreadTicks(), "delay " + delay);
        }
        DaySchedule noSpread = new DaySchedule(2000, 5000, 8000, 11000, 12000, 7, 2, 0);
        assertEquals(0, noSpread.workerDelay(123456789L));
    }

    @Test
    void workerDelayHasFewSteps() {
        assertEquals(0, SCHEDULE.stepDelay(0));
        assertEquals(133, SCHEDULE.stepDelay(1));
        assertEquals(266, SCHEDULE.stepDelay(2));
        assertEquals(400, SCHEDULE.stepDelay(3));
        assertThrows(IllegalArgumentException.class, () -> SCHEDULE.stepDelay(DaySchedule.WORKER_STEPS));
        Set<Integer> delays = new HashSet<>();
        for (long seed = -500; seed < 500; seed++) {
            delays.add(SCHEDULE.workerDelay(seed));
        }
        assertEquals(Set.of(0, 133, 266, 400), delays);
    }

    @Test
    void boundsOfAStepAreThePhaseChanges() {
        for (int step = 0; step < DaySchedule.WORKER_STEPS; step++) {
            int delay = SCHEDULE.stepDelay(step);
            DaySchedule.Bounds bounds = SCHEDULE.bounds(step);
            assertChange(bounds.homeStartMorning(), delay, DayPhase.SLEEP, DayPhase.HOME);
            assertChange(bounds.workStart(), delay, DayPhase.HOME, DayPhase.WORK);
            assertChange(bounds.marketStart(), delay, DayPhase.WORK, DayPhase.MARKET);
            assertChange(bounds.marketEnd(), delay, DayPhase.MARKET, DayPhase.WORK);
            assertChange(bounds.homeStart(), delay, DayPhase.WORK, DayPhase.HOME);
            assertChange(bounds.sleepStart(), delay, DayPhase.HOME, DayPhase.SLEEP);
        }
    }

    @Test
    void boundsWrapAtTheEndOfTheDay() {
        DaySchedule late = new DaySchedule(2000, 5000, 8000, 11000, 23900, 7, 2, 300);
        DaySchedule.Bounds bounds = late.bounds(3);
        assertEquals(200, bounds.sleepStart());
        assertEquals(300, bounds.homeStartMorning());
        assertEquals(DayPhase.SLEEP, late.phaseAt(DAY + 250, 300));
    }

    private static void assertChange(int bound, int delay, DayPhase before, DayPhase after) {
        assertEquals(before, SCHEDULE.phaseAt(DAY + bound - 1, delay), "before " + bound);
        assertEquals(after, SCHEDULE.phaseAt(DAY + bound, delay), "at " + bound);
    }

    @Test
    void clockOfTheDayTime() {
        assertEquals("06:00", DaySchedule.clock(0));
        assertEquals("11:00", DaySchedule.clock(5000));
        assertEquals("12:30", DaySchedule.clock(6500));
        assertEquals("00:00", DaySchedule.clock(18000));
        assertEquals("05:59", DaySchedule.clock(23999));
        assertEquals("08:00", DaySchedule.clock(3 * DAY + 2000));
    }

    @Test
    void marketDaysOfDay0And7And14() {
        assertTrue(SCHEDULE.isMarketDay(0, 0));
        assertTrue(SCHEDULE.isMarketDay(7, 0));
        assertTrue(SCHEDULE.isMarketDay(14, 0));
        assertFalse(SCHEDULE.isMarketDay(1, 0));
        assertFalse(SCHEDULE.isMarketDay(6, 0));
        assertFalse(SCHEDULE.isMarketDay(13, 0));
        assertEquals(2, SCHEDULE.extraOffers(14, 0));
        assertEquals(0, SCHEDULE.extraOffers(15, 0));
    }

    @Test
    void shiftMovesTheMarketDay() {
        assertTrue(SCHEDULE.isMarketDay(3, 3));
        assertTrue(SCHEDULE.isMarketDay(10, 3));
        assertFalse(SCHEDULE.isMarketDay(0, 3));
        for (long seed = -50; seed < 50; seed++) {
            int shift = SCHEDULE.marketDayShift(seed);
            assertTrue(shift >= 0 && shift < 7, "shift " + shift);
        }
    }

    @Test
    void daysToTheNextMarketDay() {
        assertEquals(0, SCHEDULE.daysToMarketDay(7, 0));
        assertEquals(6, SCHEDULE.daysToMarketDay(8, 0));
        assertEquals(1, SCHEDULE.daysToMarketDay(13, 0));
        assertEquals(2, SCHEDULE.daysToMarketDay(1, 3));
        long farDay = 7 * 1_000_000_000_000L + 3;
        assertEquals(0, SCHEDULE.daysToMarketDay(farDay, 3));
        assertEquals(6, SCHEDULE.daysToMarketDay(farDay + 1, 3));
    }

    @Test
    void periodOfOneMakesEachDayAMarketDay() {
        DaySchedule daily = new DaySchedule(2000, 5000, 8000, 11000, 12000, 1, 2, 400);
        assertTrue(daily.isMarketDay(0, daily.marketDayShift(42)));
        assertTrue(daily.isMarketDay(5, daily.marketDayShift(42)));
        assertEquals(0, daily.daysToMarketDay(5, 0));
    }

    @Test
    void invalidConfigIsRefused() {
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(-1, 5000, 8000, 11000, 12000, 7, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 5000, 8000, 11000, 24000, 7, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(6000, 5000, 8000, 11000, 12000, 7, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 8000, 8000, 11000, 12000, 7, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 5000, 8000, 7000, 12000, 7, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 5000, 8000, 11000, 10000, 7, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 5000, 8000, 11000, 12000, 0, 2, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 5000, 8000, 11000, 12000, 7, -1, 400));
        assertThrows(IllegalArgumentException.class, () -> new DaySchedule(2000, 5000, 8000, 11000, 12000, 7, 2, -1));
    }

    @Test
    void emptyWorkAndHomePhasesAreValid() {
        DaySchedule marketAllDay = new DaySchedule(0, 0, 12000, 12000, 12000, 7, 0, 0);
        assertEquals(DayPhase.MARKET, marketAllDay.phaseAt(0));
        assertEquals(DayPhase.SLEEP, marketAllDay.phaseAt(12000));
    }

    @Test
    void transitionsOfTheDefaultSchedule() {
        assertEquals(List.of(new DaySchedule.Transition(0, DayPhase.HOME), new DaySchedule.Transition(2000,
                DayPhase.WORK), new DaySchedule.Transition(5000, DayPhase.MARKET), new DaySchedule.Transition(8000,
                DayPhase.WORK), new DaySchedule.Transition(11000, DayPhase.HOME), new DaySchedule.Transition(12000,
                DayPhase.SLEEP)), SCHEDULE.transitions(0));
    }

    @Test
    void transitionsKeepTheLastPhaseOfAnEmptyPhase() {
        DaySchedule marketAllDay = new DaySchedule(0, 0, 12000, 12000, 12000, 7, 0, 0);
        assertEquals(List.of(new DaySchedule.Transition(0, DayPhase.MARKET),
                new DaySchedule.Transition(12000, DayPhase.SLEEP)), marketAllDay.transitions(0));
        assertTransitionsMatchThePhases(marketAllDay);
    }

    @Test
    void transitionsAreSortedAfterTheWrap() {
        DaySchedule late = new DaySchedule(2000, 5000, 8000, 11000, 23900, 7, 2, 300);
        List<DaySchedule.Transition> changes = late.transitions(3);
        assertEquals(new DaySchedule.Transition(200, DayPhase.SLEEP), changes.get(0));
        assertEquals(new DaySchedule.Transition(300, DayPhase.HOME), changes.get(1));
        assertTransitionsMatchThePhases(late);
    }

    @Test
    void transitionsMatchThePhasesOfEachStep() {
        assertTransitionsMatchThePhases(SCHEDULE);
        assertTransitionsMatchThePhases(new DaySchedule(0, 5000, 8000, 23999, 23999, 7, 2, 2000));
        assertTransitionsMatchThePhases(new DaySchedule(2000, 2000, 3000, 3000, 22500, 7, 2, 1999));
    }

    /**
     * For each step and each tick of the day, the phase of the last transition at or before the tick (or the last
     * transition of the list before the first one) is the phase of the worker.
     */
    private static void assertTransitionsMatchThePhases(DaySchedule schedule) {
        for (int step = 0; step < DaySchedule.WORKER_STEPS; step++) {
            List<DaySchedule.Transition> changes = schedule.transitions(step);
            for (int i = 1; i < changes.size(); i++) {
                assertTrue(changes.get(i - 1).time() < changes.get(i).time(), "sorted with no tie: " + changes);
            }
            int delay = schedule.stepDelay(step);
            for (int time = 0; time < DaySchedule.DAY_TICKS; time++) {
                DayPhase phase = changes.get(changes.size() - 1).phase();
                for (DaySchedule.Transition change : changes) {
                    if (change.time() <= time) {
                        phase = change.phase();
                    }
                }
                assertEquals(schedule.phaseAt(time, delay), phase, "step " + step + " at " + time);
            }
        }
    }
}
