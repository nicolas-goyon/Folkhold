package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
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
import net.minecraft.world.level.levelgen.Heightmap;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the growth of a village: its builders build a new home or a building of the level on a free plot
 * (see {@link VillageGrowth}).
 * See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class VillageGrowthGameTests {
    private static final int SIZE = 32;
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    /** The connector of the plot, in the street: the plot is to the east of it. */
    private static final BlockPos CONNECTOR = new BlockPos(16, 1, 16);
    private static final ResourceLocation HOUSE_FRONT =
            ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "house_front");
    private static final ResourceLocation HOMES = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/homes");
    private static final ResourceLocation COTTAGE = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/house_cottage");
    private static final ResourceLocation FORGE = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/house_forge");

    private VillageGrowthGameTests() {
    }

    /**
     * A village with too few places and a free plot: its builders start the construction of a home on the plot. The
     * connector of the home is in front of the connector of the plot, and the home faces the street. A village with
     * enough places builds nothing.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_plot")
    public static void buildersStartAHomeOnAFreePlot(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper, SIZE);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        VillagePlots.Plot plot = new VillagePlots.Plot(helper.absolutePos(CONNECTOR), Direction.EAST, HOUSE_FRONT,
                false);
        heart.setRepairReference(new VillageReference(List.of(), List.of(), Optional.empty(), List.of(plot),
                Optional.of(HOMES)));
        // The first check of the garrison scans the village (no bed) and creates the builders. The level 1 needs no
        // building.
        settlement.setProgress(1, 0);
        settlement.economy().setPopulation(Merchants.merchantCount(settlement) + 3);
        heart.ensureGarrison(level.getGameTime());
        boolean enoughPlaces = VillageGrowth.check(level, heart).isEmpty();

        settlement.economy().setPopulation(20);
        Optional<BuilderSite> site = VillageGrowth.check(level, heart);

        BuilderSites.stop(level);
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(level, heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(level, heart.getBlockPos()).forEach(Entity::discard);
        helper.assertTrue(enoughPlaces, "A village with enough places must build no home");
        helper.assertTrue(site.isPresent(), "A village that needs places must start a home on the free plot");
        helper.assertTrue(site.get().template().equals(COTTAGE), "The home must be a cottage: "
                + site.get().template());
        // The connector of the cottage is at (4, 1, 0) and faces north. To face the west, the cottage turns
        // counterclockwise: the connector goes to (0, 1, -4) from the origin. The connector of the cottage is in front
        // of the connector of the plot (one block to the east), at the Y of the first free block above the ground.
        BlockPos connector = helper.absolutePos(CONNECTOR);
        BlockPos front = connector.east();
        int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, connector.getX(), connector.getZ());
        BlockPos expected = new BlockPos(front.getX(), ground - 1, front.getZ() + 4);
        helper.assertTrue(site.get().origin().equals(expected), "Expected the origin " + expected + ", found "
                + site.get().origin());
        helper.succeed();
    }

    /**
     * A village with enough places and a missing building of its level: its builders start this building on the free
     * plot. The village of the level 5 has a storehouse sign, so the forge comes first. A village of the level 1 needs
     * no building.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_building")
    public static void buildersStartAMissingBuildingOfTheLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper, SIZE);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        helper.setBlock(HEART.east(2), ModBlocks.buildingSign(BuildingSignKind.STOREHOUSE));
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        VillagePlots.Plot plot = new VillagePlots.Plot(helper.absolutePos(CONNECTOR), Direction.EAST, HOUSE_FRONT,
                false);
        heart.setRepairReference(new VillageReference(List.of(), List.of(), Optional.empty(), List.of(plot),
                Optional.of(HOMES)));
        // The first check of the garrison scans the village and creates the builders. The population fills the
        // places of the staff: the village needs no home.
        settlement.setProgress(1, 0);
        settlement.economy().setPopulation(Merchants.merchantCount(settlement) + 3);
        heart.ensureGarrison(level.getGameTime());
        boolean lowLevel = VillageGrowth.check(level, heart).isEmpty();

        settlement.setProgress(5, 0);
        settlement.economy().setPopulation(Merchants.merchantCount(settlement) + 3);
        Optional<BuilderSite> site = VillageGrowth.check(level, heart);

        BuilderSites.stop(level);
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(level, heart.getBlockPos()).forEach(Entity::discard);
        VillageFolk.inhabitants(level, heart.getBlockPos()).forEach(Entity::discard);
        helper.assertTrue(heart.getLayout().buildings().getOrDefault("storehouse", 0) == 1,
                "The scan must count the storehouse sign: " + heart.getLayout().buildings());
        helper.assertTrue(lowLevel, "A village of the level 1 must build nothing");
        helper.assertTrue(site.isPresent(), "A village with a missing building must start it on the free plot");
        helper.assertTrue(site.get().template().equals(FORGE), "The building must be the forge: "
                + site.get().template());
        helper.succeed();
    }
}
