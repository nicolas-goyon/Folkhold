package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.JobPosts;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.registry.ModPoiTypes;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.AlertState;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import io.github.nicolasgoyon.folkhold.world.village.VillageMarket;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import java.util.ArrayList;
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
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.entity.schedule.Schedule;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.JigsawReplacementProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
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
    private static final String FARMER = "folkhold_world:farmer";
    private static final String MASON = "folkhold_world:mason";
    private static final long MORNING = 3_000L;
    private static final long NOON = 6_500L;
    private static final long NIGHT = 13_000L;
    /** A worker at work stays this near its job sign, in blocks. */
    private static final double NEAR_SIGN = 10.0;
    /** A worker at the market strolls near the market sign: it can be this far out of the market area, in blocks. */
    private static final int MARKET_MARGIN = 4;

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
        VillageHeartBlockEntity heart = village(helper, boxes);
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
        // A job sign of a chunk that was saved before its point of interest type existed.
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
        VillageFolk.assign(List.of(changed), List.of(farmer));
        assertWorker(helper, changed, "after the return to the job");
        Villager loaded = EntityType.VILLAGER.create(level);
        loaded.load(farmers.get(0).saveWithoutId(new CompoundTag()));
        assertWorker(helper, loaded, "after a load");
        loaded.discard();

        // The heart can also update the workers at its own checks, so the counts are relative.
        int calculations = heart.workers().calculations();
        helper.assertTrue(heart.updateWorkers() == 0, "A second update must change nothing");
        helper.assertTrue(heart.workers().calculations() == calculations,
                "The heart must not calculate the places again in the same phase");
        BoundingBox marketArea = market.area().inflatedBy(MARKET_MARGIN, 0, MARKET_MARGIN);
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

    /** The villager has the worker schedule of its step. */
    private static void assertWorker(GameTestHelper helper, Villager villager, String when) {
        helper.assertTrue(WorkerBrains.isWorker(villager), "The villager must be a worker " + when);
        helper.assertTrue(villager.getBrain().getSchedule() == WorkerBrains.scheduleOf(villager),
                "The worker must have the worker schedule " + when);
    }

    /** Places a market and the house-workshop of a farmer, the heart and its settlement, and scans the village. */
    private static VillageHeartBlockEntity village(GameTestHelper helper, List<BoundingBox> boxes) {
        TestSupport.buildFloor(helper, ARENA);
        ServerLevel level = helper.getLevel();
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .addProcessor(JigsawReplacementProcessor.INSTANCE).setKnownShape(true);
        List<VillageReference.Piece> pieces = new ArrayList<>();
        List<String> names = List.of("market", "job_farmer");
        List<BlockPos> origins = List.of(MARKET_ORIGIN, WORKSHOP_ORIGIN);
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
        settlement.economy().setJobs(List.of());
        settlement.setLegacyJobs(false);
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
