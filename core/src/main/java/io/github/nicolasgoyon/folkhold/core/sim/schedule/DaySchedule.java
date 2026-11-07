package io.github.nicolasgoyon.folkhold.core.sim.schedule;

/**
 * The daily schedule of the workers of a village, and the market day. The values are times of the day in ticks
 * (0 to 23999, 0 is the sunrise), as the day time of a Minecraft world.
 *
 * <p>The phases of one day:
 * <ul>
 *   <li>from 0 to {@code workStart}: {@link DayPhase#HOME};</li>
 *   <li>from {@code workStart} to {@code marketStart}: {@link DayPhase#WORK};</li>
 *   <li>from {@code marketStart} to {@code marketEnd}: {@link DayPhase#MARKET};</li>
 *   <li>from {@code marketEnd} to {@code homeStart}: {@link DayPhase#WORK};</li>
 *   <li>from {@code homeStart} to {@code sleepStart}: {@link DayPhase#HOME};</li>
 *   <li>from {@code sleepStart} to the end of the day: {@link DayPhase#SLEEP}.</li>
 * </ul>
 * A start bound is in its phase. An end bound is in the next phase.
 *
 * <p>The day number is the day time divided by {@link #DAY_TICKS}. A day is a market day of a village when its
 * number minus the shift of the village is a multiple of {@code marketDayPeriod}. With a shift of 0, the days 0,
 * {@code marketDayPeriod}, 2 × {@code marketDayPeriod}… are market days.
 *
 * @param workStart          the start of the work in the morning
 * @param marketStart        the start of the market
 * @param marketEnd          the end of the market, and the start of the work in the afternoon
 * @param homeStart          the end of the work, and the start of the time at home
 * @param sleepStart         the start of the sleep
 * @param marketDayPeriod    the number of days from one market day to the next, 1 or more. 1: each day is a market
 *                           day
 * @param marketDayOffers    the number of offers that each worker adds on a market day, 0 or more
 * @param workerSpreadTicks  the maximum delay of the phase changes of one worker, 0 or more. Each worker gets a
 *                           delay from 0 to this value, so that the workers of a village do not all move in the
 *                           same tick
 */
public record DaySchedule(int workStart, int marketStart, int marketEnd, int homeStart, int sleepStart,
        int marketDayPeriod, int marketDayOffers, int workerSpreadTicks) {
    /** The length of one Minecraft day in ticks. */
    public static final int DAY_TICKS = 24000;

    /** The default schedule: work from 2000, market from 5000 to 8000, work to 11000, home, sleep from 12000. */
    public static final DaySchedule DEFAULT = new DaySchedule(2000, 5000, 8000, 11000, 12000, 7, 2, 400);

    /**
     * Makes a schedule.
     *
     * @throws IllegalArgumentException if a bound is not in the day, if the bounds are not in the order of the
     *                                  phases, if the market has no time, or if a market day value is not valid
     */
    public DaySchedule {
        if (workStart < 0 || sleepStart >= DAY_TICKS) {
            throw new IllegalArgumentException("The bounds must be from 0 to " + (DAY_TICKS - 1));
        }
        if (workStart > marketStart || marketStart >= marketEnd || marketEnd > homeStart || homeStart > sleepStart) {
            throw new IllegalArgumentException("The bounds must be in this order: work " + workStart + " <= market "
                    + marketStart + " < market end " + marketEnd + " <= home " + homeStart + " <= sleep "
                    + sleepStart);
        }
        if (marketDayPeriod < 1 || marketDayOffers < 0 || workerSpreadTicks < 0) {
            throw new IllegalArgumentException("Invalid market day: period " + marketDayPeriod + ", offers "
                    + marketDayOffers + ", worker spread " + workerSpreadTicks);
        }
    }

    /**
     * The time of the day of a day time: 0 to 23999. The day time of a world can be larger than one day, and a
     * command can set it to any value.
     *
     * @param dayTime the day time of the world
     * @return the time in the day
     */
    public static int timeOfDay(long dayTime) {
        return (int) Math.floorMod(dayTime, (long) DAY_TICKS);
    }

    /**
     * The day number of a day time. Day 0 starts at the day time 0.
     *
     * @param dayTime the day time of the world
     * @return the day number
     */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, (long) DAY_TICKS);
    }

    /**
     * The phase at a day time, for the whole village.
     *
     * @param dayTime the day time of the world
     * @return the phase
     */
    public DayPhase phaseAt(long dayTime) {
        int time = timeOfDay(dayTime);
        if (time < workStart) {
            return DayPhase.HOME;
        }
        if (time < marketStart) {
            return DayPhase.WORK;
        }
        if (time < marketEnd) {
            return DayPhase.MARKET;
        }
        if (time < homeStart) {
            return DayPhase.WORK;
        }
        return time < sleepStart ? DayPhase.HOME : DayPhase.SLEEP;
    }

    /**
     * The phase of one worker at a day time. The phase changes of the worker occur {@code delay} ticks after the
     * phase changes of the village.
     *
     * @param dayTime the day time of the world
     * @param delay   the delay of the worker, from {@link #workerDelay(long)}
     * @return the phase of the worker
     */
    public DayPhase phaseAt(long dayTime, int delay) {
        return phaseAt(dayTime - delay);
    }

    /**
     * The delay of the phase changes of one worker: a value from 0 to {@code workerSpreadTicks} that comes from a
     * stable value of the worker, for example the bits of its UUID.
     *
     * @param seed a stable value of the worker
     * @return the delay in ticks
     */
    public int workerDelay(long seed) {
        return (int) Math.floorMod(mix(seed), (long) workerSpreadTicks + 1);
    }

    /**
     * The shift of the market days of a village: a value from 0 to {@code marketDayPeriod - 1} that comes from a
     * stable value of the village, for example the chunk of its structure start. 0 for all the villages gives one
     * market day for the whole world.
     *
     * @param seed a stable value of the village
     * @return the shift in days
     */
    public int marketDayShift(long seed) {
        return (int) Math.floorMod(mix(seed), (long) marketDayPeriod);
    }

    /**
     * Tells if a day is a market day.
     *
     * @param day   the day number
     * @param shift the shift of the village, from {@link #marketDayShift(long)}
     * @return true on a market day
     */
    public boolean isMarketDay(long day, int shift) {
        return Math.floorMod(day - shift, (long) marketDayPeriod) == 0;
    }

    /**
     * The number of days from a day to the next market day.
     *
     * @param day   the day number
     * @param shift the shift of the village, from {@link #marketDayShift(long)}
     * @return 0 on a market day, else 1 to {@code marketDayPeriod - 1}
     */
    public int daysToMarketDay(long day, int shift) {
        return (int) Math.floorMod(shift - day, (long) marketDayPeriod);
    }

    /**
     * The number of offers that each worker adds on a day.
     *
     * @param day   the day number
     * @param shift the shift of the village, from {@link #marketDayShift(long)}
     * @return {@code marketDayOffers} on a market day, else 0
     */
    public int extraOffers(long day, int shift) {
        return isMarketDay(day, shift) ? marketDayOffers : 0;
    }

    /** Mixes the bits of a seed, so that near seeds give values that are not near (the finalizer of SplitMix64). */
    private static long mix(long seed) {
        long z = seed;
        z = (z ^ (z >>> 30)) * 0xbf58476d1ce4e5b9L;
        z = (z ^ (z >>> 27)) * 0x94d049bb133111ebL;
        return z ^ (z >>> 31);
    }
}
