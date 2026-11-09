package io.github.nicolasgoyon.folkhold.world.village.schedule;

import io.github.nicolasgoyon.folkhold.core.sim.schedule.DayPhase;
import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import io.github.nicolasgoyon.folkhold.world.registry.ModPoiTypes;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;

/**
 * The brain of a worker of a faction village. The vanilla villager brain does the walks, the work, the meeting and
 * the sleep. Folkhold gives the brain a schedule and two memories (see {@link VillageWorkers}).
 *
 * <ul>
 *   <li>A worker is an adult villager of a faction village with the profession of a job. The inhabitants with no job,
 *       the builders and the handymen keep the vanilla schedule.</li>
 *   <li>The schedule of a worker comes from {@link VillageSchedule#schedule()}: {@link DayPhase#WORK} is the activity
 *       {@code WORK}, {@link DayPhase#MARKET} is {@code MEET}, {@link DayPhase#HOME} is {@code IDLE} and
 *       {@link DayPhase#SLEEP} is {@code REST}. The UUID of the worker gives its delay step, so the workers of a
 *       village share at most {@link DaySchedule#WORKER_STEPS} schedules.</li>
 *   <li>The vanilla villager sets its default schedule each time that it builds its brain goals: at the creation, at
 *       the load and at each refresh of the brain (for example after a change of profession). A mixin calls
 *       {@link #onBrainGoals} after each build, so the worker keeps its schedule.</li>
 *   <li>The vanilla activities with a higher priority stop the schedule: a panic, a raid and the sound of a bell
 *       ({@code HIDE}). The heart rings the bells of the village in an alert, and the workers hear them (see
 *       {@link VillageWorkers#hearAlarm}).</li>
 *   <li>A villager that is not a worker has no job sign as its job site, and a worker has only the job sign of its
 *       home. The build of the brain goals and each check of the heart erase an other job sign (see
 *       {@link #forgetJobSign}). Thus a villager that loses its job or its post does not walk to the old sign.</li>
 * </ul>
 */
public final class WorkerBrains {
    /** The vanilla activities that have priority over the schedule. */
    private static final List<Activity> PRIORITY_ACTIVITIES = List.of(Activity.HIDE, Activity.PANIC, Activity.RAID,
            Activity.PRE_RAID);
    private static volatile Schedules schedules;

    private WorkerBrains() {
    }

    /**
     * True if a villager follows the worker schedule: an adult villager of a faction village with the profession of a
     * job.
     *
     * @param villager the villager
     * @return true for a worker
     */
    public static boolean isWorker(Villager villager) {
        if (villager.isBaby() || !VillageFolk.isVillageFolk(villager)) {
            return false;
        }
        VillagerProfession profession = villager.getVillagerData().getProfession();
        return profession != VillagerProfession.NONE && profession != VillagerProfession.NITWIT
                && profession != ModProfessions.INHABITANT.get() && profession != ModProfessions.MERCHANT.get()
                && profession != ModProfessions.BUILDER.get() && profession != ModProfessions.HANDYMAN.get();
    }

    /**
     * The brain schedule of a worker: the schedule of the delay step of the worker.
     *
     * @param villager the worker
     * @return the schedule
     */
    public static Schedule scheduleOf(Villager villager) {
        DaySchedule schedule = VillageSchedule.schedule();
        Schedules current = schedules;
        if (current == null || !current.source().equals(schedule)) {
            current = Schedules.of(schedule);
            schedules = current;
        }
        UUID id = villager.getUUID();
        return current.steps().get(schedule.workerStep(id.getMostSignificantBits() ^ id.getLeastSignificantBits()));
    }

    /**
     * Makes a vanilla brain schedule from the phase changes of a delay step (see {@link DaySchedule#transitions}).
     *
     * @param schedule the daily schedule
     * @param step     the delay step
     * @return the brain schedule
     */
    public static Schedule toVanilla(DaySchedule schedule, int step) {
        ScheduleBuilder builder = new ScheduleBuilder(new Schedule());
        for (DaySchedule.Transition change : schedule.transitions(step)) {
            builder.changeActivityAt(change.time(), activity(change.phase()));
        }
        return builder.build();
    }

    /**
     * The vanilla activity of a phase.
     *
     * @param phase the phase
     * @return the activity
     */
    public static Activity activity(DayPhase phase) {
        return switch (phase) {
            case WORK -> Activity.WORK;
            case MARKET -> Activity.MEET;
            case HOME -> Activity.IDLE;
            case SLEEP -> Activity.REST;
        };
    }

    /**
     * Called by a mixin at the end of the build of the brain goals of a villager. A worker gets the worker schedule
     * and the activity of this schedule at once. An other villager loses a job sign as its job site (see
     * {@link #forgetJobSign}).
     *
     * @param villager the villager
     * @param brain    the new brain of the villager
     */
    public static void onBrainGoals(Villager villager, Brain<Villager> brain) {
        if (isWorker(villager)) {
            setSchedule(villager, brain, scheduleOf(villager));
        } else {
            forgetJobSign(villager, brain, null);
        }
    }

    /**
     * Gives a schedule to a brain, and sets the activity of the schedule at the time of the level. The vanilla brain
     * reads its schedule at most once each second, so this method does not wait for the next read. An activity with
     * priority over the schedule ({@code HIDE}, {@code PANIC}, {@code RAID}, {@code PRE_RAID}) stays active.
     */
    static void setSchedule(Villager villager, Brain<Villager> brain, Schedule schedule) {
        brain.setSchedule(schedule);
        if (PRIORITY_ACTIVITIES.stream().anyMatch(brain::isActive)) {
            return;
        }
        Activity activity = schedule.getActivityAt(DaySchedule.timeOfDay(villager.level().getDayTime()));
        if (!brain.isActive(activity)) {
            brain.setActiveActivityIfPossible(activity);
        }
    }

    /**
     * Erases the job site of a villager when it is a job sign other than its assigned sign. The memory points to a
     * job sign when the place has a point of interest of the type {@code folkhold_world:job_sign}. A place in a chunk
     * that is not loaded stays.
     *
     * @param villager the villager
     * @param brain    the brain of the villager
     * @param assigned the job sign of the villager, or null for no job sign
     * @return true if the memory was erased
     */
    static boolean forgetJobSign(Villager villager, Brain<Villager> brain, @Nullable GlobalPos assigned) {
        Optional<GlobalPos> site = brain.getMemory(MemoryModuleType.JOB_SITE);
        if (site.isEmpty() || site.get().equals(assigned) || villager.level().getServer() == null) {
            return false;
        }
        ServerLevel level = villager.level().getServer().getLevel(site.get().dimension());
        BlockPos pos = site.get().pos();
        if (level == null || !level.isLoaded(pos)
                || level.getPoiManager().getType(pos).filter(type -> type.is(ModPoiTypes.JOB_SIGN_KEY)).isEmpty()) {
            return false;
        }
        brain.eraseMemory(MemoryModuleType.JOB_SITE);
        return true;
    }

    /**
     * The validator of the memory {@code JOB_SITE} of the core package of a villager: the job site of the profession,
     * or a job sign. A job sign does not match the job site of the profession, so the vanilla rules that share or
     * release a job site ignore it.
     *
     * @param held the job site of the profession
     * @return the validator
     */
    public static Predicate<Holder<PoiType>> withJobSign(Predicate<Holder<PoiType>> held) {
        return held.or(holder -> holder.is(ModPoiTypes.JOB_SIGN_KEY));
    }

    /**
     * The validator of the memory {@code MEETING_POINT} of the meeting package of a villager: a bell, or a market
     * sign.
     *
     * @param meeting the vanilla validator
     * @return the validator
     */
    public static Predicate<Holder<PoiType>> withMarketSign(Predicate<Holder<PoiType>> meeting) {
        return meeting.or(holder -> holder.is(ModPoiTypes.MARKET_SIGN_KEY));
    }

    /** The brain schedules of the delay steps of one daily schedule. */
    private record Schedules(DaySchedule source, List<Schedule> steps) {
        static Schedules of(DaySchedule schedule) {
            List<Schedule> steps = new ArrayList<>(DaySchedule.WORKER_STEPS);
            for (int step = 0; step < DaySchedule.WORKER_STEPS; step++) {
                steps.add(toVanilla(schedule, step));
            }
            return new Schedules(schedule, List.copyOf(steps));
        }
    }
}
