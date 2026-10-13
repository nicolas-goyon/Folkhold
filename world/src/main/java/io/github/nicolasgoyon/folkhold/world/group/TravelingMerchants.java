package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.faction.Attitude;
import io.github.nicolasgoyon.folkhold.core.sim.faction.FactionSpec;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.ItemCost;
import net.minecraft.world.item.trading.MerchantOffer;
import net.minecraft.world.item.trading.MerchantOffers;

/**
 * The traveling merchants of the neutral faction. A
 * {@link io.github.nicolasgoyon.folkhold.world.settlement.WorldSystem} that runs on each abstract tick, after the
 * mobile groups.
 *
 * <ul>
 *   <li>A traveling merchant is a mobile group with a merchant and a mule. It goes from village to village. At the
 *       end of its route, it stays near the village for a time, then it goes to another village near it.</li>
 *   <li>It sells 6 rare items (see {@link #RARE_ITEMS} and {@link #itemsOf(MobileGroup)}): very expensive, in small
 *       quantities. At each cycle, it gets back one item of one offer (a slow restock).</li>
 *   <li>The world has a maximum number of traveling merchants. When it has fewer, a new one starts at a random
 *       active village.</li>
 *   <li>With no neutral faction, the world has no traveling merchant.</li>
 * </ul>
 */
public final class TravelingMerchants {
    /** Number of different rare items of one traveling merchant. */
    public static final int ITEM_KINDS = 6;
    /** Number of near villages from which the merchant selects its next village. */
    private static final int NEAR_VILLAGES = 5;
    private static final int MAX_USES = 4;
    private static final float PRICE_MULTIPLIER = 0.05F;

    /**
     * A rare item of the traveling merchants.
     *
     * @param item  the item id
     * @param price the price of one item in emeralds
     * @param max   the maximum quantity of the item for one merchant
     */
    public record RareItem(String item, int price, int max) {
    }

    /** The rare items. They are not in the jobs of the villages. No enchanted books. */
    public static final List<RareItem> RARE_ITEMS = List.of(
            new RareItem("minecraft:name_tag", 12, 2),
            new RareItem("minecraft:saddle", 10, 1),
            new RareItem("minecraft:ender_pearl", 6, 4),
            new RareItem("minecraft:blaze_rod", 8, 2),
            new RareItem("minecraft:nautilus_shell", 16, 1),
            new RareItem("minecraft:golden_apple", 14, 1),
            new RareItem("minecraft:diamond", 12, 2),
            new RareItem("minecraft:slime_ball", 4, 4),
            new RareItem("minecraft:glow_ink_sac", 4, 4),
            new RareItem("minecraft:amethyst_shard", 3, 8),
            new RareItem("minecraft:spyglass", 12, 1),
            new RareItem("minecraft:music_disc_cat", 20, 1),
            new RareItem("minecraft:cherry_sapling", 5, 2),
            new RareItem("minecraft:mangrove_propagule", 5, 2),
            new RareItem("minecraft:sea_pickle", 3, 4),
            new RareItem("minecraft:pointed_dripstone", 3, 4));

    private TravelingMerchants() {
    }

    /** The rare item with an id, if it is in the list. */
    public static Optional<RareItem> rareItem(String item) {
        return RARE_ITEMS.stream().filter(rare -> rare.item().equals(item)).findFirst();
    }

    /** Runs the traveling merchants for one abstract tick. */
    public static void tick(MinecraftServer server, TickInfo tick) {
        MobileGroupData data = MobileGroupData.get(server);
        SplittableRandom random = new SplittableRandom(server.overworld().getSeed() ^ tick.number());
        int count = 0;
        for (MobileGroup group : new ArrayList<>(data.groups().all())) {
            if (group.kind() != GroupKind.TRAVELING_MERCHANT) {
                continue;
            }
            count++;
            if (group.state() == GroupState.STOPPED && tick.number() >= group.departureTick()) {
                nextJourney(server, group, random);
            }
            if (tick.cycle()) {
                restock(group, random);
            }
        }
        if (count < WorldConfig.TRAVELING_MERCHANTS.get()
                && data.groups().size() < WorldConfig.GROUP_MAX_GROUPS.get()) {
            spawn(server, tick.number(), random);
        }
        data.setDirty();
    }

    /**
     * A traveling merchant at the end of its route stays near the village.
     *
     * @param group      the traveling merchant
     * @param tickNumber the number of the abstract tick
     */
    public static void stay(MobileGroup group, long tickNumber) {
        group.setState(GroupState.STOPPED);
        group.setDepartureTick(tickNumber + WorldConfig.TRAVELING_STAY_TICKS.get());
    }

    /**
     * Makes a new traveling merchant at a random active village, if the world has a neutral faction and a route
     * from this village to another village.
     *
     * @param server     the server
     * @param tickNumber the number of the abstract tick
     * @param random     the random source
     * @return the new traveling merchant, or an empty value
     */
    public static Optional<MobileGroup> spawn(MinecraftServer server, long tickNumber, SplittableRandom random) {
        Optional<FactionSpec> neutral = FactionData.get(server).factions().stream()
                .filter(faction -> faction.attitude() == Attitude.NEUTRAL).findFirst();
        List<Settlement> active = SettlementData.get(server).registry().all().stream()
                .filter(Settlement::isActive).toList();
        if (neutral.isEmpty() || active.size() < 2) {
            return Optional.empty();
        }
        Settlement origin = active.get(random.nextInt(active.size()));
        Optional<Settlement> destination = nextVillage(server, origin, random);
        if (destination.isEmpty()) {
            return Optional.empty();
        }
        Optional<Route> route = CaravanRoutes.route(server, origin, destination.get());
        if (route.isEmpty()) {
            return Optional.empty();
        }
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.TRAVELING_MERCHANT,
                neutral.get().id(), origin.id(), destination.get().id(), route.get());
        group.setDepartureTick(tickNumber + 1);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, Caravans.MERCHANT_HEALTH));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, Caravans.MULE_HEALTH));
        for (RareItem rare : itemsOf(group)) {
            group.cargo().set(rare.item(), rare.max());
        }
        MobileGroupData.get(server).groups().add(group);
        return Optional.of(group);
    }

    /**
     * The offers of a traveling merchant for a player: one rare item for each offer.
     *
     * @param group the traveling merchant
     * @param tier  the reputation tier of the player
     * @return the offers
     */
    public static MerchantOffers offers(MobileGroup group, ReputationTier tier) {
        MerchantOffers offers = new MerchantOffers();
        double factor = Merchants.priceFactor(tier);
        for (Map.Entry<String, Integer> entry : group.cargo().asMap().entrySet()) {
            Optional<RareItem> rare = rareItem(entry.getKey());
            Optional<Item> item = item(entry.getKey());
            if (rare.isEmpty() || item.isEmpty()) {
                continue;
            }
            int emeralds = (int) Math.max(1, Math.min(64, Math.round(rare.get().price() * factor)));
            int uses = Math.min(MAX_USES, entry.getValue());
            offers.add(new MerchantOffer(new ItemCost(Items.EMERALD, emeralds), Optional.empty(),
                    new ItemStack(item.get()), uses, 0, PRICE_MULTIPLIER));
        }
        return offers;
    }

    /** The traveling merchant goes from the village where it stays to another village near it. */
    private static void nextJourney(MinecraftServer server, MobileGroup group, SplittableRandom random) {
        SettlementData settlements = SettlementData.get(server);
        Optional<Settlement> here = settlements.registry().byId(group.destinationId());
        if (here.isEmpty()) {
            return;
        }
        Optional<Settlement> next = nextVillage(server, here.get(), random);
        Optional<Route> route = next.flatMap(settlement -> CaravanRoutes.route(server, here.get(), settlement));
        if (route.isEmpty()) {
            group.setDepartureTick(group.departureTick() + WorldConfig.TRAVELING_STAY_TICKS.get());
            return;
        }
        group.setJourney(here.get().id(), next.get().id(), route.get());
        group.setState(GroupState.TRAVELING);
    }

    /** A random active village among the villages nearest to a village, in the maximum route length. */
    private static Optional<Settlement> nextVillage(MinecraftServer server, Settlement from, SplittableRandom random) {
        long maxLength = WorldConfig.ROUTE_MAX_LENGTH.get();
        List<Settlement> near = SettlementData.get(server).registry().all().stream()
                .filter(settlement -> settlement.isActive() && !settlement.id().equals(from.id())
                        && settlement.distanceSquared(from.x(), from.z()) <= maxLength * maxLength)
                .sorted(Comparator.comparingLong(settlement -> settlement.distanceSquared(from.x(), from.z())))
                .limit(NEAR_VILLAGES)
                .toList();
        return near.isEmpty() ? Optional.empty() : Optional.of(near.get(random.nextInt(near.size())));
    }

    /**
     * The rare items of a traveling merchant. The id of the group selects them, so they do not change.
     *
     * @param group the traveling merchant
     * @return the items
     */
    public static List<RareItem> itemsOf(MobileGroup group) {
        SplittableRandom random = new SplittableRandom(group.id().hashCode());
        List<RareItem> items = new ArrayList<>(RARE_ITEMS);
        List<RareItem> result = new ArrayList<>();
        for (int i = 0; i < ITEM_KINDS && !items.isEmpty(); i++) {
            result.add(items.remove(random.nextInt(items.size())));
        }
        return result;
    }

    /**
     * One item of one offer comes back, up to the maximum of the item. A traveling merchant with entities has its
     * items in the mule chest, so it gets no restock.
     */
    public static void restock(MobileGroup group, SplittableRandom random) {
        if (GroupMaterializer.isMaterialized(group)) {
            return;
        }
        List<RareItem> missing = new ArrayList<>();
        for (RareItem rare : itemsOf(group)) {
            if (group.cargo().count(rare.item()) < rare.max()) {
                missing.add(rare);
            }
        }
        if (!missing.isEmpty()) {
            RareItem rare = missing.get(random.nextInt(missing.size()));
            group.cargo().add(rare.item(), 1, rare.max());
        }
    }

    private static Optional<Item> item(String id) {
        ResourceLocation location = ResourceLocation.tryParse(id);
        return location == null ? Optional.empty() : BuiltInRegistries.ITEM.getOptional(location);
    }
}
