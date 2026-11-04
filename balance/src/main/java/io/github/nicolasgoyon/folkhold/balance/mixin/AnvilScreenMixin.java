package io.github.nicolasgoyon.folkhold.balance.mixin;

import io.github.nicolasgoyon.folkhold.balance.anvil.AnvilRules;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * The anvil screen. With the anvil rules of the balance mod, the screen never shows "Too Expensive!": it shows the
 * cost, in red when the player does not have enough levels.
 */
@Mixin(AnvilScreen.class)
// Mixin calls the injected method.
@SuppressWarnings("UnusedMethod")
abstract class AnvilScreenMixin {
    @ModifyConstant(method = "renderLabels", constant = @Constant(intValue = 40))
    private int folkhold_balance$noTooExpensive(int limit) {
        return AnvilRules.isEnabled() ? Integer.MAX_VALUE : limit;
    }
}
