package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobAssignment;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSites;
import io.github.nicolasgoyon.folkhold.world.construction.GhostPlacement;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageGrowth;
import io.github.nicolasgoyon.folkhold.world.village.VillagePlots;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairData;
import java.util.ArrayList;
import java.util.Collections;
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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the growth of a village: its builders build a house-workshop or a building of the level on a free
 * plot (see {@link VillageGrowth}). Most villages of the tests have the house-workshop of the farmer (a food post that
 * trades) as a piece of their village structure, south-west of the heart.
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
    /** The pool of the homes of the test variant (GameTest data): its pool of jobs has no piece of a valid job. */
    private static final ResourceLocation TEST_HOMES = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/test/homes");
    private static final ResourceLocation STOREHOUSE = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/house_storehouse");
    private static final ResourceLocation FARM = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/job_farmer");
    /** The origin of the house-workshop of the farmer: out of the space of the plot. */
    private static final BlockPos FARM_ORIGIN = new BlockPos(1, 1, 22);
    private static final String FARMER = FolkholdWorld.MOD_ID + ":farmer";
    private static final ResourceLocation BARRACKS = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/house_barracks");

    private VillageGrowthGameTests() {
    }

    /**
     * A village with a farmer post, too few places and a free plot: its builders start the house-workshop of the job
     * that {@link JobAssignment#next} gives. The connector of the house-workshop is in front of the connector of the
     * plot, and the house-workshop faces the street. A village with enough places builds nothing. At the end of the
     * site, the scan gives 1 more post with the job and the places of the new home.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_plot")
    public static void buildersStartTheHouseWorkshopOfTheNextJob(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = village(helper, HOMES, true, List.of(plot(helper, false)), 1, 3, false);
        Settlement settlement = village.settlement();
        VillageHeartBlockEntity heart = village.heart();
        try {
            boolean enoughPlaces = VillageGrowth.check(level, heart).isEmpty();
            settlement.economy().setPopulation(20);
            String job = nextJob(level, heart, settlement);
            int places = VillageBeds.places(settlement);
            Optional<BuilderSite> site = VillageGrowth.check(level, heart);

            helper.assertTrue(enoughPlaces, "A village with enough places must build no home");
            helper.assertTrue(site.isPresent(), "A village that needs places must start a home on the free plot");
            helper.assertTrue(site.get().template().equals(workshop(job)), "The home must be the house-workshop of "
                    + job + ": " + site.get().template());
            helper.assertTrue(heart.growthWorkshop().map(VillageHeartBlockEntity.GrowthWorkshop::job)
                    .equals(Optional.of(job)), "The heart must keep the job of the site: " + heart.growthWorkshop());
            // The connector of the house-workshop is at (4, 1, 0) and faces north. To face the west, the piece turns
            // counterclockwise: the connector goes to (0, 1, -4) from the origin. The connector of the piece is in
            // front of the connector of the plot (one block to the east), at the Y of the first free block above the
            // ground.
            BlockPos connector = helper.absolutePos(CONNECTOR);
            BlockPos front = connector.east();
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, connector.getX(),
                    connector.getZ());
            BlockPos expected = new BlockPos(front.getX(), ground - 1, front.getZ() + 4);
            helper.assertTrue(site.get().origin().equals(expected), "Expected the origin " + expected + ", found "
                    + site.get().origin());

            finish(level, heart, site.get());
            List<String> posts = settlement.jobPosts().orElseThrow().stream().map(JobPosts.Post::jobId).toList();
            List<String> expectedPosts = new ArrayList<>(List.of(FARMER, job));
            helper.assertTrue(posts.stream().sorted().toList().equals(expectedPosts.stream().sorted().toList()),
                    "The scan must give the post of the new home: " + posts);
            helper.assertTrue(VillageBeds.places(settlement) > places, "The new home must give places: "
                    + VillageBeds.places(settlement) + " places, " + places + " before");
            VillageGrowth.postJobs(level.getServer(), heart, settlement);
            helper.assertTrue(heart.growthWorkshop().isEmpty(), "The scan shows the post: the heart forgets the job");
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A village with no food post and no need of places: its builders start a food house-workshop that trades before
     * the other buildings. The village of the level 3 needs a storehouse: the house-workshop comes first.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_food")
    public static void villageWithNoFoodPostBuildsAFoodHouseWorkshop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = village(helper, HOMES, false, List.of(plot(helper, false)), 3, 3, false);
        try {
            helper.assertFalse(VillageGrowth.needsPlaces(village.settlement()), "The village must have its places");
            Optional<BuilderSite> site = VillageGrowth.check(level, village.heart());
            String job = village.heart().growthWorkshop().map(VillageHeartBlockEntity.GrowthWorkshop::job).orElse("");
            helper.assertTrue(site.isPresent() && site.get().template().equals(workshop(job)),
                    "The village must start a house-workshop: " + site.map(BuilderSite::template));
            helper.assertTrue(Economies.producesFood(job) && Merchants.trades(job),
                    "The house-workshop must be of a food job that trades: " + job);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A village with no food post in a variant with no piece for its jobs: the growth tries {@link
     * VillageGrowth#WORKSHOP_JOBS} jobs and goes on with the storehouse of the level 3. With no free plot, the growth
     * starts no site.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_no_piece")
    public static void noHouseWorkshopDoesNotStopTheGrowth(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = village(helper, TEST_HOMES, false, List.of(plot(helper, false)), 3, 3, false);
        try {
            Optional<BuilderSite> site = VillageGrowth.check(level, village.heart());
            helper.assertTrue(site.isPresent() && site.get().template().equals(STOREHOUSE),
                    "The growth must go on with the storehouse: " + site.map(BuilderSite::template));
            helper.assertTrue(village.heart().growthWorkshop().isEmpty(), "No house-workshop must start");

            BuilderSites.stop(level);
            village.heart().setRepairReference(new VillageReference(List.of(), List.of(), Optional.empty(),
                    List.of(plot(helper, true)), Optional.of(HOMES)));
            helper.assertTrue(VillageGrowth.check(level, village.heart()).isEmpty(),
                    "With no free plot, the growth must start no site");
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A legacy village (its save has no list of posts) with too few places gets a food house-workshop on the free plot.
     * After the scan, the new post takes one legacy job slot: the village keeps the job slots of its level.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_legacy")
    public static void legacyVillageGetsAHouseWorkshop(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Village village = village(helper, HOMES, false, List.of(plot(helper, false)), 3, 20, true);
        Settlement settlement = village.settlement();
        int slots = LevelTables.get().traits(3).jobSlots();
        try {
            List<String> before = Economies.activeJobIds(settlement);
            Optional<BuilderSite> site = VillageGrowth.check(level, village.heart());
            String job = village.heart().growthWorkshop().map(VillageHeartBlockEntity.GrowthWorkshop::job).orElse("");
            helper.assertTrue(site.isPresent() && site.get().template().equals(workshop(job)),
                    "The legacy village must start a house-workshop: " + site.map(BuilderSite::template));
            finish(level, village.heart(), site.get());
            List<String> after = Economies.activeJobIds(settlement);

            helper.assertTrue(before.size() == slots, "Before the site, the legacy slots give the jobs: " + before);
            helper.assertTrue(after.size() == slots && after.get(0).equals(job),
                    "The post must take one legacy slot: " + after);
            helper.assertTrue(JobPosts.legacySlots(slots, 1) == slots - 1, "The legacy slots must go down by 1");
            helper.assertTrue(settlement.legacyJobs(), "The village stays a legacy village");
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * A village with its food post, enough places and a missing building of its level: its builders start this
     * building on the free plot. The village has a storehouse sign, so the barracks of the level 9 comes first. The
     * village has a market. A village of the level 1 or of the level 5 needs no building: the forge is the building of
     * the blacksmith, not a building of a level.
     */
    @GameTest(template = "arena_large", timeoutTicks = 20, batch = "village_growth_building")
    public static void buildersStartAMissingBuildingOfTheLevel(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        // The population fills the places of the staff: the village needs no home.
        Village village = village(helper, HOMES, true, List.of(plot(helper, false)), 1, 3, false);
        Settlement settlement = village.settlement();
        VillageHeartBlockEntity heart = village.heart();
        try {
            helper.setBlock(HEART.east(2), ModBlocks.buildingSign(BuildingSignKind.STOREHOUSE));
            // A market sign with the market post behind it: the village has its market of the level 5.
            helper.setBlock(HEART.west(2), ModBlocks.buildingSign(BuildingSignKind.MARKET).defaultBlockState()
                    .setValue(HorizontalDirectionalBlock.FACING, Direction.NORTH));
            helper.setBlock(HEART.west(2).south(), Blocks.OAK_LOG);
            heart.requestScan();
            heart.ensureGarrison(level.getGameTime());
            boolean lowLevel = VillageGrowth.check(level, heart).isEmpty();
            settlement.setProgress(5, 0);
            boolean noForge = VillageGrowth.check(level, heart).isEmpty();
            settlement.setProgress(9, 0);
            Optional<BuilderSite> site = VillageGrowth.check(level, heart);

            helper.assertTrue(heart.getLayout().markets().size() == 1, "The scan must find the market: "
                    + heart.getLayout().markets());
            helper.assertTrue(heart.getLayout().buildings().getOrDefault("storehouse", 0) == 1,
                    "The scan must count the storehouse sign: " + heart.getLayout().buildings());
            helper.assertTrue(lowLevel, "A village of the level 1 must build nothing");
            helper.assertTrue(noForge, "A village of the level 5 must build no forge");
            helper.assertTrue(site.isPresent(), "A village with a missing building must start it on the free plot");
            helper.assertTrue(site.get().template().equals(BARRACKS), "The building must be the barracks: "
                    + site.get().template());
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    private record Village(VillageHeartBlockEntity heart, Settlement settlement, List<BoundingBox> boxes) {
    }

    /** The plot of the tests, east of the connector in the street. */
    private static VillagePlots.Plot plot(GameTestHelper helper, boolean built) {
        return new VillagePlots.Plot(helper.absolutePos(CONNECTOR), Direction.EAST, HOUSE_FRONT, built);
    }

    /**
     * A village in the plains with its heart, its builders and a village structure. The first check of the garrison
     * scans the village, records its posts and creates the builders.
     *
     * @param farm   true for the house-workshop of the farmer as a piece of the village structure
     * @param legacy true for a legacy village with the job slots of its level
     */
    private static Village village(GameTestHelper helper, ResourceLocation homes, boolean farm,
            List<VillagePlots.Plot> plots, int level, int population, boolean legacy) {
        ServerLevel serverLevel = helper.getLevel();
        TestSupport.buildFloor(helper, SIZE);
        List<VillageReference.Piece> pieces = new ArrayList<>();
        List<BoundingBox> boxes = new ArrayList<>();
        if (farm) {
            StructureTemplate template = serverLevel.getStructureManager().get(FARM).orElseThrow();
            BlockPos origin = helper.absolutePos(FARM_ORIGIN);
            StructurePlaceSettings settings = new StructurePlaceSettings()
                    .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
            template.placeInWorld(serverLevel, origin, origin, settings, serverLevel.getRandom(),
                    Block.UPDATE_CLIENTS);
            boxes.add(template.getBoundingBox(settings, origin));
            pieces.add(new VillageReference.Piece(FARM, origin, Rotation.NONE));
        }
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        heart.setRepairReference(new VillageReference(pieces, List.of(), Optional.empty(), plots,
                Optional.of(homes)));
        Settlement settlement = Settlements.of(serverLevel.getServer(), heart).orElseThrow();
        settlement.setProgress(level, 0);
        settlement.setBiome("minecraft:plains");
        settlement.economy().setPopulation(population);
        int slots = LevelTables.get().traits(level).jobSlots();
        settlement.economy().setJobs(legacy ? Collections.nCopies(slots, "folkhold_world:miner") : List.of());
        settlement.setLegacyJobs(legacy);
        settlement.setJobPosts(Optional.empty());
        heart.ensureGarrison(serverLevel.getGameTime());
        return new Village(heart, settlement, boxes);
    }

    /** The end of a site: the blocks of its piece are in place, and the heart scans the village again. */
    private static void finish(ServerLevel level, VillageHeartBlockEntity heart, BuilderSite site) {
        StructureTemplate template = level.getStructureManager().get(site.template()).orElseThrow();
        StructurePlaceSettings settings = GhostPlacement.settings(site.rotation(), Mirror.NONE)
                .addProcessor(JigsawReplacementProcessor.INSTANCE);
        template.placeInWorld(level, site.origin(), site.origin(), settings, level.getRandom(), Block.UPDATE_CLIENTS);
        BuilderSites.stop(level);
        heart.requestScan();
        heart.ensureGarrison(level.getGameTime());
    }

    /** The job that {@link JobAssignment#next} gives for the next house-workshop of a village. */
    private static String nextJob(ServerLevel level, VillageHeartBlockEntity heart, Settlement settlement) {
        List<String> posts = VillageGrowth.postJobs(level.getServer(), heart, settlement);
        return JobAssignment.next(posts, Economies.eligibleJobs(level.getServer(), settlement), EconomyData.catalog(),
                Merchants::trades, JobAssignment.postSeed(Settlements.seed(settlement.id()), posts.size()))
                .orElseThrow();
    }

    /** The template of the house-workshop of a job in the base variant. */
    private static ResourceLocation workshop(String jobId) {
        return ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
                "village/job_" + ResourceLocation.parse(jobId).getPath());
    }

    /** Removes the sites, the villagers, the pieces and the heart: other tests near this test must not find them. */
    private static void end(GameTestHelper helper, Village village) {
        ServerLevel level = helper.getLevel();
        BuilderSites.stop(level);
        VillageHeartBlockEntity heart = village.heart();
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        VillageFolk.inhabitants(level, heart.getBlockPos()).forEach(Entity::discard);
        village.settlement().setLegacyJobs(false);
        VillageRepairData.get(level).forget(heart.getBlockPos());
        helper.setBlock(HEART, Blocks.AIR);
        village.boxes().forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos -> level.setBlock(pos,
                Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
    }
}
