package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Brain schedules of the world mod (spike #640).
 *
 * <p>{@link #WORKER}: work at the job site, meet at the market near noon, work again, then rest. The bounds are the
 * defaults of #641.
 */
public final class ModSchedules {
    private static final DeferredRegister<Schedule> SCHEDULES =
            DeferredRegister.create(Registries.SCHEDULE, FolkholdWorld.MOD_ID);

    public static final DeferredHolder<Schedule, Schedule> WORKER = SCHEDULES.register("worker",
            () -> new ScheduleBuilder(new Schedule())
                    .changeActivityAt(10, Activity.IDLE)
                    .changeActivityAt(2000, Activity.WORK)
                    .changeActivityAt(5000, Activity.MEET)
                    .changeActivityAt(8000, Activity.WORK)
                    .changeActivityAt(11000, Activity.IDLE)
                    .changeActivityAt(12000, Activity.REST)
                    .build());

    private ModSchedules() {
    }

    public static void register(IEventBus modEventBus) {
        SCHEDULES.register(modEventBus);
    }
}
