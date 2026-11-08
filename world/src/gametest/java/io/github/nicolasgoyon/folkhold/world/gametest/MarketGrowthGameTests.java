package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
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
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** The connector of the plot, in the street: the plot is to the east of it. */
    private static final BlockPos CONNECTOR = new BlockPos(16, 1, 16);
    private static final ResourceLocation HOUSE_FRONT =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "house_front");
    private static final ResourceLocation HOMES = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/homes");
    private static final ResourceLocation MARKET = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/market");
    private static final ResourceLocation PLAZA = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/plaza");

    private MarketGrowthGameTests() {
    }

    /**
     * A village of the level 5 with a free plot builds its market before a new home. When the market stands, the heart
     * finds it: the trade place is the market, and its area is the box of the market piece. The village keeps one
     * market: the next check starts no second market.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "market_growth")
    public static void levelFiveVillageBuildsItsMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        VillageHeartBlockEntity heart = village(helper, List.of(new VillagePlots.Plot(
                helper.absolutePos(CONNECTOR), Direction.EAST, HOUSE_FRONT, false)));
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.setProgress(5, 0);
        settlement.economy().setPopulation(20);
        Optional<BuilderSite> site = VillageGrowth.check(level, heart);
        VillageMarket.State during = heart.marketState(level);
        VillageMarket.TradePlace before = heart.tradePlace(level);

        BoundingBox box = null;
        if (site.isPresent()) {
            // The builders build the market: the test places the finished template at the site.
            StructureTemplate template = level.getStructureManager().get(MARKET).orElseThrow();
            StructurePlaceSettings settings = GhostPlacement.settings(site.get().rotation(), Mirror.NONE)
                    .addProcessor(JigsawReplacementProcessor.INSTANCE);
            BlockPos origin = site.get().origin();
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            box = GhostPlacement.box(template.getSize(), site.get().rotation(), Mirror.NONE, origin);
        }
        BuilderSites.stop(level);
        heart.requestScan();
        heart.ensureGarrison(level.getGameTime());
        VillageMarket.TradePlace after = heart.tradePlace(level);
        VillageMarket.State built = heart.marketState(level);
        Optional<BuilderSite> next = VillageGrowth.check(level, heart);
        BuilderSites.stop(level);
        cleanUp(level, heart);

        helper.assertTrue(site.isPresent(), "A village of the level 5 must start its market on the free plot");
        helper.assertTrue(site.get().template().equals(MARKET), "The market must come before the home: "
                + site.get().template());
        helper.assertTrue(during == VillageMarket.State.BUILDING, "Expected the state BUILDING, found " + during);
        helper.assertFalse(before.market(), "Before its market stands, the village trades at the plaza");
        helper.assertTrue(heart.getLayout().markets().size() == 1, "The heart must find the market sign: "
                + heart.getLayout().markets());
        helper.assertTrue(built == VillageMarket.State.BUILT, "Expected the state BUILT, found " + built);
        helper.assertTrue(after.market(), "The trade place must be the market");
        helper.assertTrue(after.area().equals(box), "The market area must be the box of the piece " + box
                + ", found " + after.area());
        helper.assertTrue(after.area().isInside(after.point()), "The market sign must be in the market area");
        helper.assertTrue(next.isEmpty() || !next.get().template().equals(MARKET),
                "A village keeps one market: " + next.map(BuilderSite::template));
        helper.succeed();
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
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper, SIZE);
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
