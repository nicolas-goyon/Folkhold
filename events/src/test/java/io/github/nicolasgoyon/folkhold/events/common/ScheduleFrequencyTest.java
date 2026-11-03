package io.github.nicolasgoyon.folkhold.events.common;

import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.events.config.EventsConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.junit.jupiter.api.Test;

/**
 * The frequency of the natural events. The test runs the logic of {@code EventSchedule.isDue} over many game days,
 * with the default config: the schedule examines the time each 20 ticks, as the earthquakes and the storms do. The
 * test prints the measured intervals.
 */
class ScheduleFrequencyTest {
    /** The interval of the examination of the schedule, in ticks (see the earthquakes and the storms). */
    private static final int SCHEDULE_INTERVAL = 20;

    /**
     * The intervals between the starts of the natural events, in game days, over a number of game days.
     *
     * @param days the number of simulated game days
     * @param seed the seed of the random numbers
     * @return the intervals
     */
    private static List<Double> simulate(int days, long seed) {
        Random random = new Random(seed);
        long nextTime = -1L;
        long lastStart = -1L;
        List<Double> intervals = new ArrayList<>();
        for (long now = 0L; now <= days * EventMath.TICKS_PER_DAY; now += SCHEDULE_INTERVAL) {
            boolean due = EventMath.isDue(nextTime, now);
            if (nextTime < 0L || due) {
                nextTime = now + EventMath.nextDelayTicks(random.nextDouble(), EventsConfig.DEFAULT_MIN_DAYS,
                        EventsConfig.DEFAULT_MAX_DAYS);
            }
            if (due) {
                if (lastStart >= 0L) {
                    intervals.add((now - lastStart) / (double) EventMath.TICKS_PER_DAY);
                }
                lastStart = now;
            }
        }
        return intervals;
    }

    private static void check(String type, long seed) {
        List<Double> intervals = simulate(1000, seed);
        double mean = intervals.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
        double min = intervals.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
        double max = intervals.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
        System.out.println(String.format(Locale.ROOT,
                "%s: %d events in 1000 game days, interval mean %.2f days, min %.2f days, max %.2f days", type,
                intervals.size() + 1, mean, min, max));
        assertTrue(intervals.size() >= 30, type + ": too few events: " + intervals.size());
        assertTrue(mean >= EventsConfig.DEFAULT_MIN_DAYS && mean <= EventsConfig.DEFAULT_MAX_DAYS,
                type + ": mean interval " + mean);
        // One examination of the schedule (20 ticks) can add a delay to an interval.
        double step = SCHEDULE_INTERVAL / (double) EventMath.TICKS_PER_DAY;
        assertTrue(min >= EventsConfig.DEFAULT_MIN_DAYS, type + ": shortest interval " + min);
        assertTrue(max <= EventsConfig.DEFAULT_MAX_DAYS + step, type + ": longest interval " + max);
    }

    @Test
    void naturalEarthquakesOccurEvery15To30GameDays() {
        check("Earthquakes", 130L);
    }

    @Test
    void naturalStormsOccurEvery15To30GameDays() {
        check("Storms", 129L);
    }
}
