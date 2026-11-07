package io.github.nicolasgoyon.folkhold.core.sim.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
}
