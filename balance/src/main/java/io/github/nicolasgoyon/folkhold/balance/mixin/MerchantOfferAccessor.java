package io.github.nicolasgoyon.folkhold.balance.mixin;

import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Sets the first cost of an offer. The other values of the offer (uses, demand, special price, experience) stay the
 * same.
 */
@Mixin(MerchantOffer.class)
public interface MerchantOfferAccessor {
    @Accessor("baseCostA")
    @Mutable
    void folkhold_balance$setBaseCostA(ItemCost cost);
}
