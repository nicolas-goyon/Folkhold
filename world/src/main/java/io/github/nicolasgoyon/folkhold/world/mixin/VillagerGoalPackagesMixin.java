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
 * The goal packages of the vanilla villager. Near a memory of a place, the vanilla brain erases the memory if the
 * place is not a point of interest of the expected type. A worker of a faction village keeps a job sign as its job
 * site and a market sign as its meeting point (see {@link WorkerBrains}).
 */
@Mixin(VillagerGoalPackages.class)
// Mixin calls the injected methods.
@SuppressWarnings("UnusedMethod")
abstract class VillagerGoalPackagesMixin {
    private static final String VALIDATE = "Lnet/minecraft/world/entity/ai/behavior/ValidateNearbyPoi;create("
            + "Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)"
            + "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;";

    /** The first validator of the core package: the memory {@code JOB_SITE}. */
    @ModifyArg(method = "getCorePackage", at = @At(value = "INVOKE", ordinal = 0, target = VALIDATE), index = 0)
    private static Predicate<Holder<PoiType>> folkhold_world$jobSign(Predicate<Holder<PoiType>> held) {
        return WorkerBrains.withJobSign(held);
    }

    /** The validator of the meeting package: the memory {@code MEETING_POINT}. */
    @ModifyArg(method = "getMeetPackage", at = @At(value = "INVOKE", ordinal = 0, target = VALIDATE), index = 0)
    private static Predicate<Holder<PoiType>> folkhold_world$marketSign(Predicate<Holder<PoiType>> meeting) {
        return WorkerBrains.withMarketSign(meeting);
    }
}
