package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.economy.EconomySettings;
import io.github.nicolasgoyon.folkhold.core.sim.economy.ItemInfo;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobTrades;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Prices;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Stock;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.reputation.ReputationTier;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.ItemIds;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.trading.MerchantOffers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * The stay of a caravan at the trade place of its destination.
 *
 * <ul>
 *   <li>The stop of a caravan is in the trade place of the destination (see {@link VillageMarket#tradePlace}). At a
 *       village with a market, the stop is the caravan yard of the market piece. If the yard position is not free,
 *       the stop is the market sign. At a village with no market (a hamlet, or a village that waits for its market),
 *       the stop is the heart, in the middle of the plaza. If the heart is not loaded, the stop is the heart.</li>
 *   <li>The merchant walks to the stop and arrives near it (see {@link GroupMaterializer#isAtDestination}). During the
 *       stay, the merchant stays at the stop (see
 *       {@link io.github.nicolasgoyon.folkhold.world.entity.CaravanMerchant}).</li>
 *   <li>During the stay, the merchant trades at all the hours. It sells the surplus of the stock of the destination
 *       that no worker sells (see {@link JobTrades#surplus}): no output of an active job with a trader, and only the
 *       quantity above the target of the inputs of the active jobs and of the food. Thus it sells the goods that the
 *       caravans deliver and that no job of the village makes. The prices are the prices of the destination, the items leave its stock and the emeralds go into the
 *       treasury of its faction (see {@link Merchants#applyTrade}).</li>
 *   <li>The stop is not saved: the mod finds it again from the heart. A caravan of a saved world goes to the stop that
 *       the heart gives now.</li>
 * </ul>
 */
public final class CaravanStays {
    /** The position of the caravan yard in the market piece: the middle of the place of the mules. */
    static final BlockPos YARD = new BlockPos(2, 1, 2);
    /** The merchant stays at this distance or less from the caravan yard, in blocks. */
    public static final int YARD_RADIUS = 2;
    /** The merchant stays at this distance or less from the heart at the plaza, in blocks. */
    public static final int PLAZA_RADIUS = 6;
    /** The maximum number of offers of the merchant during the stay. */
    private static final int MAX_OFFERS = 6;
    /** The maximum number of uses of one offer. */
    private static final int MAX_USES = 16;
    /** The search for a stand position goes up to this number of blocks above the stop. */
    private static final int STAND_SEARCH = 4;
    /** Game ticks between two calculations of the stop of a group. */
    private static final long REFRESH_TICKS = 20;

    /** The stop of a caravan with entities, by group id, with the game time of its calculation. Not saved. */
    private static final Map<String, Cached> STOPS = new HashMap<>();

    private record Cached(Stop stop, long time) {
    }

    /**
     * The stop of a caravan.
     *
     * @param pos    the position
     * @param radius the merchant stays at this distance or less from the position
     * @param market true for a stop at the market
     */
    public record Stop(BlockPos pos, int radius, boolean market) {
    }

    private CaravanStays() {
    }

    /**
     * The stop of a group at its destination, if the heart of the destination is known. A traveling merchant stops at
     * the heart.
     *
     * @param level the level
     * @param group the group
     * @return the stop
     */
    public static Optional<Stop> stop(ServerLevel level, MobileGroup group) {
        Optional<BlockPos> heartPos = GroupMaterializer.destinationHeart(level.getServer(), group);
        if (heartPos.isEmpty()) {
            return Optional.empty();
        }
        if (group.kind() != GroupKind.CARAVAN) {
            return Optional.of(new Stop(heartPos.get(), PLAZA_RADIUS, false));
        }
        long now = level.getGameTime();
        Cached cached = STOPS.get(group.id());
        if (cached != null && now - cached.time() < REFRESH_TICKS && now >= cached.time()) {
            return Optional.of(cached.stop());
        }
        Stop stop = ReadyChunks.isReady(level, heartPos.get())
                && level.getBlockEntity(heartPos.get()) instanceof VillageHeartBlockEntity heart
                ? stopOf(level, heart) : new Stop(heartPos.get(), PLAZA_RADIUS, false);
        STOPS.put(group.id(), new Cached(stop, now));
        return Optional.of(stop);
    }

    /** Forgets the stop of a group: the group arrived or does not exist. */
    static void forget(String groupId) {
        STOPS.remove(groupId);
    }

    /** Forgets all the stops at the stop of the server. */
    static void clear() {
        STOPS.clear();
    }

    /**
     * The stop of the caravans at a village: the caravan yard of its market, else the market sign, else the heart.
     *
     * @param level the level
     * @param heart the village heart
     * @return the stop
     */
    public static Stop stopOf(ServerLevel level, VillageHeartBlockEntity heart) {
        VillageMarket.TradePlace place = heart.tradePlace(level);
        if (!place.market()) {
            return new Stop(heart.getBlockPos(), PLAZA_RADIUS, false);
        }
        return new Stop(yard(level, heart, place).orElse(place.point()), YARD_RADIUS, true);
    }

    /** The caravan yard of the market: from the plot of the market, in the area of the market, and free. */
    private static Optional<BlockPos> yard(ServerLevel level, VillageHeartBlockEntity heart,
            VillageMarket.TradePlace place) {
        Optional<VillageMarket.Plot> plot = heart.marketPlot();
        if (plot.isEmpty()) {
            return Optional.empty();
        }
        Optional<StructureTemplate> template = level.getStructureManager().get(plot.get().template());
        if (template.isEmpty()) {
            return Optional.empty();
        }
        Vec3i size = template.get().getSize();
        if (YARD.getX() >= size.getX() || YARD.getY() >= size.getY() || YARD.getZ() >= size.getZ()) {
            return Optional.empty();
        }
        BlockPos pos = StructureTemplate.calculateRelativePosition(
                GhostPlacement.settings(plot.get().rotation(), Mirror.NONE), YARD).offset(plot.get().origin());
        boolean free = ReadyChunks.isReady(level, pos)
                && isFree(level, pos) && isFree(level, pos.above())
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP);
        return free && place.area().isInside(pos) ? Optional.of(pos) : Optional.empty();
    }

    /**
     * A position where the merchant can stand at a stop: the first position from the stop up to
     * {@link #STAND_SEARCH} blocks above it with two free blocks and a solid block below them. For example, on the
     * heart at the plaza. Else the block above the stop.
     *
     * @param level the level
     * @param stop  the stop
     * @return the position of the feet
     */
    public static BlockPos standPos(Level level, BlockPos stop) {
        for (int dy = 0; dy <= STAND_SEARCH; dy++) {
            BlockPos pos = stop.above(dy);
            if (isFree(level, pos) && isFree(level, pos.above()) && !isFree(level, pos.below())) {
                return pos;
            }
        }
        return stop.above();
    }

    private static boolean isFree(Level level, BlockPos pos) {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty();
    }

    /**
     * The surplus of the stock of a settlement that no worker sells, with the quantity for sale (see
     * {@link JobTrades#surplus}).
     *
     * @param settlement the settlement
     * @return the quantity for sale, by item id, the most valuable surplus first
     */
    public static Map<String, Integer> surplus(Settlement settlement) {
        return JobTrades.surplus(Merchants.sellingJobs(settlement), Economies.activeJobs(settlement),
                settlement.economy().stock().asMap(), EconomyData.catalog(), settlement.economy().population(),
                id -> ItemIds.item(id).filter(item -> item != Items.EMERALD).isPresent());
    }

    /**
     * The offers of the merchant of a caravan during its stay: the surplus of the stock of the destination that no
     * worker sells, the most valuable surplus first.
     *
     * @param settlement the destination settlement
     * @param tier       the tier of the player
     * @return the offers
     */
    public static MerchantOffers offers(Settlement settlement, ReputationTier tier) {
        MerchantOffers offers = new MerchantOffers();
        if (!settlement.isActive()) {
            return offers;
        }
        Stock stock = settlement.economy().stock();
        int population = settlement.economy().population();
        EconomySettings settings = Economies.settings();
        double factor = Merchants.priceFactor(tier);
        for (Map.Entry<String, Integer> entry : surplus(settlement).entrySet()) {
            if (offers.size() >= MAX_OFFERS) {
                break;
            }
            String id = entry.getKey();
            ItemInfo info = EconomyData.catalog().info(id);
            double price = Prices.sellPrice(info, stock.count(id), population, settings) * factor;
            Merchants.sellOffer(ItemIds.item(id).orElseThrow(), entry.getValue(), price, MAX_USES)
                    .ifPresent(offers::add);
        }
        return offers;
    }
}
