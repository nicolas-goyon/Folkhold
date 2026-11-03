package io.github.nicolasgoyon.folkhold.balance.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.enchantment.Enchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The name of an enchantment with its level. The vanilla language files have the levels I to X only. A level with no
 * translation shows as a number, for example "Efficiency 20".
 */
@Mixin(Enchantment.class)
// Mixin calls the injected method.
@SuppressWarnings("UnusedMethod")
abstract class EnchantmentMixin {
    private static final String LEVEL_KEY = "enchantment.level.";

    @WrapOperation(method = "getFullname", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/network/chat/Component;translatable(Ljava/lang/String;)"
                    + "Lnet/minecraft/network/chat/MutableComponent;"))
    private static MutableComponent folkhold_balance$levelAsNumber(String key, Operation<MutableComponent> original) {
        if (key.startsWith(LEVEL_KEY)) {
            return Component.translatableWithFallback(key, key.substring(LEVEL_KEY.length()));
        }
        return original.call(key);
    }
}
