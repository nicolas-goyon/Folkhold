package io.github.nicolasgoyon.folkhold.world.mixin;

import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The vanilla villager. A worker of a faction village keeps the worker schedule after each build of its brain goals:
 * the creation, the load and each refresh of the brain (see {@link WorkerBrains#onBrainGoals}).
 */
@Mixin(Villager.class)
// Mixin calls the injected method, and gives the callback info that the method does not use.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class VillagerMixin {
    @Inject(method = "registerBrainGoals", at = @At("TAIL"))
    private void folkhold_world$workerSchedule(Brain<Villager> brain, CallbackInfo info) {
        WorkerBrains.onBrainGoals((Villager) (Object) this, brain);
    }
}
