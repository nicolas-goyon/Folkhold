package io.github.nicolasgoyon.folkhold.balance.enchantment;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.mixin.AbstractVillagerAccessor;
import io.github.nicolasgoyon.folkhold.balance.mixin.MerchantOfferAccessor;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.tags.EnchantmentTags;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.VillagerTrades.ItemListing;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.village.VillagerTradesEvent;
import net.neoforged.neoforge.event.village.WandererTradesEvent;

/** Removes the blocked enchantments from the offers of villagers and wandering traders. */
public final class VillagerOffers {
    private VillagerOffers() {
    }

    /**
     * Wraps each villager listing in a {@link FilteredListing}. Register this listener with
     * {@link EventPriority#LOWEST}: the filter then also wraps the listings that other mods add.
     */
    public static void onVillagerTrades(VillagerTradesEvent event) {
        for (List<ItemListing> listings : event.getTrades().values()) {
            FilteredListing.wrapAll(listings);
        }
    }

    /** Wraps each wandering trader listing in a {@link FilteredListing}. Register with {@link EventPriority#LOWEST}. */
    public static void onWandererTrades(WandererTradesEvent event) {
        FilteredListing.wrapAll(event.getGenericTrades());
        FilteredListing.wrapAll(event.getRareTrades());
    }

    /** Cleans the existing offers of a villager or a wandering trader when it joins a server level. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof AbstractVillager villager
                && BalanceConfig.SPEC.isLoaded() && BalanceConfig.BLOCKED_IN_TRADES.get() && BalanceConfig.BLOCKED_CLEAN_EXISTING_OFFERS.get()) {
            clean(villager);
        }
    }

    /**
     * Replaces each blocked enchantment on the result of an offer with a random enchantment of the tag
     * {@code #minecraft:tradeable}, at the same level in the range of the new enchantment. For a vanilla book offer
     * (one stored enchantment for emeralds), the price is calculated again with the vanilla formula. The uses, the
     * demand and the other values of the offer stay the same. A villager with no offers yet does not change.
     *
     * @param villager the villager or the wandering trader
     * @return the number of changed offers
     */
    public static int clean(AbstractVillager villager) {
        MerchantOffers offers = ((AbstractVillagerAccessor) villager).folkhold_balance$offers();
        if (offers == null) {
            return 0;
        }
        EnchantmentFilter filter = BlockedEnchantments.filter();
        int changed = 0;
        for (MerchantOffer offer : offers) {
            // The result of an offer is the stack of the offer, not a copy.
            ItemStack result = offer.getResult();
            if (BlockedEnchantments.replaceBlocked(result, filter, EnchantmentTags.TRADEABLE,
                    villager.level().registryAccess(), villager.getRandom())) {
                reprice(offer, result, villager);
                changed++;
            }
        }
        return changed;
    }

    private static void reprice(MerchantOffer offer, ItemStack book, AbstractVillager villager) {
        ItemCost cost = offer.getItemCostA();
        ItemEnchantments stored = book.getOrDefault(DataComponents.STORED_ENCHANTMENTS, ItemEnchantments.EMPTY);
        if (!book.is(Items.ENCHANTED_BOOK) || stored.size() != 1 || !cost.itemStack().is(Items.EMERALD)) {
            return;
        }
        Holder<Enchantment> enchantment = stored.keySet().iterator().next();
        int level = stored.getLevel(enchantment);
        int price = BookOffers.price(level, villager.getRandom().nextInt(BookOffers.priceRollBound(level)),
                enchantment.is(EnchantmentTags.DOUBLE_TRADE_PRICE));
        ((MerchantOfferAccessor) offer).folkhold_balance$setBaseCostA(
                new ItemCost(cost.item(), price, cost.components()));
    }
}
