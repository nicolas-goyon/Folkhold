package io.github.nicolasgoyon.folkhold.world.village.schedule;

import io.github.nicolasgoyon.folkhold.world.registry.ModPoiTypes;
import io.github.nicolasgoyon.folkhold.world.registry.ModSchedules;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.function.Predicate;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;

/**
 * The brain of a worker of a faction village (spike #640): the worker schedule, the job sign as its job site and the
 * market as its meeting point. The vanilla behaviors do the walks.
 */
public final class WorkerBrains {

    private WorkerBrains() {
    }

    /**
     * Called by the mixin at the end of {@code Villager.registerBrainGoals}: the constructor, the load and each
     * {@code refreshBrain}. A villager of a faction village gets the worker schedule.
     */
    public static void onBrainGoals(Villager villager, Brain<Villager> brain) {
        if (!villager.isBaby() && VillageFolk.isVillageFolk(villager)) {
            brain.setSchedule(ModSchedules.WORKER.get());
        }
    }

    /**
     * The validator of the {@code JOB_SITE} memory: the held job site of the profession, or a job sign.
     *
     * @param held the held job site of the profession
     * @return the validator
     */
    public static Predicate<Holder<PoiType>> withJobSign(Predicate<Holder<PoiType>> held) {
        return held.or(holder -> holder.is(ModPoiTypes.JOB_SIGN_KEY));
    }

    /**
     * Sets the job site and the meeting point of a worker.
     *
     * @param villager the worker
     * @param jobSign  the position of the job sign of its building
     * @param market   the position of the meeting point of its market (a bell)
     */
    public static void assign(Villager villager, GlobalPos jobSign, GlobalPos market) {
        Brain<Villager> brain = villager.getBrain();
        brain.setMemory(MemoryModuleType.JOB_SITE, jobSign);
        brain.setMemory(MemoryModuleType.MEETING_POINT, market);
    }
}
