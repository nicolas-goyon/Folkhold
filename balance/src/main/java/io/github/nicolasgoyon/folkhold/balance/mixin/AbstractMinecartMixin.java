package io.github.nicolasgoyon.folkhold.balance.mixin;

import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.TrainSpeed;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * The vanilla minecart. Two linked minecarts do not push each other: the chain controls their distance (see
 * {@link io.github.nicolasgoyon.folkhold.balance.rail.Coupling}). The vanilla push gives the motion of a furnace
 * minecart to the minecart that it touches, and this motion is much more than the real speed of the furnace
 * minecart.
 *
 * <p>A minecart in a train with a locomotive gets the speed cap of its train (see
 * {@link io.github.nicolasgoyon.folkhold.balance.rail.TrainSpeed}). Other minecarts keep the vanilla cap.
 */
@Mixin(AbstractMinecart.class)
// Mixin calls the injected method.
@SuppressWarnings("UnusedMethod")
abstract class AbstractMinecartMixin {
    @Inject(method = "push(Lnet/minecraft/world/entity/Entity;)V", at = @At("HEAD"), cancellable = true)
    private void folkhold_balance$noPushInTrain(Entity entity, CallbackInfo info) {
        if (entity instanceof AbstractMinecart other && CartLinks.areLinked((AbstractMinecart) (Object) this, other)) {
            info.cancel();
        }
    }

    @Inject(method = "getMaxSpeedWithRail()D", at = @At("RETURN"), cancellable = true)
    private void folkhold_balance$trainSpeedCap(CallbackInfoReturnable<Double> info) {
        info.setReturnValue(TrainSpeed.railCap((AbstractMinecart) (Object) this, info.getReturnValueD()));
    }
}
