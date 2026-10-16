package io.github.nicolasgoyon.folkhold.world.economy;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * The merchants of the faction villages.
 *
 * <p>A merchant is a vanilla villager with the profession {@link ModProfessions#MERCHANT}. So the vanilla systems
 * work with it: raids, Hero of the Village, zombie conversion and cure, iron golems, gossip, the bell, and the other
 * mods that use the villagers.
 *
 * <ul>
 *   <li>When a player starts to trade, the offers come from the stocks of the village: the merchant sells the
 *       items in stock (price {@code P × 1.15}) and buys the items that the village needs (price
 *       {@code P × 0.85}). The treasury of the faction limits the purchases. The number of offers comes from the
 *       level.</li>
 *   <li>Each trade changes the stock and the treasury.</li>
 *   <li>The merchant does not trade with a player below the neutral tier. Friends and allies get a discount.</li>
 * </ul>
 */
public final class Merchants {
    /** Persistent data key: the position of the heart of the village of a merchant. */
    public static final String VILLAGE_TAG = "folkhold_world:village";
    /** Maximum uses of one offer. */
    private static final int MAX_USES = 64;
    private static final float PRICE_MULTIPLIER = 0.05F;

    private Merchants() {
    }

    /** True if the entity is a villager with the merchant profession. */
    public static boolean isMerchant(Entity entity) {
        return entity instanceof Villager villager
                && villager.getVillagerData().getProfession() == ModProfessions.MERCHANT.get();
    }

    /** The heart of the village of a merchant: the heart that it records, or the nearest heart. */
    @Nullable
    public static VillageHeartBlockEntity heartOf(ServerLevel level, AbstractVillager villager) {
        if (villager.getPersistentData().contains(VILLAGE_TAG)) {
            BlockPos pos = BlockPos.of(villager.getPersistentData().getLong(VILLAGE_TAG));
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
                return heart;
            }
        }
        return VillageHearts.findNearest(level, villager.blockPosition(), WorldConfig.VILLAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast)
                .orElse(null);
    }

    /** True if the merchants of the village trade with the player: neutral tier or better. */
    public static boolean canTrade(MinecraftServer server, ServerPlayer player, VillageHeartBlockEntity heart) {
        return Reputations.tier(server, player, heart).ordinal() >= ReputationTier.NEUTRAL.ordinal();
    }

    /** A player interacts with a merchant: the offers come from the stocks, or the merchant refuses. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !isMerchant(event.getTarget())
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Villager villager = (Villager) event.getTarget();
        VillageHeartBlockEntity heart = heartOf(level, villager);
        if (heart == null) {
            return;
        }
        Optional<Settlement> settlement = Settlements.of(level.getServer(), heart).filter(Settlement::isActive);
        if (settlement.isEmpty()) {
            villager.setOffers(new MerchantOffers());
            return;
        }
        if (!canTrade(level.getServer(), player, heart)) {
            player.displayClientMessage(Component.translatable("message.folkhold_world.merchant_refuses",
                    villager.getDisplayName()), true);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        ReputationTier tier = Reputations.tier(level.getServer(), player, heart);
        villager.setOffers(offers(level.getServer(), settlement.get(), tier));
    }

    /**
     * A trade with a merchant changes the stock and the treasury. A trade that the stock or the treasury cannot pay
     * is canceled. After the trade, the open screens of the merchants of the village show what the stock and the
     * treasury can pay now.
     */
    public static void onTrade(TradeWithVillagerEvent event) {
        AbstractVillager villager = event.getAbstractVillager();
        if (!isMerchant(villager) || !(villager.level() instanceof ServerLevel level)) {
            return;
        }
        VillageHeartBlockEntity heart = heartOf(level, villager);
        if (heart == null) {
            return;
        }
        MinecraftServer server = level.getServer();
        Settlements.of(server, heart).ifPresent(settlement -> {
            if (!applyTrade(server, settlement, event.getMerchantOffer())
                    && event.getEntity() instanceof ServerPlayer player) {
                cancel(player, event.getMerchantOffer());
            }
            Stock stock = settlement.economy().stock();
            long treasury = SettlementData.get(server).treasury(settlement.factionId());
            for (Villager merchant : merchantsOf(level, heart.getBlockPos())) {
                syncOffers(merchant, stock::count, treasury);
            }
        });
    }

    /**
     * The price factor for a reputation tier: a discount for the friends and the allies.
     *
     * @param tier the tier of the player
     * @return the factor of the sell prices
     */
    public static double priceFactor(ReputationTier tier) {
        return switch (tier) {
            case ALLY -> WorldConfig.MERCHANT_ALLY_FACTOR.get();
            case FRIEND -> WorldConfig.MERCHANT_FRIEND_FACTOR.get();
            default -> 1.0;
        };
    }

    /**
     * The offers of the merchants of a settlement for a player.
     *
     * @param server     the server
     * @param settlement the settlement
     * @param tier       the reputation tier of the player
     * @return the offers
     */
    public static MerchantOffers offers(MinecraftServer server, Settlement settlement, ReputationTier tier) {
        MerchantOffers offers = new MerchantOffers();
        int maxOffers = LevelTables.get().traits(settlement.level()).tradeOffers();
        int sellSlots = Math.max(1, (maxOffers * 2 + 2) / 3);
        int buySlots = Math.max(0, maxOffers - sellSlots);
        EconomySettings settings = Economies.settings();
        Stock stock = settlement.economy().stock();
        int population = settlement.economy().population();
        double factor = priceFactor(tier);

        List<Map.Entry<String, Integer>> sellable = stock.asMap().entrySet().stream()
                .filter(entry -> item(entry.getKey()).filter(item -> item != Items.EMERALD).isPresent())
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) ->
                        EconomyData.catalog().info(entry.getKey()).price() * entry.getValue()).reversed())
                .toList();
        for (Map.Entry<String, Integer> entry : sellable) {
            if (offers.size() >= sellSlots) {
                break;
            }
            Item item = item(entry.getKey()).orElseThrow();
            ItemInfo info = EconomyData.catalog().info(entry.getKey());
            double price = Prices.sellPrice(info, entry.getValue(), population, settings) * factor;
            int maxStack = new ItemStack(item).getMaxStackSize();
            int emeralds = price >= 1.0 ? (int) Math.min(64, Math.round(price)) : 1;
            int quantity = price >= 1.0 ? 1 : (int) Math.max(1, Math.min(maxStack, Math.floor(1.0 / price)));
            int uses = Math.min(MAX_USES, entry.getValue() / quantity);
            if (uses > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                        new ItemStack(item, quantity), uses, 0, PRICE_MULTIPLIER));
            }
        }

        long treasury = SettlementData.get(server).treasury(settlement.factionId());
        List<ItemInfo> needs = EconomyData.catalog().all().stream()
                .filter(info -> item(info.id()).isPresent()
                        && stock.count(info.id()) < Prices.target(info, population) / 2)
                .sorted(Comparator.comparingInt((ItemInfo info) ->
                        stock.count(info.id()) - Prices.target(info, population)).thenComparing(ItemInfo::id))
                .toList();
        int bought = 0;
        for (ItemInfo info : needs) {
            if (bought >= buySlots) {
                break;
            }
            Item item = item(info.id()).orElseThrow();
            double price = Prices.buyPrice(info, stock.count(info.id()), population, settings);
            int maxStack = new ItemStack(item).getMaxStackSize();
            int emeralds = price >= 1.0 ? (int) Math.min(64, Math.floor(price)) : 1;
            int quantity = price >= 1.0 ? 1 : (int) Math.max(1, Math.min(maxStack, Math.ceil(1.0 / price)));
            int wanted = (Prices.target(info, population) - stock.count(info.id())) / quantity;
            int uses = (int) Math.min(MAX_USES, Math.min(wanted, treasury / emeralds));
            if (uses > 0) {
                offers.add(new MerchantOffer(new ItemCost(item, quantity), Optional.empty(),
                        new ItemStack(Items.EMERALD, emeralds), uses, 0, PRICE_MULTIPLIER));
                bought++;
            }
        }
        return offers;
    }

    /**
     * Applies one trade to the stock and the treasury.
     *
     * <ul>
     *   <li>The player buys an item: the item leaves the stock, the emeralds go into the treasury.</li>
     *   <li>The player sells an item: the item goes into the stock, up to the stock capacity of the level, and the
     *       emeralds leave the treasury.</li>
     * </ul>
     *
     * @param server     the server
     * @param settlement the settlement of the merchant
     * @param offer      the offer of the trade
     * @return true if the trade applied. False when the stock does not have the items or the treasury does not have
     *         the emeralds: the stock and the treasury do not change.
     */
    public static boolean applyTrade(MinecraftServer server, Settlement settlement, MerchantOffer offer) {
        SettlementData data = SettlementData.get(server);
        ItemStack cost = offer.getCostA();
        ItemStack result = offer.getResult();
        Stock stock = settlement.economy().stock();
        if (result.is(Items.EMERALD)) {
            if (data.treasury(settlement.factionId()) < result.getCount()) {
                return false;
            }
            int capacity = LevelTables.get().traits(settlement.level()).stockCapacity();
            stock.add(key(cost.getItem()), cost.getCount(), capacity);
            data.addTreasury(settlement.factionId(), -result.getCount());
        } else {
            if (stock.count(key(result.getItem())) < result.getCount()) {
                return false;
            }
            stock.remove(key(result.getItem()), result.getCount());
            if (cost.is(Items.EMERALD)) {
                data.addTreasury(settlement.factionId(), cost.getCount());
            }
        }
        data.setDirty();
        return true;
    }

    /**
     * Cancels a trade that the merchant cannot pay: the result goes back from the player, the player gets the cost
     * back and a notice. The player keeps the part of the result that is not in the inventory and not in the hand
     * (for example a dropped item).
     *
     * @param player the player
     * @param offer  the offer of the trade
     */
    public static void cancel(ServerPlayer player, MerchantOffer offer) {
        ItemStack result = offer.getResult();
        int left = result.getCount();
        ItemStack carried = player.containerMenu.getCarried();
        if (ItemStack.isSameItemSameComponents(carried, result)) {
            int taken = Math.min(left, carried.getCount());
            carried.shrink(taken);
            left -= taken;
        }
        if (left > 0) {
            player.getInventory().clearOrCountMatchingItems(stack -> ItemStack.isSameItemSameComponents(stack, result),
                    left, player.inventoryMenu.getCraftSlots());
        }
        player.getInventory().placeItemBackInInventory(offer.getCostA());
        player.getInventory().placeItemBackInInventory(offer.getCostB().copy());
        Notices.send(player, Component.translatable("message.folkhold_world.trade_canceled"),
                Component.translatable("message.folkhold_world.trade_canceled.details"), Notices.Tone.WARN);
    }

    /**
     * Limits the uses of the offers of a merchant to what the merchant can pay now, and shows the offers again to
     * the player who trades with the merchant. Does nothing when no player trades with the merchant.
     *
     * @param merchant  the merchant
     * @param available the quantity that the merchant can sell, by item id
     * @param treasury  the emeralds that the merchant can pay
     */
    public static void syncOffers(AbstractVillager merchant, ToIntFunction<String> available, long treasury) {
        if (!(merchant.getTradingPlayer() instanceof ServerPlayer player)
                || !(player.containerMenu instanceof MerchantMenu menu)) {
            return;
        }
        MerchantOffers offers = merchant.getOffers();
        boolean changed = false;
        for (int index = 0; index < offers.size(); index++) {
            MerchantOffer offer = offers.get(index);
            ItemStack result = offer.getResult();
            long payable = result.is(Items.EMERALD) ? treasury / result.getCount()
                    : available.applyAsInt(key(result.getItem())) / result.getCount();
            int maxUses = (int) Math.min(offer.getMaxUses(), offer.getUses() + payable);
            if (maxUses != offer.getMaxUses()) {
                MerchantOffer limited = new MerchantOffer(offer.getItemCostA(), offer.getItemCostB(), result,
                        offer.getUses(), maxUses, offer.getXp(), offer.getPriceMultiplier(), offer.getDemand());
                limited.setSpecialPriceDiff(offer.getSpecialPriceDiff());
                offers.set(index, limited);
                changed = true;
            }
        }
        if (changed) {
            int level = merchant instanceof Villager villager ? villager.getVillagerData().getLevel() : 1;
            player.sendMerchantOffers(menu.containerId, offers, level, merchant.getVillagerXp(),
                    merchant.showProgressBar(), merchant.canRestock());
            menu.slotsChanged(player.getInventory());
        }
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }

    private static String key(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** The target number of merchants of a settlement: 1, plus 1 for each 8 levels. */
    public static int merchantCount(Settlement settlement) {
        return 1 + settlement.level() / 8;
    }

    /** A list of the loaded merchants of a village. */
    public static List<Villager> merchantsOf(ServerLevel level, BlockPos heartPos) {
        return VillageFolk.of(level, heartPos, ModProfessions.MERCHANT.get());
    }
}
