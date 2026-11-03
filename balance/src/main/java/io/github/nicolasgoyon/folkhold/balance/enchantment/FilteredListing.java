package io.github.nicolasgoyon.folkhold.balance.enchantment;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.item.trading.MerchantOffer;

/**
 * A trade listing that never gives an offer with a blocked enchantment on its result. When an offer has a blocked
 * enchantment, the listing makes the offer again, up to {@code rerollAttempts} times. When all the attempts fail,
 * the listing gives no offer: the villager then takes an offer from a different listing.
 *
 * <p>The listing reads the config at each call.
 *
 * @param delegate the listing that makes the offers
 */
public record FilteredListing(ItemListing delegate) implements ItemListing {

    /**
     * Wraps each listing of a list in place. A listing that is already a filtered listing stays the same.
     *
     * @param listings the listings
     */
    public static void wrapAll(List<ItemListing> listings) {
        listings.replaceAll(listing -> listing instanceof FilteredListing ? listing : new FilteredListing(listing));
    }

    @Override
    @Nullable
    public MerchantOffer getOffer(Entity trader, RandomSource random) {
        MerchantOffer offer = delegate.getOffer(trader, random);
        if (!BalanceConfig.SPEC.isLoaded() || !BalanceConfig.BLOCKED_IN_TRADES.get()) {
            return offer;
        }
        EnchantmentFilter filter = BlockedEnchantments.filter();
        int rerolls = BalanceConfig.BLOCKED_REROLL_ATTEMPTS.get();
        for (int attempt = 0; offer != null && BlockedEnchantments.hasBlocked(offer.getResult(), filter); attempt++) {
            if (attempt >= rerolls) {
                return null;
            }
            offer = delegate.getOffer(trader, random);
        }
        return offer;
    }
}
