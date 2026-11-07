package io.github.nicolasgoyon.folkhold.world.mixin;

import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The core package keeps a {@code JOB_SITE} memory on a job sign (spike #640). The first {@code ValidateNearbyPoi} of
 * the core package validates {@code JOB_SITE} with the held job site of the profession.
 */
@Mixin(VillagerGoalPackages.class)
// Mixin calls the injected method.
@SuppressWarnings("UnusedMethod")
abstract class VillagerGoalPackagesMixin {

    @ModifyArg(method = "getCorePackage", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/minecraft/world/entity/ai/behavior/ValidateNearbyPoi;create("
                    + "Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)"
                    + "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;"), index = 0)
    private static Predicate<Holder<PoiType>> folkhold_world$jobSign(Predicate<Holder<PoiType>> held) {
        return WorkerBrains.withJobSign(held);
    }
}
