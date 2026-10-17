package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.entity.CaravanMerchant;
import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import io.github.nicolasgoyon.folkhold.world.faction.FactionVisuals;
import io.github.nicolasgoyon.folkhold.world.faction.Reputations;
import io.github.nicolasgoyon.folkhold.world.notice.Notices;
import io.github.nicolasgoyon.folkhold.world.quest.Escorts;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.entity.player.TradeWithVillagerEvent;

/**
 * What a player does with a caravan: trade on the road, or raid.
 *
 * <ul>
 *   <li><b>Trade</b>: the caravan merchant sells the items of the cargo, with the prices of the origin settlement.
 *       The reputation rules of the village merchant apply: no trade below the neutral tier, a discount for the
 *       friends and the allies. A sale removes the items from the cargo. The emeralds go into the treasury of the
 *       faction of the caravan.</li>
 *   <li><b>Raid</b>: a player who hurts a member of the caravan, or who opens a mule chest, raids the caravan. The
 *       reputation of the player with the faction of the caravan goes down (once in each offense window), the
 *       guards of the caravan attack the player, and the escort of the player on this caravan fails.</li>
 *   <li>A mule of a caravan does not carry a player. To open its chest, the player uses the secondary action (sneak
 *       and click).</li>
 * </ul>
 */
public final class CaravanInteractions {
    /** The maximum number of offers of a caravan merchant. */
    private static final int MAX_OFFERS = 6;
    /** The maximum number of uses of one offer. */
    private static final int MAX_USES = 16;
    private static final float PRICE_MULTIPLIER = 0.05F;

    /** Game time of the last raid of each player on each group, by "group|player". Not saved. */
    private static final Map<String, Long> LAST_RAIDS = new HashMap<>();

    private CaravanInteractions() {
    }

    /** The group of an entity, if the entity is a member of a known group. */
    private static Optional<MobileGroup> groupOf(MinecraftServer server, Entity entity) {
        String groupId = GroupMaterializer.groupId(entity);
        return groupId == null ? Optional.empty() : MobileGroupData.get(server).groups().byId(groupId);
    }

    /** A player interacts with a caravan merchant or a caravan mule. */
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        Entity target = event.getTarget();
        Optional<MobileGroup> group = groupOf(level.getServer(), target);
        if (group.isEmpty()) {
            return;
        }
        if (target instanceof CaravanMerchant merchant) {
            ReputationTier tier = Reputations.tier(level.getServer(), player, group.get().factionId());
            if (tier.isHostile()) {
                player.displayClientMessage(Component.translatable("message.folkhold_world.merchant_refuses",
                        merchant.getDisplayName()), true);
                merchant.playSound(SoundEvents.VILLAGER_NO, 1.0F, 1.0F);
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            merchant.setOffers(offers(level.getServer(), group.get(), tier));
        } else if (target instanceof AbstractChestedHorse) {
            if (!player.isSecondaryUseActive()) {
                Notices.warn(player, "caravan_mule", Component.translatable("message.folkhold_world.caravan_mule"),
                        Component.translatable("message.folkhold_world.caravan_mule.details"));
                event.setCancellationResult(InteractionResult.SUCCESS);
                event.setCanceled(true);
                return;
            }
            raid(level, player, group.get());
        }
    }

    /**
     * A trade with a caravan merchant removes the items from the cargo and fills the treasury. A trade that the
     * cargo cannot pay is canceled. After the trade, the open screen shows what the cargo can pay now.
     */
    public static void onTrade(TradeWithVillagerEvent event) {
        if (!(event.getAbstractVillager() instanceof CaravanMerchant merchant)
                || !(merchant.level() instanceof ServerLevel level)) {
            return;
        }
        groupOf(level.getServer(), merchant).ifPresent(group -> {
            MerchantOffer offer = event.getMerchantOffer();
            ItemStack result = offer.getResult();
            String item = BuiltInRegistries.ITEM.getKey(result.getItem()).toString();
            int count = result.getCount();
            boolean paid = group.cargo().count(item) >= count
                    && GroupMaterializer.removeCargo(level, group, item, count) == count;
            if (!paid) {
                if (event.getEntity() instanceof ServerPlayer player) {
                    Merchants.cancel(player, offer);
                }
            } else if (offer.getCostA().is(Items.EMERALD)) {
                SettlementData.get(level.getServer()).addTreasury(group.factionId(), offer.getCostA().getCount());
            }
            Merchants.syncOffers(merchant, group.cargo()::count, 0L);
        });
    }

    /** A player who hurts a member of a caravan raids the caravan. */
    public static void onDamage(LivingDamageEvent.Post event) {
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)
                || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        groupOf(level.getServer(), event.getEntity()).ifPresent(group -> raid(level, player, group));
    }

    /**
     * A player raids a caravan: the guards attack the player, and the reputation goes down once in each offense
     * window.
     *
     * @param level  the level
     * @param player the player
     * @param group  the caravan
     */
    public static void raid(ServerLevel level, ServerPlayer player, MobileGroup group) {
        Escorts.onRaid(level.getServer(), player, group);
        for (GroupMember member : group.members()) {
            if (member.role() == MemberRole.GUARD && level.getEntity(member.id()) instanceof FactionUnit guard
                    && guard.isAlive()) {
                guard.setTarget(player);
            }
        }
        String key = group.id() + "|" + player.getUUID();
        long now = level.getGameTime();
        Long last = LAST_RAIDS.get(key);
        if (last != null && now - last < WorldConfig.REPUTATION_OFFENSE_WINDOW_SECONDS.get() * 20L) {
            return;
        }
        LAST_RAIDS.put(key, now);
        Reputations.apply(level.getServer(), player, group.factionId(), null,
                WorldConfig.REPUTATION_RAID_CARAVAN.get());
        Component faction = FactionVisuals.faction(level.getServer(), group.factionId())
                .<Component>map(FactionVisuals::name).orElse(Component.literal(group.factionId()));
        Notices.send(player, Component.translatable("message.folkhold_world.caravan_raided", faction), null,
                Notices.Tone.BAD);
    }

    /**
     * The offers of a caravan merchant for a player: the items of the cargo with the most value first. A traveling
     * merchant has its own offers (see {@link TravelingMerchants#offers(MobileGroup, ReputationTier)}).
     *
     * @param server the server
     * @param group  the caravan
     * @param tier   the reputation tier of the player
     * @return the offers
     */
    public static MerchantOffers offers(MinecraftServer server, MobileGroup group, ReputationTier tier) {
        if (group.kind() == GroupKind.TRAVELING_MERCHANT) {
            return TravelingMerchants.offers(group, tier);
        }
        MerchantOffers offers = new MerchantOffers();
        Optional<Settlement> origin = SettlementData.get(server).registry().byId(group.originId());
        EconomySettings settings = Economies.settings();
        double factor = Merchants.priceFactor(tier);
        List<Map.Entry<String, Integer>> items = group.cargo().asMap().entrySet().stream()
                .filter(entry -> item(entry.getKey()).filter(item -> item != Items.EMERALD).isPresent())
                .sorted(Comparator.comparingDouble((Map.Entry<String, Integer> entry) ->
                        EconomyData.catalog().info(entry.getKey()).price() * entry.getValue()).reversed())
                .toList();
        for (Map.Entry<String, Integer> entry : items) {
            if (offers.size() >= MAX_OFFERS) {
                break;
            }
            Item item = item(entry.getKey()).orElseThrow();
            ItemInfo info = EconomyData.catalog().info(entry.getKey());
            double price = origin.map(settlement -> Prices.sellPrice(info,
                    settlement.economy().stock().count(entry.getKey()), settlement.economy().population(), settings))
                    .orElse(info.price() * settings.sellMarkup()) * factor;
            int maxStack = new ItemStack(item).getMaxStackSize();
            int emeralds = price >= 1.0 ? (int) Math.min(64, Math.round(price)) : 1;
            int quantity = price >= 1.0 ? 1 : (int) Math.max(1, Math.min(maxStack, Math.floor(1.0 / price)));
            int uses = Math.min(MAX_USES, entry.getValue() / quantity);
            if (uses > 0) {
                offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                        new ItemStack(item, quantity), uses, 0, PRICE_MULTIPLIER));
            }
        }
        return offers;
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }
}
