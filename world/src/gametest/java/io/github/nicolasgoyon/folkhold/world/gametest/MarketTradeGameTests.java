package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobTrades;
import io.github.nicolasgoyon.folkhold.core.sim.schedule.DaySchedule;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.faction.ReputationData;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import io.github.nicolasgoyon.folkhold.world.village.schedule.VillageSchedule;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the market rule of the workers: a worker trades only at the trade place of its village during the
 * market hours, and has extra offers on the market day of the village.
 *
 * <p>The day time applies to the full level. Each test sets a day time and the old day time again in the same tick:
 * the other tests of the batch do not see the change.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketTradeGameTests {
    private static final int SIZE = 32;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** The origin of the market piece: the street side (z = 0 of the piece) is to the north. */
    private static final BlockPos MARKET_ORIGIN = new BlockPos(14, 1, 18);
    /** A place out of the market and of the plaza, for example the building of the job of a worker. */
    private static final BlockPos AWAY = new BlockPos(28, 1, 2);
    /** A place far from the plaza, from the market and from {@link #AWAY}. */
    private static final BlockPos FAR = new BlockPos(2, 1, 28);
    private static final ResourceLocation MARKET = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/market");
    private static final ResourceLocation HOMES = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/homes");
    private static final String BAKER = "folkhold_world:baker";
    private static final String MINER = "folkhold_world:miner";
    /** A day time in the work phase of the default schedule. */
    private static final long WORK_TIME = 3000;
    /** A day time in the market phase of the default schedule. */
    private static final long MARKET_TIME = 6000;
    /** The treasury of the faction in the tests of the offers: the workers can pay their buy offers. */
    private static final long TREASURY = 1000;

    private MarketTradeGameTests() {
    }

    /** A village heart with its settlement at level 1. The end of the test removes it, also after a failure. */
    private static VillageHeartBlockEntity village(GameTestHelper helper) {
        TestSupport.setConfigForTest(helper, WorldConfig.CONSTRUCTION_GROWTH, false);
        TestSupport.buildFloor(helper, SIZE);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        // A known level: a hamlet with 3 offers, so a single trader has 3 offers and 5 on the market day.
        settlement.setProgress(1, 0);
        settlement.economy().setPopulation(10);
        TestSupport.emptyStock(settlement);
        TestSupport.atTestEnd(helper, () -> {
            VillageFolk.inhabitants(helper.getLevel(), heart.getBlockPos()).forEach(Entity::discard);
            VillageRepairData.get(helper.getLevel()).forget(heart.getBlockPos());
            settlement.setState(SettlementState.RUINS);
            SettlementData.get(helper.getLevel().getServer()).setDirty();
            helper.getLevel().setBlock(heart.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        });
        return heart;
    }

    /** A mock player with a neutral reputation in the village: the workers trade with it. */
    private static ServerPlayer neutralPlayer(GameTestHelper helper, Settlement settlement) {
        ServerPlayer player = TestSupport.mockPlayer(helper);
        ReputationData reputation = ReputationData.get(helper.getLevel().getServer());
        reputation.get(player.getUUID()).setFaction(settlement.factionId(), 0);
        reputation.get(player.getUUID()).setVillage(settlement.id(), 0);
        return player;
    }

    /** A worker of the village at a position of the test. */
    private static Villager worker(GameTestHelper helper, VillageHeartBlockEntity heart,
            VillagerProfession profession, BlockPos absolute) {
        Villager worker = VillageFolk.spawn(helper.getLevel(), heart.getBlockPos(), profession);
        worker.moveTo(absolute.getX() + 0.5, absolute.getY(), absolute.getZ() + 0.5);
        return worker;
    }

    /**
     * Places a bell with its point of interest. The level adds the point of interest of a new block after the tick:
     * the test adds it at once (a second add of the same type does nothing).
     */
    private static void placeBell(ServerLevel level, BlockPos pos) {
        level.setBlockAndUpdate(pos, Blocks.BELL.defaultBlockState());
        level.getPoiManager().add(pos, BuiltInRegistries.POINT_OF_INTEREST_TYPE.getHolderOrThrow(PoiTypes.MEETING));
    }

    /**
     * Scans the blocks of the arena only, with no record of the job posts: the blocks of the other tests do not change
     * the bells of the village.
     */
    private static void scanArena(GameTestHelper helper, VillageHeartBlockEntity heart) {
        BlockPos min = helper.absolutePos(BlockPos.ZERO);
        BlockPos max = helper.absolutePos(new BlockPos(SIZE - 1, 8, SIZE - 1));
        heart.getLayout().scan(helper.getLevel(), BoundingBox.fromCorners(min, max));
    }

    /** The player starts to trade with a worker: true if the worker accepts. */
    private static boolean interact(ServerPlayer player, Villager worker) {
        PlayerInteractEvent.EntityInteract event =
                new PlayerInteractEvent.EntityInteract(player, InteractionHand.MAIN_HAND, worker);
        NeoForge.EVENT_BUS.post(event);
        return !event.isCanceled();
    }

    /** The first market day of a settlement from a day number, at a time of the day. */
    private static long marketDay(GameTestHelper helper, Settlement settlement, long from) {
        ServerLevel level = helper.getLevel();
        for (long day = from; day < from + VillageSchedule.schedule().marketDayPeriod(); day++) {
            level.setDayTime(day * DaySchedule.DAY_TICKS);
            if (VillageSchedule.isMarketDay(level, settlement)) {
                return day;
            }
        }
        helper.fail("No market day in one period");
        return from;
    }

    /**
     * The miner job with its outputs in stock, and a treasury that pays the buy offers: the miner can fill more offers
     * than its share. The end of the test puts the treasury back.
     */
    private static void minerStock(GameTestHelper helper, Settlement settlement) {
        SettlementData data = SettlementData.get(helper.getLevel().getServer());
        String faction = settlement.factionId();
        long original = data.treasury(faction);
        TestSupport.atTestEnd(helper, () -> data.addTreasury(faction, original - data.treasury(faction)));
        data.addTreasury(faction, TREASURY - original);
        settlement.economy().setJobs(List.of(MINER));
        for (String item : List.of("minecraft:cobblestone", "minecraft:raw_iron", "minecraft:raw_copper",
                "minecraft:coal", "minecraft:flint", "minecraft:redstone", "minecraft:lapis_lazuli",
                "minecraft:raw_gold", "minecraft:diamond")) {
            settlement.economy().stock().set(item, 64);
        }
    }

    /**
     * A village with a market: a baker at the building of its job refuses the trade during the market hours. At the
     * market, the baker refuses the trade out of the market hours, and accepts it during the market hours. The
     * interactions do not calculate the trade place again.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_trade_place")
    public static void workerTradesOnlyAtTheMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.economy().setJobs(List.of(BAKER));
        settlement.economy().stock().set("minecraft:bread", 64);
        BlockPos origin = helper.absolutePos(MARKET_ORIGIN);
        StructureTemplate template = level.getStructureManager().get(MARKET).orElseThrow();
        template.placeInWorld(level, origin, origin, GhostPlacement.settings(Rotation.NONE, Mirror.NONE)
                .addProcessor(JigsawReplacementProcessor.INSTANCE), level.getRandom(), Block.UPDATE_CLIENTS);
        heart.setRepairReference(new VillageReference(List.of(new VillageReference.Piece(MARKET, origin,
                Rotation.NONE)), List.of(), Optional.empty(), List.of(), Optional.of(HOMES)));
        // A scan with no record of the job posts: the village keeps the job of its settlement.
        heart.getLayout().scan(level, heart.scanArea(level));
        VillageMarket.TradePlace place = heart.tradePlace(level);
        helper.assertTrue(place.market(), "The village must trade at its market: " + place);
        int calculations = heart.tradePlaceCalculations();

        ServerPlayer player = neutralPlayer(helper, settlement);
        Villager atMarket = worker(helper, heart, ModProfessions.BAKER.get(), place.point());
        Villager atWork = worker(helper, heart, ModProfessions.BAKER.get(), helper.absolutePos(AWAY));
        heart.updateWorkers();
        helper.assertTrue(heart.workers().validMeetingPoint(level).map(point -> point.pos().equals(place.point()))
                .orElse(false), "The meeting point must be the market sign");
        long oldTime = level.getDayTime();
        try {
            level.setDayTime(WORK_TIME);
            helper.assertTrue(Merchants.marketRefusal(level, heart, atMarket) == Merchants.MarketRefusal.CLOSED
                    && !interact(player, atMarket), "The baker must refuse the trade out of the market hours");

            level.setDayTime(MARKET_TIME);
            helper.assertTrue(Merchants.marketRefusal(level, heart, atWork) == Merchants.MarketRefusal.AWAY
                    && !interact(player, atWork), "The baker must refuse the trade at the building of its job");

            helper.assertTrue(Merchants.marketRefusal(level, heart, atMarket) == Merchants.MarketRefusal.NONE
                    && interact(player, atMarket), "The baker must trade at the market during the market hours");
            helper.assertTrue(atMarket.getOffers().stream().anyMatch(offer -> offer.getResult().is(Items.BREAD)),
                    "The baker at the market must sell the bread: " + atMarket.getOffers());
        } finally {
            level.setDayTime(oldTime);
        }
        helper.assertTrue(heart.tradePlaceCalculations() == calculations,
                "The trades must use the kept trade place: " + (heart.tradePlaceCalculations() - calculations)
                        + " new calculations");
        heart.requestScan();
        heart.tradePlace(level);
        helper.assertTrue(heart.tradePlaceCalculations() == calculations + 1,
                "A request for a scan must calculate the trade place again");
        helper.succeed();
    }

    /**
     * A hamlet has no market: a baker trades at the plaza during the market hours, and refuses the trade far from the
     * plaza.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_trade_plaza")
    public static void hamletTradesAtThePlaza(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.economy().setJobs(List.of(BAKER));
        settlement.economy().stock().set("minecraft:bread", 64);
        BlockPos bell = heart.getBlockPos().offset(3, 0, 0);
        placeBell(level, bell);
        scanArena(helper, heart);
        VillageMarket.TradePlace place = heart.tradePlace(level);
        helper.assertTrue(settlement.level() < 5 && heart.marketState(level) == VillageMarket.State.HAMLET,
                "The village must be a hamlet: level " + settlement.level() + ", " + heart.marketState(level));
        helper.assertTrue(!place.market() && place.point().equals(heart.getBlockPos()),
                "The hamlet must trade at the plaza: " + place);

        ServerPlayer player = neutralPlayer(helper, settlement);
        Villager atPlaza = worker(helper, heart, ModProfessions.BAKER.get(), heart.getBlockPos().offset(2, 0, 1));
        Villager away = worker(helper, heart, ModProfessions.BAKER.get(), helper.absolutePos(AWAY));
        heart.updateWorkers();
        helper.assertTrue(heart.workers().validMeetingPoint(level).map(point -> point.pos().equals(bell))
                .orElse(false), "The meeting point must be the bell of the plaza");
        long oldTime = level.getDayTime();
        try {
            level.setDayTime(MARKET_TIME);
            helper.assertTrue(interact(player, atPlaza), "The baker must trade at the plaza");
            helper.assertTrue(Merchants.marketRefusal(level, heart, away) == Merchants.MarketRefusal.AWAY
                    && !interact(player, away), "The baker must refuse the trade far from the plaza");
        } finally {
            level.setDayTime(oldTime);
        }
        helper.succeed();
    }

    /**
     * A hamlet with no bell in the area of its plaza: the meeting point of the workers is the bell nearest to the heart,
     * out of the plaza. A baker at this bell trades, a baker at the plaza trades, and a baker far from the two places
     * refuses.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_trade_far_bell")
    public static void workerTradesAtAMeetingPointOutOfThePlaza(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.economy().setJobs(List.of(BAKER));
        settlement.economy().stock().set("minecraft:bread", 64);
        BlockPos bell = helper.absolutePos(AWAY);
        placeBell(level, bell);
        scanArena(helper, heart);
        VillageMarket.TradePlace place = heart.tradePlace(level);
        helper.assertTrue(!place.market() && !place.area().inflatedBy(Merchants.MARKET_MARGIN).isInside(bell),
                "The bell must be out of the plaza: " + place);

        ServerPlayer player = neutralPlayer(helper, settlement);
        Villager atBell = worker(helper, heart, ModProfessions.BAKER.get(), bell.offset(1, 0, 1));
        Villager atPlaza = worker(helper, heart, ModProfessions.BAKER.get(), heart.getBlockPos().offset(2, 0, 1));
        Villager far = worker(helper, heart, ModProfessions.BAKER.get(), helper.absolutePos(FAR));
        heart.updateWorkers();
        helper.assertTrue(heart.workers().validMeetingPoint(level).map(point -> point.pos().equals(bell))
                .orElse(false), "The meeting point must be the bell out of the plaza");
        helper.assertTrue(atBell.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                .map(point -> point.pos().equals(bell)).orElse(false), "The worker must go to the bell");
        long oldTime = level.getDayTime();
        try {
            level.setDayTime(MARKET_TIME);
            helper.assertTrue(interact(player, atBell), "The baker must trade at the meeting point");
            helper.assertTrue(interact(player, atPlaza), "The baker must trade at the plaza");
            helper.assertTrue(Merchants.marketRefusal(level, heart, far) == Merchants.MarketRefusal.AWAY
                    && !interact(player, far), "The baker must refuse the trade far from the two places");
        } finally {
            level.setDayTime(oldTime);
        }
        helper.succeed();
    }

    /**
     * A hamlet with no bell has no meeting point: only the market hours apply. A baker far from the plaza trades during
     * the market hours, and refuses the trade out of the market hours.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_trade_no_meeting")
    public static void noMeetingPointKeepsOnlyTheHours(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.economy().setJobs(List.of(BAKER));
        settlement.economy().stock().set("minecraft:bread", 64);
        scanArena(helper, heart);
        ServerPlayer player = neutralPlayer(helper, settlement);
        Villager far = worker(helper, heart, ModProfessions.BAKER.get(), helper.absolutePos(FAR));
        heart.updateWorkers();
        helper.assertTrue(heart.getLayout().bells().isEmpty()
                && heart.workers().validMeetingPoint(level).isEmpty(), "The village must have no meeting point");
        long oldTime = level.getDayTime();
        try {
            level.setDayTime(MARKET_TIME);
            helper.assertTrue(interact(player, far), "With no meeting point, the baker must trade at the market hours");
            level.setDayTime(WORK_TIME);
            helper.assertTrue(Merchants.marketRefusal(level, heart, far) == Merchants.MarketRefusal.CLOSED
                    && !interact(player, far), "The baker must refuse the trade out of the market hours");
        } finally {
            level.setDayTime(oldTime);
        }
        helper.succeed();
    }

    /**
     * On the market day of the village, the miner has the extra offers of the schedule. The next day, it has the
     * offers of its share again.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_trade_day")
    public static void marketDayGivesExtraOffers(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        minerStock(helper, settlement);
        ServerPlayer player = neutralPlayer(helper, settlement);
        Villager miner = worker(helper, heart, VillagerProfession.TOOLSMITH, heart.getBlockPos().offset(1, 0, 2));
        int extra = VillageSchedule.schedule().marketDayOffers();
        int share = Merchants.offerCount(settlement, VillagerProfession.TOOLSMITH, 0);
        long oldTime = level.getDayTime();
        int marketDayOffers;
        int nextDayOffers;
        int marketDayExtra;
        int nextDayExtra;
        try {
            long day = marketDay(helper, settlement, 300);
            level.setDayTime(day * DaySchedule.DAY_TICKS + MARKET_TIME);
            marketDayExtra = VillageSchedule.extraOffers(level, settlement);
            helper.assertTrue(interact(player, miner), "The miner must trade at the plaza on the market day");
            marketDayOffers = miner.getOffers().size();

            level.setDayTime((day + 1) * DaySchedule.DAY_TICKS + MARKET_TIME);
            nextDayExtra = VillageSchedule.extraOffers(level, settlement);
            helper.assertTrue(interact(player, miner), "The miner must trade at the plaza the next day");
            nextDayOffers = miner.getOffers().size();
        } finally {
            level.setDayTime(oldTime);
        }
        helper.assertTrue(extra == 2 && marketDayExtra == extra && nextDayExtra == 0,
                "Expected 2 extra offers on the market day only: " + marketDayExtra + ", then " + nextDayExtra);
        helper.assertTrue(marketDayOffers == share + extra,
                "The miner must have " + (share + extra) + " offers on the market day, found " + marketDayOffers);
        helper.assertTrue(nextDayOffers == share,
                "The miner must have " + share + " offers after the market day, found " + nextDayOffers);
        helper.succeed();
    }

    /**
     * A large number of extra offers in the config: on the market day, a trader has {@link JobTrades#MAX_OFFERS}
     * offers or fewer. The config value applies to the full server, so the test has its own batch.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_trade_cap")
    public static void extraOffersStopAtTheCap(GameTestHelper helper) {
        TestSupport.setConfigForTest(helper, WorldConfig.SCHEDULE_MARKET_DAY_OFFERS, 64);
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        minerStock(helper, settlement);
        ServerPlayer player = neutralPlayer(helper, settlement);
        Villager miner = worker(helper, heart, VillagerProfession.TOOLSMITH, heart.getBlockPos().offset(1, 0, 2));
        long oldTime = level.getDayTime();
        int extra;
        int count;
        int offers;
        try {
            long day = marketDay(helper, settlement, 300);
            level.setDayTime(day * DaySchedule.DAY_TICKS + MARKET_TIME);
            extra = VillageSchedule.extraOffers(level, settlement);
            count = Merchants.offerCount(settlement, VillagerProfession.TOOLSMITH, extra);
            helper.assertTrue(interact(player, miner), "The miner must trade on the market day");
            offers = miner.getOffers().size();
        } finally {
            level.setDayTime(oldTime);
        }
        helper.assertTrue(extra == 64, "Expected 64 extra offers from the config, found " + extra);
        helper.assertTrue(count == JobTrades.MAX_OFFERS,
                "The offer count must stop at " + JobTrades.MAX_OFFERS + ", found " + count);
        helper.assertTrue(offers > Merchants.offerCount(settlement, VillagerProfession.TOOLSMITH, 0)
                && offers <= JobTrades.MAX_OFFERS, "The miner must have more offers, up to the cap: " + offers);
        helper.succeed();
    }
}
