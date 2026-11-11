package io.github.nicolasgoyon.folkhold.world.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import java.util.function.Predicate;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import net.minecraft.world.entity.ai.behavior.OneShot;
import net.minecraft.world.entity.ai.behavior.VillagerGoalPackages;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.Villager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

/**
 * The goal packages of the vanilla villager. Near a memory of a place, the vanilla brain erases the memory if the
 * place is not a point of interest of the expected type. A worker of a faction village keeps a job sign as its job
 * site and a market sign as its meeting point. A villager of a faction village does not claim a vanilla workstation
 * and keeps its profession with no job site. A worker keeps its job site and its meeting point after a long walk with
 * no path to the place (see {@link WorkerBrains}).
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
    private static final String RESET = "Lnet/minecraft/world/entity/ai/behavior/ResetProfession;create()"
            + "Lnet/minecraft/world/entity/ai/behavior/BehaviorControl;";
    private static final String WALK = "Lnet/minecraft/world/entity/ai/behavior/SetWalkTargetFromBlockMemory;create("
            + "Lnet/minecraft/world/entity/ai/memory/MemoryModuleType;FIII)"
            + "Lnet/minecraft/world/entity/ai/behavior/OneShot;";

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

    /** The behavior of the core package that removes the profession of a villager with no job site. */
    @ModifyExpressionValue(method = "getCorePackage", at = @At(value = "INVOKE", target = RESET))
    private static BehaviorControl<Villager> folkhold_world$keepProfession(BehaviorControl<Villager> reset) {
        return WorkerBrains.keepProfession(reset);
    }

    /** The walk to the job site in the work package: the only call of {@code SetWalkTargetFromBlockMemory}. */
    @WrapOperation(method = "getWorkPackage", at = @At(value = "INVOKE", target = WALK))
    private static OneShot<Villager> folkhold_world$keepJobSite(MemoryModuleType<GlobalPos> place, float speed,
            int closeEnough, int tooFar, int unreachableTicks, Operation<OneShot<Villager>> original) {
        return WorkerBrains.keepPlace(original.call(place, speed, closeEnough, tooFar, unreachableTicks), place,
                unreachableTicks);
    }

    /** The walk to the meeting point in the meeting package: the only call of {@code SetWalkTargetFromBlockMemory}. */
    @WrapOperation(method = "getMeetPackage", at = @At(value = "INVOKE", target = WALK))
    private static OneShot<Villager> folkhold_world$keepMeetingPoint(MemoryModuleType<GlobalPos> place, float speed,
            int closeEnough, int tooFar, int unreachableTicks, Operation<OneShot<Villager>> original) {
        return WorkerBrains.keepPlace(original.call(place, speed, closeEnough, tooFar, unreachableTicks), place,
                unreachableTicks);
    }
}
