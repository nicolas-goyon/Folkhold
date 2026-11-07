package io.github.nicolasgoyon.folkhold.world.mixin;

import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A villager of a faction village keeps the worker schedule after each brain refresh (spike #640). */
@Mixin(Villager.class)
// Mixin calls the injected method.
@SuppressWarnings({"UnusedMethod", "UnusedVariable"})
abstract class VillagerMixin {

    @Inject(method = "registerBrainGoals", at = @At("TAIL"))
    private void folkhold_world$workerSchedule(Brain<Villager> brain, CallbackInfo info) {
        WorkerBrains.onBrainGoals((Villager) (Object) this, brain);
    }
}
