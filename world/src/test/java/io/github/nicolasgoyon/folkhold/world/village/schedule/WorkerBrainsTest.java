package io.github.nicolasgoyon.folkhold.world.village.schedule;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import org.junit.jupiter.api.Test;

class WorkerBrainsTest {
    @Test
    void defaultScheduleGivesTheVanillaActivities() {
        Schedule schedule = WorkerBrains.toVanilla(DaySchedule.DEFAULT, 0);
        assertEquals(Activity.IDLE, schedule.getActivityAt(1000));
        assertEquals(Activity.WORK, schedule.getActivityAt(3000));
        assertEquals(Activity.MEET, schedule.getActivityAt(6500));
        assertEquals(Activity.WORK, schedule.getActivityAt(9000));
        assertEquals(Activity.IDLE, schedule.getActivityAt(11500));
        assertEquals(Activity.REST, schedule.getActivityAt(13000));
    }

    @Test
    void vanillaScheduleMatchesThePhasesOfEachStep() {
        assertMatches(DaySchedule.DEFAULT);
        // An empty morning at home, and the sleep of the last step after the end of the day.
        assertMatches(new DaySchedule(0, 5000, 8000, 11000, 23900, 7, 2, 300));
        // Empty work phases and an empty evening at home.
        assertMatches(new DaySchedule(2000, 2000, 3000, 3000, 22500, 7, 2, 1999));
    }

    /** For each step and each tick of the day, the vanilla schedule gives the activity of the phase of the worker. */
    private static void assertMatches(DaySchedule schedule) {
        for (int step = 0; step < DaySchedule.WORKER_STEPS; step++) {
            Schedule vanilla = WorkerBrains.toVanilla(schedule, step);
            int delay = schedule.stepDelay(step);
            for (int time = 0; time < DaySchedule.DAY_TICKS; time++) {
                assertEquals(WorkerBrains.activity(schedule.phaseAt(time, delay)), vanilla.getActivityAt(time),
                        "step " + step + " at " + time);
            }
        }
    }
}
