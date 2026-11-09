package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.registry.ModPoiTypes;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageHomes;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.ai.village.poi.PoiTypes;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.entity.schedule.ScheduleBuilder;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the daily schedule of the workers (see {@link WorkerBrains}): a village with a market and the
 * house-workshop of a farmer. The vanilla brain of the workers walks them to the job sign of their home, to the market
 * sign and to their beds.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkerScheduleGameTests {
    private static final int ARENA = 32;
    private static final BlockPos HEART = new BlockPos(16, 1, 15);
    private static final BlockPos MARKET_ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos WORKSHOP_ORIGIN = new BlockPos(19, 1, 1);
    /** The origins of the pieces of the legacy village: a farmer house-workshop and a cottage. */
    private static final BlockPos LEGACY_WORKSHOP_ORIGIN = new BlockPos(1, 1, 1);
    private static final BlockPos COTTAGE_ORIGIN = new BlockPos(11, 1, 1);
    /** A composter on the floor, south of the heart. */
    private static final BlockPos COMPOSTER = new BlockPos(13, 1, 20);
    /** A composter and a vanilla villager in the small arena. */
    private static final BlockPos ARENA_COMPOSTER = new BlockPos(11, 1, 8);
    private static final BlockPos ARENA_VILLAGER = new BlockPos(4, 1, 8);
    /** A vanilla villager claims a free workstation in this time, in ticks: an attempt each 20 to 40 ticks. */
    private static final int CLAIM_TICKS = 300;
    /** A legacy job: its profession is the vanilla farmer, with the composter as workstation. */
    private static final String GARDENER = "folkhold_world:gardener";
    private static final String FARMER = "folkhold_world:farmer";
    private static final String MASON = "folkhold_world:mason";
    private static final long MORNING = 3_000L;
    private static final long NOON = 6_500L;
    private static final long NIGHT = 13_000L;
    /** A worker at work stays this near its job sign, in blocks. */
    private static final double NEAR_SIGN = 10.0;
    /** A worker at the market strolls near the market sign: it can be this far out of the market area, in blocks. */
    private static final int MARKET_MARGIN = 4;
    /** The interval of the villager checks of the heart when a player is near it, in ticks. */
    private static final int HEART_CHECK_TICKS = 40;
    /** The cell of a villager with no path out, in the small arena. The places are far from it. */
    private static final BlockPos CELL = new BlockPos(3, 1, 8);
    private static final BlockPos CELL_MARKET_SIGN = new BlockPos(13, 1, 4);
    private static final BlockPos CELL_JOB_SIGN = new BlockPos(13, 1, 12);
    /**
     * The age of the memory {@code CANT_REACH_WALK_TARGET_SINCE} in the tests of the places, in ticks: more than the
     * limit of the vanilla walk to the meeting point (200 ticks) and to the job site (1200 ticks).
     */
    private static final long UNREACHABLE_AGE = 2_000L;
    /** The vanilla brain runs the walk to a place in this time, in ticks. */
    private static final int BRAIN_TICKS = 100;

    private WorkerScheduleGameTests() {
    }

    /**
     * The farmers of a house-workshop work near its job sign at 3000, meet in the market area at 6500 and sleep in a
     * bed of the workshop at 13000. They keep the worker schedule after a refresh of the brain, a change of
     * profession and a load. The builder keeps the vanilla schedule. An alert stops the schedule.
     */
    @GameTest(template = "arena_large", timeoutTicks = 3000, batch = "worker_schedule", skyAccess = true)
    public static void workersFollowTheDailySchedule(GameTestHelper helper) {
        TestSupport.setConfigForTest(helper, WorldConfig.BUILDERS, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.HANDYMEN, 0);
        TestSupport.setConfigForTest(helper, WorldConfig.CONSTRUCTION_GROWTH, false);
        TestSupport.setConfigForTest(helper, WorldConfig.SCHEDULE_WORKER_SPREAD_TICKS, 0);
        ServerLevel level = helper.getLevel();
        long[] dayTime = {MORNING};
        helper.onEachTick(() -> level.setDayTime(dayTime[0]));
        List<BoundingBox> boxes = new ArrayList<>();
        VillageHeartBlockEntity heart = village(helper, boxes, List.of("market", "job_farmer"),
                List.of(MARKET_ORIGIN, WORKSHOP_ORIGIN), List.of(), false);
        // The floor of the test sets the noon: set the morning after it.
        level.setDayTime(MORNING);
        BoundingBox workshop = boxes.get(1);
        List<Villager> villagers = new ArrayList<>();
        TestSupport.atTestEnd(helper, () -> {
            villagers.forEach(Entity::discard);
            end(helper, boxes);
        });
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        VillageFolk.seats(settlement, VillageFolk.inhabitantCount(settlement)).forEach(seat ->
                villagers.add(VillageFolk.spawn(level, heart.getBlockPos(), seat.profession())));
        VillagerProfession farmer = VillageFolk.professionOf(FARMER);
        List<Villager> farmers = villagers.stream()
                .filter(villager -> villager.getVillagerData().getProfession() == farmer).toList();
        Villager builder = villagers.stream()
                .filter(villager -> villager.getVillagerData().getProfession() == ModProfessions.BUILDER.get())
                .findFirst().orElseThrow();
        helper.assertTrue(farmers.size() == 2, "Expected 2 farmers: " + villagers.size() + " villagers");
        heart.claimHomeBeds();

        JobPosts.Post post = settlement.jobPosts().orElseThrow().get(0);
        BlockPos sign = new BlockPos(post.x(), post.y(), post.z());
        GlobalPos jobSign = GlobalPos.of(level.dimension(), sign);
        VillageMarket.TradePlace market = heart.tradePlace(level);
        helper.assertTrue(market.market(), "The village must trade at its market");
        GlobalPos marketSign = GlobalPos.of(level.dimension(), market.point());
        // A job sign in a point of interest section with no record for the sign.
        level.getPoiManager().remove(sign);

        helper.assertTrue(heart.updateWorkers() > 0, "The workers must get their places");
        helper.assertTrue(level.getPoiManager().exists(sign, type -> type.is(ModPoiTypes.JOB_SIGN_KEY)),
                "The heart must add the point of interest of the job sign");
        for (Villager worker : farmers) {
            assertWorker(helper, worker, "after the spawn");
            helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(jobSign::equals)
                    .isPresent(), "The job site must be the job sign of the home");
            helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.MEETING_POINT).filter(marketSign::equals)
                    .isPresent(), "The meeting point must be the market sign");
        }
        helper.assertTrue(builder.getBrain().getSchedule() == Schedule.VILLAGER_DEFAULT,
                "The builder must keep the vanilla schedule");

        Villager changed = farmers.get(1);
        changed.refreshBrain(level);
        assertWorker(helper, changed, "after refreshBrain");
        VillageFolk.assign(List.of(changed), List.of(VillageFolk.professionOf(MASON)));
        assertWorker(helper, changed, "after a change of job");
        helper.assertTrue(changed.getBrain().getMemory(MemoryModuleType.JOB_SITE).isPresent(),
                "The worker must keep its job site after a change of job");
        VillageFolk.assign(List.of(changed), List.of(ModProfessions.INHABITANT.get()));
        helper.assertTrue(changed.getBrain().getSchedule() == Schedule.VILLAGER_DEFAULT,
                "An inhabitant with no job must have the vanilla schedule");
        helper.assertTrue(changed.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                "An inhabitant with no job must lose the job sign");
        VillageFolk.assign(List.of(changed), List.of(farmer));
        assertWorker(helper, changed, "after the return to the job");
        Villager loaded = EntityType.VILLAGER.create(level);
        loaded.load(farmers.get(0).saveWithoutId(new CompoundTag()));
        assertWorker(helper, loaded, "after a load");
        loaded.discard();
        heart.updateWorkers();
        helper.assertTrue(changed.getBrain().getMemory(MemoryModuleType.JOB_SITE).filter(jobSign::equals)
                .isPresent(), "The worker must get the job sign of its home again after the return to the job");
        builder.getBrain().setMemory(MemoryModuleType.JOB_SITE, jobSign);
        heart.updateWorkers();
        helper.assertTrue(builder.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                "The check of the heart must erase the job sign of a villager that is not a worker");

        // The heart can also update the workers at its own checks, so the counts are relative.
        int calculations = heart.workers().calculations();
        helper.assertTrue(heart.updateWorkers() == 0, "A second update must change nothing");
        helper.assertTrue(heart.workers().calculations() == calculations,
                "The heart must not calculate the places again in the same phase");
        BoundingBox marketArea = market.area().inflatedBy(MARKET_MARGIN, 0, MARKET_MARGIN);
        // The heart checks the villagers only when a player is near it. A GameTest has no player near the heart, so
        // the test runs the worker check of the heart at the same interval. The heart does not check the villagers in
        // an alert.
        helper.onEachTick(() -> {
            if (helper.getTick() % HEART_CHECK_TICKS == 0 && heart.getAlertState() == AlertState.CALM) {
                heart.updateWorkers();
            }
        });
        helper.startSequence()
                .thenWaitUntil(() -> farmers.forEach(worker -> {
                    helper.assertTrue(worker.getBrain().isActive(Activity.WORK), "Expected WORK at " + MORNING);
                    helper.assertTrue(worker.position().closerThan(sign.getCenter(), NEAR_SIGN),
                            "The farmer is not at its job sign: " + worker.blockPosition());
                }))
                .thenExecute(() -> {
                    dayTime[0] = NOON;
                    level.setDayTime(NOON);
                    int before = heart.workers().calculations();
                    heart.updateWorkers();
                    helper.assertTrue(heart.workers().calculations() == before + 1,
                            "The heart must calculate the places again after a phase change: " + before + " to "
                                    + heart.workers().calculations());
                })
                .thenWaitUntil(() -> farmers.forEach(worker -> {
                    helper.assertTrue(worker.getBrain().isActive(Activity.MEET), "Expected MEET at " + NOON);
                    helper.assertTrue(marketArea.isInside(worker.blockPosition()),
                            "The farmer is not in the market area: " + worker.blockPosition());
                }))
                .thenExecute(() -> dayTime[0] = NIGHT)
                .thenWaitUntil(() -> farmers.forEach(worker -> {
                    helper.assertTrue(worker.isSleeping(), "The farmer does not sleep: " + worker.blockPosition());
                    helper.assertTrue(worker.getSleepingPos().filter(workshop::isInside).isPresent(),
                            "The farmer must sleep in its home: " + worker.getSleepingPos());
                }))
                .thenExecute(() -> heart.setAlertState(AlertState.ALERT))
                .thenWaitUntil(() -> farmers.forEach(worker -> helper.assertTrue(
                        worker.getBrain().isActive(Activity.HIDE), "An alert must stop the schedule")))
                .thenExecute(() -> heart.setAlertState(AlertState.CALM))
                .thenSucceed();
    }

    /**
     * A legacy village: a farmer house-workshop and a cottage. The workers of the legacy job slots live in the cottage
     * and have no job sign. Their profession is the vanilla farmer. A composter near them stays free: they keep their
     * job and the worker schedule. The check of the heart releases a composter that a worker holds.
     */
    @GameTest(template = "arena_large", timeoutTicks = 1000, batch = "worker_schedule_workstation", skyAccess = true)
    public static void legacyWorkersDoNotClaimAWorkstation(GameTestHelper helper) {
        TestSupport.setConfigForTest(helper, WorldConfig.BUILDERS, 2);
        TestSupport.setConfigForTest(helper, WorldConfig.HANDYMEN, 1);
        TestSupport.setConfigForTest(helper, WorldConfig.CONSTRUCTION_GROWTH, false);
        TestSupport.setConfigForTest(helper, WorldConfig.SCHEDULE_WORKER_SPREAD_TICKS, 0);
        ServerLevel level = helper.getLevel();
        helper.onEachTick(() -> level.setDayTime(MORNING));
        List<BoundingBox> boxes = new ArrayList<>();
        int slots = LevelTables.get().traits(3).jobSlots();
        VillageHeartBlockEntity heart = village(helper, boxes, List.of("job_farmer", "house_cottage"),
                List.of(LEGACY_WORKSHOP_ORIGIN, COTTAGE_ORIGIN), Collections.nCopies(slots, GARDENER), true);
        level.setDayTime(MORNING);
        BlockPos composter = helper.absolutePos(COMPOSTER);
        GlobalPos composterSite = GlobalPos.of(level.dimension(), composter);
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        List<Villager> villagers = new ArrayList<>();
        TestSupport.atTestEnd(helper, () -> {
            villagers.forEach(Entity::discard);
            settlement.setLegacyJobs(false);
            helper.setBlock(COMPOSTER, Blocks.AIR);
            end(helper, boxes);
        });
        VillageFolk.seats(settlement, VillageFolk.inhabitantCount(settlement)).forEach(seat ->
                villagers.add(VillageFolk.spawn(level, heart.getBlockPos(), seat.profession())));
        heart.claimHomeBeds();
        heart.updateWorkers();
        BoundingBox cottage = boxes.get(1);
        List<Villager> legacy = villagers.stream()
                .filter(villager -> villager.getVillagerData().getProfession() == VillagerProfession.FARMER)
                .filter(villager -> VillageHomes.home(level, villager).filter(cottage::isInside).isPresent())
                .toList();
        helper.assertTrue(!legacy.isEmpty(), "Expected workers of a legacy job slot in the cottage");
        for (Villager worker : legacy) {
            assertWorker(helper, worker, "of a legacy job slot");
            helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                    "A worker of a legacy job slot must have no job sign");
        }

        helper.setBlock(COMPOSTER, Blocks.COMPOSTER);
        helper.assertTrue(hasFreeTicket(level, composter), "The composter must be a free workstation");
        // A worker that holds the composter in its save.
        Villager holder = legacy.get(0);
        level.getPoiManager().take(type -> type.is(PoiTypes.FARMER), (type, pos) -> pos.equals(composter),
                composter, 1);
        holder.getBrain().setMemory(MemoryModuleType.JOB_SITE, composterSite);
        heart.updateWorkers();
        helper.assertTrue(holder.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                "The check of the heart must erase the workstation of a worker");
        helper.assertTrue(hasFreeTicket(level, composter), "The check of the heart must release the composter");

        helper.startSequence()
                .thenExecuteFor(CLAIM_TICKS, () -> legacy.forEach(worker -> {
                    helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE).isEmpty()
                            && worker.getBrain().getMemory(MemoryModuleType.JOB_SITE).isEmpty(),
                            "A worker of a legacy job slot must not claim a workstation: "
                                    + worker.getBrain().getMemory(MemoryModuleType.POTENTIAL_JOB_SITE) + ", "
                                    + worker.getBrain().getMemory(MemoryModuleType.JOB_SITE));
                    helper.assertTrue(worker.getVillagerData().getProfession() == VillagerProfession.FARMER,
                            "A worker of a legacy job slot must keep its profession: "
                                    + worker.getVillagerData().getProfession());
                }))
                .thenExecute(() -> {
                    legacy.forEach(worker -> assertWorker(helper, worker, "near a composter"));
                    helper.assertTrue(hasFreeTicket(level, composter), "The composter must stay free");
                })
                .thenSucceed();
    }

    /** A vanilla villager outside the faction villages keeps the vanilla rules: it claims a free composter. */
    @GameTest(template = "arena", timeoutTicks = 1000, batch = "worker_schedule_vanilla_workstation")
    public static void vanillaVillagerClaimsAWorkstation(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos composter = helper.absolutePos(ARENA_COMPOSTER);
        GlobalPos composterSite = GlobalPos.of(level.dimension(), composter);
        Villager villager = EntityType.VILLAGER.create(level);
        TestSupport.atTestEnd(helper, () -> {
            villager.discard();
            helper.setBlock(ARENA_COMPOSTER, Blocks.AIR);
        });
        helper.setBlock(ARENA_COMPOSTER, Blocks.COMPOSTER);
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.FARMER).setLevel(1));
        villager.setVillagerXp(1);
        villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(ARENA_VILLAGER)));
        level.addFreshEntity(villager);
        helper.assertFalse(VillageFolk.isVillageFolk(villager), "The villager must not belong to a village");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                        .filter(composterSite::equals).isPresent(), "The vanilla villager must claim the composter: "
                        + villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)))
                .thenExecute(() -> helper.assertTrue(!hasFreeTicket(level, composter)
                        && villager.getVillagerData().getProfession() == VillagerProfession.FARMER,
                        "The vanilla villager must hold the ticket of the composter"))
                .thenSucceed();
    }

    /**
     * A worker in a cell with no path out, with no player near and no heart. The memory
     * {@code CANT_REACH_WALK_TARGET_SINCE} is older than the limits of the vanilla walks to the places. The worker
     * keeps its meeting point in {@code MEET} and its job sign in {@code WORK}. It also keeps a meeting point in an
     * other dimension.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "worker_places")
    public static void workerKeepsItsPlacesWithNoPlayer(GameTestHelper helper) {
        Places places = cellPlaces(helper);
        Villager worker = cellVillager(helper, true, 1);
        helper.assertTrue(WorkerBrains.isWorker(worker), "The villager must be a worker");
        worker.getBrain().setMemory(MemoryModuleType.MEETING_POINT, places.market());
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, places.job());
        GlobalPos nether = GlobalPos.of(Level.NETHER, places.market().pos());
        helper.startSequence()
                .thenExecute(() -> unreachable(helper, worker, Activity.MEET))
                .thenIdle(BRAIN_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(worker.getBrain().isActive(Activity.MEET), "Expected MEET");
                    helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                            .filter(places.market()::equals).isPresent(),
                            "The worker must keep the market sign as its meeting point: "
                                    + worker.getBrain().getMemory(MemoryModuleType.MEETING_POINT));
                    unreachable(helper, worker, Activity.WORK);
                })
                .thenIdle(BRAIN_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(worker.getBrain().isActive(Activity.WORK), "Expected WORK");
                    helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                            .filter(places.job()::equals).isPresent(),
                            "The worker must keep the job sign as its job site: "
                                    + worker.getBrain().getMemory(MemoryModuleType.JOB_SITE));
                    helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                            .filter(places.market()::equals).isPresent(), "The worker must keep its meeting point");
                    // The vanilla walk erases a place in an other dimension also with a recent memory
                    // CANT_REACH_WALK_TARGET_SINCE.
                    worker.getBrain().setMemory(MemoryModuleType.MEETING_POINT, nether);
                    unreachable(helper, worker, Activity.MEET);
                    worker.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                            helper.getLevel().getGameTime());
                })
                .thenIdle(BRAIN_TICKS)
                .thenExecute(() -> helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                        .filter(nether::equals).isPresent(), "The worker must keep a meeting point in an other "
                                + "dimension: " + worker.getBrain().getMemory(MemoryModuleType.MEETING_POINT)))
                .thenSucceed();
    }

    /**
     * A worker with no experience, in a cell with no path out, with no player near and no heart. It keeps its
     * profession when the memory {@code CANT_REACH_WALK_TARGET_SINCE} is older than the limit of the vanilla walk to
     * the job site, and also with no job site.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "worker_places_profession")
    public static void workerWithNoExperienceKeepsItsProfession(GameTestHelper helper) {
        Places places = cellPlaces(helper);
        Villager worker = cellVillager(helper, true, 0);
        VillagerProfession farmer = VillageFolk.professionOf(FARMER);
        helper.assertTrue(WorkerBrains.isWorker(worker) && worker.getVillagerXp() == 0,
                "The villager must be a worker with no experience");
        worker.getBrain().setMemory(MemoryModuleType.JOB_SITE, places.job());
        helper.startSequence()
                .thenExecute(() -> unreachable(helper, worker, Activity.WORK))
                .thenIdle(BRAIN_TICKS)
                .thenExecute(() -> {
                    helper.assertTrue(worker.getVillagerData().getProfession() == farmer,
                            "The worker must keep its profession after a long walk with no path to its job sign: "
                                    + worker.getVillagerData().getProfession());
                    helper.assertTrue(worker.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                            .filter(places.job()::equals).isPresent(), "The worker must keep its job sign");
                    worker.getBrain().eraseMemory(MemoryModuleType.JOB_SITE);
                })
                .thenIdle(BRAIN_TICKS)
                .thenExecute(() -> helper.assertTrue(worker.getVillagerData().getProfession() == farmer,
                        "The worker must keep its profession with no job site: "
                                + worker.getVillagerData().getProfession()))
                .thenSucceed();
    }

    /**
     * A villager outside the faction villages keeps the vanilla rules. In a cell with no path out, it loses its
     * meeting point after the limit of the vanilla walk. With no experience and no job site, it loses its profession.
     */
    @GameTest(template = "arena", timeoutTicks = 600, batch = "worker_places_vanilla")
    public static void vanillaVillagerLosesAnUnreachablePlace(GameTestHelper helper) {
        Places places = cellPlaces(helper);
        Villager villager = cellVillager(helper, false, 1);
        helper.assertFalse(VillageFolk.isVillageFolk(villager), "The villager must not belong to a village");
        villager.getBrain().setMemory(MemoryModuleType.MEETING_POINT, places.market());
        helper.startSequence()
                .thenExecute(() -> unreachable(helper, villager, Activity.MEET))
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT).isEmpty(),
                        "The vanilla villager must lose its meeting point"))
                .thenExecute(() -> villager.setVillagerXp(0))
                .thenWaitUntil(() -> helper.assertTrue(
                        villager.getVillagerData().getProfession() == VillagerProfession.NONE,
                        "The vanilla villager with no experience must lose its profession with no job site"))
                .thenSucceed();
    }

    /** The places of the tests in the cell: a market sign and a job sign, with their points of interest. */
    private record Places(GlobalPos market, GlobalPos job) {
    }

    /** Builds the floor, the cell and the 2 signs, and removes them at the end of the test. */
    private static Places cellPlaces(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        ServerLevel level = helper.getLevel();
        List<BlockPos> walls = List.of(CELL.west(), CELL.east(), CELL.north(), CELL.south(), CELL.above().west(),
                CELL.above().east(), CELL.above().north(), CELL.above().south(), CELL.above(2));
        walls.forEach(pos -> helper.setBlock(pos, Blocks.GLASS));
        helper.setBlock(CELL_MARKET_SIGN, ModBlocks.buildingSign(BuildingSignKind.MARKET));
        helper.setBlock(CELL_JOB_SIGN, ModBlocks.JOB_SIGN.get());
        TestSupport.atTestEnd(helper, () -> {
            walls.forEach(pos -> helper.setBlock(pos, Blocks.AIR));
            helper.setBlock(CELL_MARKET_SIGN, Blocks.AIR);
            helper.setBlock(CELL_JOB_SIGN, Blocks.AIR);
        });
        BlockPos market = helper.absolutePos(CELL_MARKET_SIGN);
        BlockPos job = helper.absolutePos(CELL_JOB_SIGN);
        helper.assertTrue(level.getPoiManager().exists(market, type -> type.is(ModPoiTypes.MARKET_SIGN_KEY)),
                "The market sign must have its point of interest");
        helper.assertTrue(level.getPoiManager().exists(job, type -> type.is(ModPoiTypes.JOB_SIGN_KEY)),
                "The job sign must have its point of interest");
        return new Places(GlobalPos.of(level.dimension(), market), GlobalPos.of(level.dimension(), job));
    }

    /**
     * Creates an adult farmer in the cell.
     *
     * @param village    true for a villager of a faction village (the village tag, with no heart at the position)
     * @param experience the experience of the villager
     */
    private static Villager cellVillager(GameTestHelper helper, boolean village, int experience) {
        ServerLevel level = helper.getLevel();
        Villager villager = EntityType.VILLAGER.create(level);
        TestSupport.atTestEnd(helper, villager::discard);
        if (village) {
            villager.getPersistentData().putLong(Merchants.VILLAGE_TAG, helper.absolutePos(HEART).asLong());
        }
        villager.setVillagerData(villager.getVillagerData().setProfession(VillageFolk.professionOf(FARMER))
                .setLevel(1));
        villager.setVillagerXp(experience);
        villager.moveTo(Vec3.atBottomCenterOf(helper.absolutePos(CELL)));
        level.addFreshEntity(villager);
        villager.refreshBrain(level);
        return villager;
    }

    /**
     * Gives a villager a schedule with one activity, and a memory {@code CANT_REACH_WALK_TARGET_SINCE} older than the
     * limits of the vanilla walks to the places: the conditions of the vanilla erase of a place.
     */
    private static void unreachable(GameTestHelper helper, Villager villager, Activity activity) {
        villager.getBrain().setSchedule(new ScheduleBuilder(new Schedule()).changeActivityAt(0, activity).build());
        villager.getBrain().setActiveActivityIfPossible(activity);
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getBrain().setMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE,
                helper.getLevel().getGameTime() - UNREACHABLE_AGE);
    }

    /** True if the composter has a free ticket. */
    private static boolean hasFreeTicket(ServerLevel level, BlockPos composter) {
        return level.getPoiManager().getInRange(type -> type.is(PoiTypes.FARMER), composter, 1,
                PoiManager.Occupancy.HAS_SPACE).anyMatch(record -> record.getPos().equals(composter));
    }

    /** The villager has the worker schedule of its step. */
    private static void assertWorker(GameTestHelper helper, Villager villager, String when) {
        helper.assertTrue(WorkerBrains.isWorker(villager), "The villager must be a worker " + when);
        helper.assertTrue(villager.getBrain().getSchedule() == WorkerBrains.scheduleOf(villager),
                "The worker must have the worker schedule " + when);
    }

    /**
     * Places the pieces of a village of level 3, the heart and its settlement, and scans the village.
     *
     * @param names    the pieces of the base style
     * @param origins  the origin of each piece
     * @param slotJobs the jobs of the economy
     * @param legacy   true for a legacy village
     */
    private static VillageHeartBlockEntity village(GameTestHelper helper, List<BoundingBox> boxes, List<String> names,
            List<BlockPos> origins, List<String> slotJobs, boolean legacy) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        List<VillageReference.Piece> pieces = new ArrayList<>();
        for (int i = 0; i < names.size(); i++) {
            ResourceLocation id = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, "village/"
                    + names.get(i));
            StructureTemplate template = level.getStructureManager().get(id).orElseThrow();
            BlockPos origin = helper.absolutePos(origins.get(i));
            template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
            boxes.add(template.getBoundingBox(settings, origin));
            pieces.add(new VillageReference.Piece(id, origin, Rotation.NONE));
        }
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        heart.setRepairReference(new VillageReference(pieces));
        Settlement settlement = Settlements.of(level.getServer(), heart).orElseThrow();
        settlement.setProgress(3, 0);
        settlement.setBiome("minecraft:plains");
        settlement.economy().setPopulation(20);
        settlement.economy().setJobs(slotJobs);
        settlement.setLegacyJobs(legacy);
        settlement.setJobPosts(Optional.empty());
        helper.assertTrue(heart.getLayout().scan(level, heart.getBlockPos()), "The scan area must be ready");
        heart.recordBeds();
        heart.recordJobPosts();
        return heart;
    }

    /** Removes the heart and the pieces: a later test at this place must not find them. */
    private static void end(GameTestHelper helper, List<BoundingBox> boxes) {
        helper.setBlock(HEART, Blocks.AIR);
        boxes.forEach(box -> BlockPos.betweenClosedStream(box).forEach(pos -> helper.getLevel()
                .setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS)));
    }
}
