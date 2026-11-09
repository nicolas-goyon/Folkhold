package io.github.nicolasgoyon.folkhold.world.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import java.util.function.Predicate;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The goal packages of the vanilla villager. Near a memory of a place, the vanilla brain erases the memory if the
 * place is not a point of interest of the expected type. A worker of a faction village keeps a job sign as its job
 * site and a market sign as its meeting point. A villager of a faction village does not claim a vanilla workstation
 * (see {@link WorkerBrains}).
 */
@Mixin(VillagerGoalPackages.class)
// Mixin calls the injected methods.
@SuppressWarnings("UnusedMethod")
abstract class VillagerGoalPackagesMixin {
    private static final String VALIDATE = "Lnet/minecraft/world/entity/ai/behavior/ValidateNearbyPoi;create("
            + "Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;)"
            + "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;";
    private static final String ACQUIRE = "Lnet/minecraft/world/entity/ai/behavior/AcquirePoi;create("
            + "Ljava/util/function/Predicate;Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;"
            + "Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;ZLjava/util/Optional;)"
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

    /**
     * The behavior of the core package that claims a workstation: the only call with the memories {@code JOB_SITE}
     * and {@code POTENTIAL_JOB_SITE}.
     */
    @ModifyExpressionValue(method = "getCorePackage", at = @At(value = "INVOKE", target = ACQUIRE))
    private static BehaviorControl<PathfinderMob> folkhold_world$noWorkstation(
            BehaviorControl<PathfinderMob> acquire) {
        return WorkerBrains.withoutWorkstation(acquire);
    }
}
