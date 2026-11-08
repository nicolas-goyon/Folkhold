package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillageHearts;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the market of a village (see {@link VillageMarket}): from the level 5, the growth builds one market
 * on a free plot, and the heart finds its area. A hamlet trades at the plaza. With no free plot, the village has no
 * market.
 * See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MarketGrowthGameTests {
    private static final int SIZE = 32;
    /** The size of the arena of the construction of the market. */
    private static final int HUGE = 56;
    /** The builders of the test, in addition to the builders of the garrison. */
    private static final int BUILDERS = 2;
    /** The quantity of each delivered material in the stock. */
    private static final int MATERIALS = 64;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** The connector of the plot, in the street: the plot is to the east of it. */
    private static final BlockPos CONNECTOR = new BlockPos(16, 1, 16);
    /** The connector of the second plot, farther from the heart. */
    private static final BlockPos SECOND_CONNECTOR = new BlockPos(36, 1, 16);
    private static final ResourceLocation HOUSE_FRONT =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "house_front");
    private static final ResourceLocation HOMES = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/homes");
    private static final ResourceLocation MARKET = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/market");
    private static final ResourceLocation COTTAGE = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/house_cottage");
    private static final ResourceLocation PLAZA = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/plaza");

    private MarketGrowthGameTests() {
    }

    /**
     * A village of the level 5 with 2 free plots builds its market before a new home, on the nearest plot. The builders
     * build the market to the end. Then the heart finds it: the trade place is the market, and its area is the box of
     * the market piece. The village keeps one market: the next check starts a home on the second plot.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 24000, batch = "market_growth", skyAccess = true)
    public static void buildersBuildTheMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(1);
        VillageHeartBlockEntity heart = village(helper, HUGE, List.of(
                new VillagePlots.Plot(helper.absolutePos(CONNECTOR), Direction.EAST, HOUSE_FRONT, false),
                new VillagePlots.Plot(helper.absolutePos(SECOND_CONNECTOR), Direction.EAST, HOUSE_FRONT, false)));
        BlockPos heartPos = heart.getBlockPos();
        VillageHearts.register(level, heartPos);
        for (int i = 0; i < BUILDERS; i++) {
            helper.assertTrue(VillageFolk.spawn(level, heartPos, ModProfessions.BUILDER.get()) != null,
                    "The builder must spawn");
        }
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.setProgress(5, 0);
        settlement.economy().setPopulation(20);
        // The construction is longer than a day: the test stops the cycle of the day, else the builders stop at night.
        GameRules.BooleanValue daylight = level.getGameRules().getRule(GameRules.RULE_DAYLIGHT);
        boolean cycle = daylight.get();
        daylight.set(false, level.getServer());
        level.setDayTime(TestSupport.NOON);

        Optional<BuilderSite> site = VillageGrowth.check(level, heart);
        helper.assertTrue(site.isPresent(), "A village of the level 5 must start its market on a free plot");
        helper.assertTrue(site.get().template().equals(MARKET), "The market must come before the home: "
                + site.get().template());
        helper.assertTrue(heart.marketState(level) == VillageMarket.State.BUILDING,
                "Expected the state BUILDING, found " + heart.marketState(level));
        helper.assertFalse(heart.tradePlace(level).market(), "Before its market stands, the village trades at the plaza");
        StructureTemplate template = level.getStructureManager().get(MARKET).orElseThrow();
        BoundingBox box = GhostPlacement.box(template.getSize(), site.get().rotation(), Mirror.NONE,
                site.get().origin());
        BuilderSite market = site.get();
        // The players deliver the materials that the builders wait for: they go into the stock of the village.
        helper.onEachTick(() -> market.missingMaterials()
                .forEach(item -> settlement.economy().stock().set(item, MATERIALS)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderSites.of(level, heartPos).stream()
                        .noneMatch(found -> found.template().equals(MARKET)), "The builders must finish the market: "
                        + market.phase() + " " + market.status() + " " + market.appliedSteps() + "/"
                        + market.totalSteps()))
                .thenExecute(() -> {
                    heart.ensureGarrison(level.getGameTime());
                    VillageMarket.TradePlace place = heart.tradePlace(level);
                    VillageMarket.State state = heart.marketState(level);
                    settlement.economy().setPopulation(20);
                    // The heart can start the next site at its own check: then the test uses this site.
                    List<BuilderSite> sites = BuilderSites.of(level, heartPos);
                    Optional<BuilderSite> next = sites.isEmpty() ? VillageGrowth.check(level, heart)
                            : Optional.of(sites.get(0));
                    BlockPos nextOrigin = next.map(BuilderSite::origin).orElse(null);
                    BuilderSites.stop(level);
                    daylight.set(cycle, level.getServer());
                    WorldConfig.CONSTRUCTION_BUILDER_TICKS.set(WorldConfig.CONSTRUCTION_BUILDER_TICKS.getDefault());
                    VillageRepairData.get(level).forget(heartPos);
                    cleanUp(level, heart);
                    level.setBlock(heartPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

                    helper.assertTrue(heart.getLayout().markets().size() == 1,
                            "The heart must find the market sign with the market post behind it: "
                            + heart.getLayout().markets());
                    helper.assertTrue(state == VillageMarket.State.BUILT, "Expected the state BUILT, found " + state);
                    helper.assertTrue(place.market(), "The trade place must be the market");
                    helper.assertTrue(place.area().equals(box), "The market area must be the box of the piece " + box
                            + ", found " + place.area());
                    helper.assertTrue(place.area().isInside(place.point()), "The market sign must be in the area");
                    helper.assertTrue(next.isPresent() && next.get().template().equals(COTTAGE),
                            "A village keeps one market: the next site must be a home, found "
                            + next.map(BuilderSite::template));
                    helper.assertFalse(box.isInside(nextOrigin), "The home must go on the second plot");
                })
                .thenSucceed();
    }

    /**
     * A broken market goes again on its plot. The village has a market on the nearest plot and a second free plot. A
     * raid breaks the market sign and the market post: no block event tells the heart. The next check of the growth
     * scans the village again and starts the market on the same plot, with the same piece. The builders build the
     * missing blocks, and the heart finds the market sign at the same position. The second plot stays free: the next
     * check starts a home on it.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 12000, batch = "market_broken", skyAccess = true)
    public static void brokenMarketGoesAgainOnItsPlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        TestSupport.setConfigForTest(helper, WorldConfig.CONSTRUCTION_BUILDER_TICKS, 1);
        TestSupport.setGameRuleForTest(helper, GameRules.RULE_DAYLIGHT, false);
        level.setDayTime(TestSupport.NOON);
        VillageHeartBlockEntity heart = village(helper, HUGE, twoPlots(helper));
        BlockPos heartPos = heart.getBlockPos();
        VillageHearts.register(level, heartPos);
        Settlement settlement = levelFive(helper, heart);
        BuilderSite first = VillageGrowth.check(level, heart).orElseThrow();
        BoundingBox box = placeMarket(level, first);
        BuilderSites.stop(level);
        heart.requestScan();
        heart.ensureGarrison(level.getGameTime());
        helper.assertTrue(heart.getLayout().markets().size() == 1, "The heart must find the market sign: "
                + heart.getLayout().markets());
        BlockPos sign = heart.getLayout().markets().get(0);
        Direction facing = level.getBlockState(sign).getValue(HorizontalDirectionalBlock.FACING);
        level.setBlock(sign, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        level.setBlock(sign.relative(facing.getOpposite()), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);

        BuilderSite again = VillageGrowth.check(level, heart).orElseThrow();
        VillageMarket.State building = heart.marketState(level);
        helper.assertTrue(again.template().equals(first.template()) && again.origin().equals(first.origin())
                && again.rotation() == first.rotation(), "The market must go again on its plot: " + again.template()
                + " at " + again.origin() + ", expected " + first.template() + " at " + first.origin());
        helper.assertTrue(building == VillageMarket.State.BUILDING, "Expected the state BUILDING, found " + building);
        helper.onEachTick(() -> again.missingMaterials()
                .forEach(item -> settlement.economy().stock().set(item, MATERIALS)));
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(BuilderSites.of(level, heartPos).stream()
                        .noneMatch(found -> found.template().equals(MARKET)), "The builders must finish the market: " + again.phase() + " " + again.status() + " "
                        + again.appliedSteps() + "/" + again.totalSteps()))
                .thenExecute(() -> {
                    heart.ensureGarrison(level.getGameTime());
                    List<BlockPos> markets = heart.getLayout().markets();
                    VillageMarket.State state = heart.marketState(level);
                    settlement.economy().setPopulation(20);
                    // The heart can start the next site at its own check: then the test uses this site.
                    List<BuilderSite> sites = BuilderSites.of(level, heartPos);
                    Optional<BuilderSite> next = sites.isEmpty() ? VillageGrowth.check(level, heart)
                            : Optional.of(sites.get(0));
                    BuilderSites.stop(level);
                    VillageRepairData.get(level).forget(heartPos);
                    cleanUp(level, heart);
                    level.setBlock(heartPos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);

                    helper.assertTrue(markets.equals(List.of(sign)), "The market sign must stand again at " + sign
                            + ": " + markets);
                    helper.assertTrue(state == VillageMarket.State.BUILT, "Expected the state BUILT, found " + state);
                    helper.assertTrue(next.isPresent() && next.get().template().equals(COTTAGE)
                            && !box.isInside(next.get().origin()), "The second plot must stay free for a home: "
                            + next.map(BuilderSite::template));
                })
                .thenSucceed();
    }

    /**
     * A market site that stops before its end goes on with its plot after a load of the heart. The builders build a part
     * of the market, then the site stops. The heart is saved and loaded again (a restart of the server). The next check
     * of the growth starts the market on the same plot, with the same piece, and not on the second free plot.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 20, batch = "market_stopped")
    public static void stoppedMarketGoesOnWithItsPlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        VillageHeartBlockEntity heart = village(helper, HUGE, twoPlots(helper));
        levelFive(helper, heart);
        BuilderSite first = VillageGrowth.check(level, heart).orElseThrow();
        placeMarket(level, first);
        // A part of the market: the market sign is not built yet.
        heart.requestScan();
        heart.ensureGarrison(level.getGameTime());
        heart.getLayout().markets().forEach(pos -> level.setBlock(pos, Blocks.AIR.defaultBlockState(),
                Block.UPDATE_CLIENTS));
        BuilderSites.stop(level);
        VillageReference reference = heart.repairReference(level).orElseThrow();
        CompoundTag tag = heart.saveWithoutMetadata(level.registryAccess());
        heart.setMarketPlot(null);
        heart.loadWithComponents(tag, level.registryAccess());
        heart.setRepairReference(reference);
        heart.requestScan();
        heart.ensureGarrison(level.getGameTime());

        Optional<VillageMarket.Plot> plot = heart.marketPlot();
        VillageMarket.State missing = heart.marketState(level);
        Optional<BuilderSite> again = VillageGrowth.check(level, heart);
        BuilderSites.stop(level);
        VillageRepairData.get(level).forget(heart.getBlockPos());
        cleanUp(level, heart);

        helper.assertTrue(plot.equals(Optional.of(new VillageMarket.Plot(first.template(), first.origin(),
                first.rotation()))), "The heart must keep the plot of its market after a load: " + plot);
        helper.assertTrue(missing == VillageMarket.State.MISSING, "Expected the state MISSING, found " + missing);
        helper.assertTrue(again.isPresent() && again.get().template().equals(first.template())
                && again.get().origin().equals(first.origin()) && again.get().rotation() == first.rotation(),
                "The market must go on with its plot: " + again.map(BuilderSite::origin) + ", expected "
                + first.origin());
        helper.succeed();
    }

    /** The 2 plots of a test: the nearest plot to the heart, and a second plot farther. */
    private static List<VillagePlots.Plot> twoPlots(GameTestHelper helper) {
        return List.of(new VillagePlots.Plot(helper.absolutePos(CONNECTOR), Direction.EAST, HOUSE_FRONT, false),
                new VillagePlots.Plot(helper.absolutePos(SECOND_CONNECTOR), Direction.EAST, HOUSE_FRONT, false));
    }

    /** Sets a village to the level 5 with more population than places, and gives it builders. */
    private static Settlement levelFive(GameTestHelper helper, VillageHeartBlockEntity heart) {
        ServerLevel level = helper.getLevel();
        for (int i = 0; i < BUILDERS; i++) {
            helper.assertTrue(VillageFolk.spawn(level, heart.getBlockPos(), ModProfessions.BUILDER.get()) != null,
                    "The builder must spawn");
        }
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.setProgress(5, 0);
        settlement.economy().setPopulation(20);
        return settlement;
    }

    /** Places the finished template of a market site: the builders built it. Returns the box of the market. */
    private static BoundingBox placeMarket(ServerLevel level, BuilderSite site) {
        StructureTemplate template = level.getStructureManager().get(site.template()).orElseThrow();
        StructurePlaceSettings settings = GhostPlacement.settings(site.rotation(), Mirror.NONE)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        template.placeInWorld(level, site.origin(), site.origin(), settings, level.getRandom(), Block.UPDATE_CLIENTS);
        return GhostPlacement.box(template.getSize(), site.rotation(), Mirror.NONE, site.origin());
    }

    /**
     * A hamlet (below the level 5) needs no market: it trades at the plaza. The area of the plaza is the box of the
     * plaza piece of the village structure. With no plaza piece, it is the box of 15 blocks around the heart.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_hamlet")
    public static void hamletTradesAtThePlaza(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper, List.of());
        Settlements.of(level.getServer(), heart).orElseThrow().setProgress(4, 0);
        BlockPos heartPos = heart.getBlockPos();
        VillageMarket.State state = heart.marketState(level);
        VillageMarket.TradePlace noPiece = heart.tradePlace(level);
        // The heart is in the middle of the plaza piece of 15 x 6 x 15 blocks, one block above its floor.
        BlockPos origin = heartPos.offset(-7, -1, -7);
        heart.setRepairReference(new VillageReference(List.of(new VillageReference.Piece(PLAZA, origin,
                Rotation.NONE)), List.of(), Optional.empty(), List.of(), Optional.of(HOMES)));
        VillageMarket.TradePlace withPiece = heart.tradePlace(level);
        cleanUp(level, heart);

        helper.assertTrue(state == VillageMarket.State.HAMLET, "Expected the state HAMLET, found " + state);
        helper.assertFalse(noPiece.market(), "A hamlet must trade at the plaza");
        helper.assertTrue(noPiece.point().equals(heartPos), "The point of the plaza must be the heart");
        BoundingBox around = new BoundingBox(heartPos.getX() - 7, heartPos.getY() - 1, heartPos.getZ() - 7,
                heartPos.getX() + 7, heartPos.getY() + 4, heartPos.getZ() + 7);
        helper.assertTrue(noPiece.area().equals(around), "Expected the area " + around + ", found " + noPiece.area());
        BoundingBox piece = new BoundingBox(origin.getX(), origin.getY(), origin.getZ(), origin.getX() + 14,
                origin.getY() + 5, origin.getZ() + 14);
        helper.assertFalse(withPiece.market(), "A hamlet must trade at the plaza");
        helper.assertTrue(withPiece.area().equals(piece), "The plaza area must be the box of the plaza piece " + piece
                + ", found " + withPiece.area());
        helper.succeed();
    }

    /**
     * A village of the level 5 with no free plot gets no market: it trades at the plaza, and the state is NO_SPACE.
     * The market comes when a plot is free again.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_no_space")
    public static void noFreePlotGivesNoMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper, List.of());
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.setProgress(5, 0);
        settlement.economy().setPopulation(3);
        VillageMarket.State missing = heart.marketState(level);
        Optional<BuilderSite> site = VillageGrowth.check(level, heart);
        VillageMarket.State state = heart.marketState(level);
        VillageMarket.TradePlace place = heart.tradePlace(level);
        BuilderSites.stop(level);
        cleanUp(level, heart);

        helper.assertTrue(missing == VillageMarket.State.MISSING, "Expected the state MISSING, found " + missing);
        helper.assertTrue(site.isEmpty(), "With no free plot, the growth must start nothing");
        helper.assertTrue(state == VillageMarket.State.NO_SPACE, "Expected the state NO_SPACE, found " + state);
        helper.assertFalse(place.market(), "A village with no market must trade at the plaza");
        helper.succeed();
    }

    /**
     * A village with a heart, its builders and a village structure with the given plots. The first check of the
     * garrison scans the village and creates the builders.
     */
    private static VillageHeartBlockEntity village(GameTestHelper helper, List<VillagePlots.Plot> plots) {
        return village(helper, SIZE, plots);
    }

    /** As {@link #village(GameTestHelper, List)}, for an arena of a size. */
    private static VillageHeartBlockEntity village(GameTestHelper helper, int size, List<VillagePlots.Plot> plots) {
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper, size);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        heart.setRepairReference(new VillageReference(List.of(), List.of(), Optional.empty(), plots,
                Optional.of(HOMES)));
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.setProgress(1, 0);
        settlement.economy().setPopulation(3);
        heart.ensureGarrison(level.getGameTime());
        return heart;
    }

    private static void cleanUp(ServerLevel level, VillageHeartBlockEntity heart) {
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        VillageFolk.inhabitants(level, heart.getBlockPos()).forEach(Entity::discard);
    }
}
