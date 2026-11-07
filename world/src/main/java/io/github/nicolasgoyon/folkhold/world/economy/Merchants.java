package io.github.nicolasgoyon.folkhold.world.economy;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobTrades;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.function.ToIntFunction;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
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
 * The trades of the workers of the faction villages: each worker sells the goods of its job.
 *
 * <p>A worker is a vanilla villager with the profession of its job (see {@link VillageFolk#professionOf}). So the
 * vanilla systems work with it: raids, Hero of the Village, zombie conversion and cure, iron golems, gossip, the
 * bell, and the other mods that use the villagers.
 *
 * <ul>
 *   <li>A worker trades when the settlement has an active job with its profession (see
 *       {@link Economies#activeJobIds}). The builders, the handymen and the inhabitants with no job do not trade.</li>
 *   <li>When a player starts to trade, the offers come from the stock of the village (see {@link JobTrades}): the
 *       worker sells the outputs of its jobs in stock (price {@code P × 1.15}) and buys the inputs of its jobs that
 *       the village needs (price {@code P × 0.85}). The treasury of the faction limits the purchases. The offer
 *       count of the level divides between the traders of the village.</li>
 *   <li>Each trade changes the stock and the treasury.</li>
 *   <li>A worker does not trade with a player below the neutral tier. Friends and allies get a discount.</li>
 * </ul>
 *
 * <p>The profession {@link ModProfessions#MERCHANT} does not trade: a villager with this profession becomes an
 * inhabitant at the next check of the heart (see {@link VillageFolk#inhabitants}).
 */
public final class Merchants {
    /** Persistent data key: the position of the heart of the village of a villager. */
    public static final String VILLAGE_TAG = "folkhold_world:village";
    /** Maximum uses of one offer. */
    private static final int MAX_USES = 64;
    /** The price change of an offer for each use, as a share: the vanilla demand. */
    public static final float PRICE_MULTIPLIER = 0.05F;

    private Merchants() {
    }

    /**
     * The active jobs that a worker of a settlement trades: the active jobs with the profession of the worker. The
     * inhabitant profession trades no job.
     *
     * @param settlement the settlement
     * @param profession the profession of the worker
     * @return the jobs, or no job if the worker does not trade
     */
    public static List<Job> tradedJobs(Settlement settlement, VillagerProfession profession) {
        if (profession == ModProfessions.INHABITANT.get()) {
            return List.of();
        }
        return Economies.activeJobs(settlement).stream()
                .filter(job -> VillageFolk.professionOf(job.id()) == profession)
                .distinct()
                .toList();
    }

    /**
     * The number of traders of a settlement: the different professions of its active jobs. The workers with the
     * same profession show the same offers.
     */
    public static int traderCount(Settlement settlement) {
        return (int) Economies.activeJobIds(settlement).stream()
                .map(VillageFolk::professionOf)
                .filter(profession -> profession != ModProfessions.INHABITANT.get())
                .distinct()
                .count();
    }

    /** The active settlement of a villager of a faction village, or empty. */
    private static Optional<Settlement> settlementOf(ServerLevel level, Villager villager) {
        if (!VillageFolk.isVillageFolk(villager)) {
            return Optional.empty();
        }
        VillageHeartBlockEntity heart = heartOf(level, villager);
        return heart == null ? Optional.empty()
                : Settlements.of(level.getServer(), heart).filter(Settlement::isActive);
    }

    /**
     * True if a villager of a faction village trades: its settlement is active and has an active job with the
     * profession of the villager.
     */
    public static boolean isTrader(ServerLevel level, Villager villager) {
        return settlementOf(level, villager)
                .filter(settlement -> !tradedJobs(settlement, villager.getVillagerData().getProfession()).isEmpty())
                .isPresent();
    }

    /** The heart of the village of a villager: the heart that it records, or the nearest heart. */
    @Nullable
    public static VillageHeartBlockEntity heartOf(ServerLevel level, AbstractVillager villager) {
        if (villager.getPersistentData().contains(VILLAGE_TAG)) {
            BlockPos pos = BlockPos.of(villager.getPersistentData().getLong(VILLAGE_TAG));
            if (ReadyChunks.isReady(level, pos) && level.getBlockEntity(pos) instanceof VillageHeartBlockEntity heart) {
                return heart;
            }
        }
        return VillageHearts.findNearest(level, villager.blockPosition(), WorldConfig.VILLAGE_RADIUS.get())
                .map(level::getBlockEntity)
                .filter(VillageHeartBlockEntity.class::isInstance)
                .map(VillageHeartBlockEntity.class::cast)
                .orElse(null);
    }

    /** True if the workers of the village trade with the player: neutral tier or better. */
    public static boolean canTrade(MinecraftServer server, ServerPlayer player, VillageHeartBlockEntity heart) {
        return !Reputations.tier(server, player, heart).isHostile();
    }

    /**
     * A player interacts with a worker that trades: the offers come from the stock, or the worker refuses. The other
     * villagers of the village do not trade (see {@link VillageFolk#onEntityInteract}).
     */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(event.getTarget() instanceof Villager villager)
                || !(player.level() instanceof ServerLevel level) || !isTrader(level, villager)) {
            return;
        }
        VillageHeartBlockEntity heart = heartOf(level, villager);
        Settlement settlement = settlementOf(level, villager).orElseThrow();
        if (!canTrade(level.getServer(), player, heart)) {
            player.displayClientMessage(Component.translatable("message.folkhold_world.merchant_refuses",
                    villager.getDisplayName()), true);
            villager.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
            event.setCancellationResult(InteractionResult.SUCCESS);
            event.setCanceled(true);
            return;
        }
        ReputationTier tier = Reputations.tier(level.getServer(), player, heart);
        villager.setOffers(offers(level.getServer(), settlement, tier, villager.getVillagerData().getProfession()));
    }

    /**
     * A trade with a worker changes the stock and the treasury. A trade that the stock or the treasury cannot pay is
     * canceled. After the trade, the open screens of the workers of the village show what the stock and the treasury
     * can pay now.
     */
    public static void onTrade(TradeWithVillagerEvent event) {
        AbstractVillager villager = event.getAbstractVillager();
        if (!(villager instanceof Villager worker) || !VillageFolk.isVillageFolk(worker)
                || !(villager.level() instanceof ServerLevel level)) {
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
            for (Villager other : VillageFolk.inhabitants(level, heart.getBlockPos())) {
                syncOffers(other, stock::count, treasury);
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
     * The offers of a worker of a settlement for a player: the goods of its active jobs (see {@link JobTrades}).
     *
     * @param server     the server
     * @param settlement the settlement
     * @param tier       the reputation tier of the player
     * @param profession the profession of the worker
     * @return the offers, empty if the worker does not trade
     */
    public static MerchantOffers offers(MinecraftServer server, Settlement settlement, ReputationTier tier,
            VillagerProfession profession) {
        MerchantOffers offers = new MerchantOffers();
        List<Job> jobs = tradedJobs(settlement, profession);
        if (jobs.isEmpty()) {
            return offers;
        }
        int count = JobTrades.share(LevelTables.get().traits(settlement.level()).tradeOffers(),
                traderCount(settlement));
        EconomySettings settings = Economies.settings();
        Stock stock = settlement.economy().stock();
        int population = settlement.economy().population();
        JobTrades.Plan plan = JobTrades.plan(jobs, stock.asMap(), EconomyData.catalog(), population, count,
                id -> ItemIds.item(id).filter(item -> item != Items.EMERALD).isPresent());
        double factor = priceFactor(tier);

        for (String id : plan.sells()) {
            if (offers.size() >= plan.sellSlots()) {
                break;
            }
            ItemInfo info = EconomyData.catalog().info(id);
            double price = Prices.sellPrice(info, stock.count(id), population, settings) * factor;
            sellOffer(ItemIds.item(id).orElseThrow(), stock.count(id), price, MAX_USES).ifPresent(offers::add);
        }

        long treasury = SettlementData.get(server).treasury(settlement.factionId());
        int bought = 0;
        for (String id : plan.buys()) {
            if (bought >= plan.buySlots()) {
                break;
            }
            ItemInfo info = EconomyData.catalog().info(id);
            Item item = ItemIds.item(id).orElseThrow();
            double price = Prices.buyPrice(info, stock.count(id), population, settings);
            int maxStack = new ItemStack(item).getMaxStackSize();
            int emeralds = price >= 1.0 ? (int) Math.min(64, Math.floor(price)) : 1;
            int quantity = price >= 1.0 ? 1 : (int) Math.max(1, Math.min(maxStack, Math.ceil(1.0 / price)));
            int wanted = (Prices.target(info, population) - stock.count(id)) / quantity;
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
     * The text of a price or of a price factor: two decimals at most, no trailing zero ("2", "0.85", "12.5"). The
     * decimal separator is a point in all languages.
     *
     * @param value the price in emeralds, or the factor
     * @return the text
     */
    public static String priceText(double value) {
        String text = String.format(Locale.ROOT, "%.2f", value);
        return text.contains(".") ? text.replaceAll("0+$", "").replaceAll("\\.$", "") : text;
    }

    /**
     * The items of a stock that a caravan merchant sells, with the most valuable stock first: the known items except
     * the emeralds.
     *
     * @param stock the stock, by item id
     * @return the entries of the stock, sorted
     */
    public static List<Map.Entry<String, Integer>> sellable(Map<String, Integer> stock) {
        return stock.entrySet().stream()
                .filter(entry -> ItemIds.item(entry.getKey()).filter(item -> item != Items.EMERALD).isPresent())
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) ->
                        EconomyData.catalog().info(entry.getKey()).price() * entry.getValue()).reversed())
                .toList();
    }

    /**
     * The offer of a merchant that sells one item: one unit for its price in emeralds, or several units for one
     * emerald when the price is below one emerald.
     *
     * @param item      the item
     * @param available the number of units for sale
     * @param price     the price of one unit, in emeralds
     * @param maxUses   the maximum number of trades of the offer
     * @return the offer, or empty if the available units are not enough for one trade
     */
    public static Optional<MerchantOffer> sellOffer(Item item, int available, double price, int maxUses) {
        int maxStack = new ItemStack(item).getMaxStackSize();
        int emeralds = price >= 1.0 ? (int) Math.min(64, Math.round(price)) : 1;
        int quantity = price >= 1.0 ? 1 : (int) Math.max(1, Math.min(maxStack, Math.floor(1.0 / price)));
        int uses = Math.min(maxUses, available / quantity);
        if (uses <= 0) {
            return Optional.empty();
        }
        return Optional.of(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                new ItemStack(item, quantity), uses, 0, PRICE_MULTIPLIER));
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
     * @param settlement the settlement of the worker
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
            stock.add(ItemIds.id(cost.getItem()), cost.getCount(), capacity);
            data.addTreasury(settlement.factionId(), -result.getCount());
        } else {
            if (stock.count(ItemIds.id(result.getItem())) < result.getCount()) {
                return false;
            }
            stock.remove(ItemIds.id(result.getItem()), result.getCount());
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
                    : available.applyAsInt(ItemIds.id(result.getItem())) / result.getCount();
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

}
