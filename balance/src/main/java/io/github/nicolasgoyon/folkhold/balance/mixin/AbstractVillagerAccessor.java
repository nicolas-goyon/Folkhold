package io.github.nicolasgoyon.folkhold.balance.mixin;

import javax.annotation.Nullable;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.item.trading.MerchantOffers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * The offers of a villager or a wandering trader, with no side effect. {@link AbstractVillager#getOffers()} makes
 * the offers when they do not exist.
 */
@Mixin(AbstractVillager.class)
public interface AbstractVillagerAccessor {
    /** The offers, or null when the villager has no offers yet. */
    @Accessor("offers")
    @Nullable
    MerchantOffers folkhold_balance$offers();
}
