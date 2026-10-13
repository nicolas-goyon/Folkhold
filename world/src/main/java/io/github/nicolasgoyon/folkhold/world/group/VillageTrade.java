package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemCatalog;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Job;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Recipe;
import io.github.nicolasgoyon.folkhold.core.sim.faction.RelationState;
import io.github.nicolasgoyon.folkhold.core.sim.faction.Relations;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroups;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher.Order;
import io.github.nicolasgoyon.folkhold.core.sim.trade.TradeMatcher.Participant;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.faction.FactionData;
import io.github.nicolasgoyon.folkhold.world.quest.Escorts;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.server.MinecraftServer;

/**
 * The trade between villages. A {@link io.github.nicolasgoyon.folkhold.world.settlement.WorldSystem} that runs on
 * each abstract tick, after the mobile groups.
 *
 * <ol>
 *   <li>Each active settlement with an economy lists its surpluses and its needs (see {@link TradeMatcher}). A
 *       settlement uses the inputs of its jobs and the food.</li>
 *   <li>The system requests a small number of new routes between possible partners, the nearest pairs first. A
 *       worker thread calculates them (see {@link CaravanRoutes#request}). The trade uses a route at the next
 *       abstract tick after its calculation.</li>
 *   <li>Two settlements can trade if their factions are not at war, if a route connects them, and if no caravan of
 *       the seller is on the way to the buyer.</li>
 *   <li>Each order becomes a caravan. The goods leave the stock of the seller. The caravan leaves at the next
 *       abstract tick, or later if it waits for an escort (see {@link Escorts#offer}).</li>
 *   <li>At the arrival, the buyer pays the value of the goods to the treasury of the seller, and the relation
 *       between the two factions goes up (see {@link #onArrival(MinecraftServer, MobileGroup)}).</li>
 * </ol>
 */
public final class VillageTrade {
    /** The distance in blocks that divides the score of a trade by 2. */
    private static final double DISTANCE_WEIGHT = 500.0;

    private VillageTrade() {
    }

    /** Runs the trade for one abstract tick, between all active settlements. */
    public static void tick(MinecraftServer server, TickInfo tick) {
        trade(server, tick.number(), SettlementData.get(server).registry().all());
    }

    /**
     * Runs the trade between some settlements.
     *
     * @param server     the server
     * @param tickNumber the number of the abstract tick
     * @param candidates the settlements. Only the active settlements with an economy trade.
     * @return the new caravans
     */
    public static List<MobileGroup> trade(MinecraftServer server, long tickNumber,
            Collection<Settlement> candidates) {
        List<MobileGroup> created = new ArrayList<>();
        if (!EconomyData.isActive()) {
            return created;
        }
        MobileGroupData groupData = MobileGroupData.get(server);
        int free = WorldConfig.GROUP_MAX_GROUPS.get() - groupData.groups().size();
        int maxOrders = Math.min(free, WorldConfig.TRADE_CARAVANS_PER_TICK.get());
        if (maxOrders <= 0) {
            return created;
        }
        SettlementData settlementData = SettlementData.get(server);
        Map<String, Settlement> settlements = new LinkedHashMap<>();
        for (Settlement settlement : candidates) {
            if (settlement.isActive() && settlement.economy().isInitialized()) {
                settlements.put(settlement.id(), settlement);
            }
        }
        if (settlements.size() < 2) {
            return created;
        }
        ItemCatalog catalog = EconomyData.catalog();
        List<Participant> participants = new ArrayList<>();
        for (Settlement settlement : settlements.values()) {
            int population = settlement.economy().population();
            participants.add(new Participant(settlement.id(), settlement.factionId(), settlement.x(), settlement.z(),
                    TradeMatcher.surpluses(settlement.economy().stock(), population, catalog),
                    TradeMatcher.needs(settlement.economy().stock(), population, catalog,
                            usedItems(settlement, catalog))));
        }
        Relations relations = FactionData.get(server).relations();
        calculateRoutes(server, participants, settlements, relations);

        MobileGroups groups = groupData.groups();
        TradeMatcher.PairFilter filter = (seller, buyer) -> canTrade(server, settlements.get(seller.id()),
                settlements.get(buyer.id()), relations, groups);
        TradeMatcher.Pricing pricing = (sellerId, item) -> {
            Settlement seller = settlements.get(sellerId);
            return Prices.price(catalog.info(item), seller.economy().stock().count(item),
                    seller.economy().population(), Economies.settings());
        };
        List<Order> orders = TradeMatcher.match(participants, filter, pricing, new TradeMatcher.Settings(maxOrders,
                WorldConfig.TRADE_CARAVAN_CAPACITY.get(), DISTANCE_WEIGHT));
        for (Order order : orders) {
            Settlement seller = settlements.get(order.sellerId());
            Settlement buyer = settlements.get(order.buyerId());
            Optional<Route> route = CaravanRoutes.cached(server, seller, buyer);
            if (route == null || route.isEmpty()) {
                continue;
            }
            long payment = Math.max(1, Math.round(order.value()));
            if (settlementData.treasury(buyer.factionId()) < payment) {
                continue;
            }
            Map<String, Integer> cargo = new LinkedHashMap<>();
            order.items().forEach((item, count) -> {
                int removed = seller.economy().stock().remove(item, count);
                if (removed > 0) {
                    cargo.put(item, removed);
                }
            });
            if (cargo.isEmpty()) {
                continue;
            }
            MobileGroup caravan = Caravans.create(seller, buyer, route.get(), cargo, payment, tickNumber + 1);
            groups.add(caravan);
            Escorts.offer(server, caravan, seller, tickNumber);
            created.add(caravan);
        }
        if (!created.isEmpty()) {
            settlementData.setDirty();
            groupData.setDirty();
        }
        return created;
    }

    /**
     * Pays the seller and raises the relation between the two factions. The {@link MobileGroupSystem} calls this
     * method when a caravan arrives, after the delivery of the cargo.
     *
     * @param server  the server
     * @param caravan the caravan
     */
    public static void onArrival(MinecraftServer server, MobileGroup caravan) {
        SettlementData settlementData = SettlementData.get(server);
        Optional<Settlement> buyer = settlementData.registry().byId(caravan.destinationId())
                .filter(Settlement::isActive);
        if (buyer.isEmpty()) {
            return;
        }
        String sellerFaction = caravan.factionId();
        String buyerFaction = buyer.get().factionId();
        if (sellerFaction.equals(buyerFaction)) {
            return;
        }
        long payment = Math.min(caravan.payment(), settlementData.treasury(buyerFaction));
        if (payment > 0) {
            settlementData.addTreasury(buyerFaction, -payment);
            settlementData.addTreasury(sellerFaction, payment);
        }
        FactionData factions = FactionData.get(server);
        factions.relations().change(sellerFaction, buyerFaction, WorldConfig.TRADE_RELATION_GAIN.get());
        factions.setDirty();
    }

    /** The items that a settlement uses: the inputs of its jobs and the food. */
    public static Set<String> usedItems(Settlement settlement, ItemCatalog catalog) {
        Set<String> items = new LinkedHashSet<>();
        for (Job job : Economies.jobs(settlement)) {
            for (Recipe.Input input : job.recipe().inputs()) {
                items.add(input.item());
            }
        }
        for (ItemInfo info : catalog.all()) {
            if (info.isFood()) {
                items.add(info.id());
            }
        }
        return items;
    }

    /** True if two settlements can trade now. The route must be in the data. */
    private static boolean canTrade(MinecraftServer server, Settlement seller, Settlement buyer, Relations relations,
            MobileGroups groups) {
        if (relations.state(seller.factionId(), buyer.factionId()) == RelationState.WAR) {
            return false;
        }
        for (MobileGroup group : groups.bySettlement(seller.id())) {
            if (group.originId().equals(seller.id()) && group.destinationId().equals(buyer.id())) {
                return false;
            }
        }
        Optional<Route> route = CaravanRoutes.cached(server, seller, buyer);
        return route != null && route.isPresent();
    }

    /**
     * Requests the routes that are not in the data, for the pairs that could trade: a surplus of one is a need of
     * the other, the factions are not at war, and the direct distance is below the maximum route length. The
     * nearest pairs come first. The number of routes on the way stays below the configured number.
     */
    private static void calculateRoutes(MinecraftServer server, List<Participant> participants,
            Map<String, Settlement> settlements, Relations relations) {
        int budget = WorldConfig.TRADE_ROUTES_PER_TICK.get();
        if (budget <= 0) {
            return;
        }
        double maxLength = WorldConfig.ROUTE_MAX_LENGTH.get();
        List<Settlement[]> missing = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Participant seller : participants) {
            for (Participant buyer : participants) {
                if (seller == buyer || seller.surpluses().keySet().stream().noneMatch(buyer.needs()::containsKey)) {
                    continue;
                }
                String key = seller.id().compareTo(buyer.id()) < 0 ? seller.id() + "|" + buyer.id()
                        : buyer.id() + "|" + seller.id();
                Settlement from = settlements.get(seller.id());
                Settlement to = settlements.get(buyer.id());
                if (!seen.add(key) || Math.sqrt(from.distanceSquared(to.x(), to.z())) > maxLength
                        || relations.state(from.factionId(), to.factionId()) == RelationState.WAR
                        || CaravanRoutes.cached(server, from, to) != null) {
                    continue;
                }
                missing.add(new Settlement[] {from, to});
            }
        }
        Map<Settlement[], Long> distances = new HashMap<>();
        missing.forEach(pair -> distances.put(pair, pair[0].distanceSquared(pair[1].x(), pair[1].z())));
        missing.sort(Comparator.comparingLong(distances::get));
        int requests = Math.min(budget - CaravanRoutes.pending(), missing.size());
        for (int i = 0; i < requests; i++) {
            CaravanRoutes.request(server, missing.get(i)[0], missing.get(i)[1]);
        }
    }
}
