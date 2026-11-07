package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.CycleReport;
import io.github.nicolasgoyon.folkhold.core.sim.economy.Economy;
import io.github.nicolasgoyon.folkhold.core.sim.level.LevelTraits;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
import io.github.nicolasgoyon.folkhold.world.economy.EconomyData;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.Settlements;
import io.github.nicolasgoyon.folkhold.world.village.VillageBeds;
import io.github.nicolasgoyon.folkhold.world.village.VillageFolk;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SplittableRandom;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the job buildings: a job works only when the village has the building of the job. The test job
 * {@code folkhold_world:test_smith} (GameTest data) needs its building and shows the weaponsmith profession. Its
 * minimum level is more than the last level, so no settlement gets it from the job assignment.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobBuildingGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos SIGN = new BlockPos(4, 1, 4);
    private static final String SIGN_JOB = "test_smith";
    private static final String JOB = "folkhold_world:" + SIGN_JOB;
    private static final int LEVEL = 5;
    private static final int POPULATION = 20;
    /** The tests call the checks of the heart this number of times to create all the villagers. */
    private static final int FILL_CHECKS = 30;

    private JobBuildingGameTests() {
    }

    private record Village(VillageHeartBlockEntity heart, Settlement settlement, int inhabitants, int workers) {
    }

    /** A scanned village with smith jobs and no smith building has no smith and no production. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "job_building_missing")
    public static void jobWithNoBuildingIsInactive(GameTestHelper helper) {
        Village village = village(helper, false);
        try {
            fill(helper, village);
            CycleReport report = cycle(village.settlement());
            helper.assertTrue(village.settlement().jobBuildings().equals(Optional.of(Map.of())),
                    "Expected known counts with no job building: " + village.settlement().jobBuildings());
            assertSmiths(helper, village, 0);
            helper.assertTrue(report.activeJobs() == 0 && report.produced().isEmpty(),
                    "A job with no building must not produce: " + report);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /** A scanned village with a smith building has its smiths and their production. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "job_building_present")
    public static void jobWithItsBuildingIsActive(GameTestHelper helper) {
        Village village = village(helper, true);
        try {
            fill(helper, village);
            CycleReport report = cycle(village.settlement());
            helper.assertTrue(village.settlement().jobBuildings().equals(Optional.of(Map.of(JOB, 1))),
                    "Expected 1 smith building: " + village.settlement().jobBuildings());
            assertSmiths(helper, village, village.workers());
            helper.assertTrue(report.produced().getOrDefault("minecraft:iron_sword", 0) > 0,
                    "The smiths must produce: " + report);
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    /**
     * The villagers that exist follow the building: with no sign, the smiths become inhabitants with no job. With the
     * sign again, they become smiths again. The number of inhabitants does not change.
     */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "job_building_switch")
    public static void workersFollowTheBuilding(GameTestHelper helper) {
        Village village = village(helper, true);
        try {
            fill(helper, village);
            assertSmiths(helper, village, village.workers());
            helper.setBlock(SIGN, Blocks.AIR);
            village.heart().requestScan();
            fill(helper, village);
            assertSmiths(helper, village, 0);
            placeSign(helper);
            village.heart().requestScan();
            fill(helper, village);
            assertSmiths(helper, village, village.workers());
        } finally {
            end(helper, village);
        }
        helper.succeed();
    }

    private static Village village(GameTestHelper helper, boolean sign) {
        TestSupport.buildFloor(helper);
        if (sign) {
            placeSign(helper);
        }
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        settlement.setProgress(LEVEL, 0);
        settlement.economy().setPopulation(POPULATION);
        int slots = LevelTables.get().traits(LEVEL).jobSlots();
        settlement.economy().setJobs(Collections.nCopies(slots, JOB));
        TestSupport.emptyStock(settlement);
        int inhabitants = POPULATION - Merchants.merchantCount(settlement);
        TestSupport.placeBeds(helper, inhabitants - VillageBeds.staff());
        int workers = Math.min(inhabitants - WorldConfig.BUILDERS.get() - WorldConfig.HANDYMEN.get(),
                VillageFolk.workerCount(slots, POPULATION, WorldConfig.ECONOMY_WORKERS_PER_JOB.get()));
        return new Village(heart, settlement, inhabitants, workers);
    }

    private static void placeSign(GameTestHelper helper) {
        helper.setBlock(SIGN, ModBlocks.JOB_SIGN.get());
        JobSignBlockEntity sign = helper.getBlockEntity(SIGN);
        sign.setJob(SIGN_JOB, null);
    }

    /** Calls the checks of the heart: the heart scans the village if necessary and gives the professions. */
    private static void fill(GameTestHelper helper, Village village) {
        long now = helper.getLevel().getGameTime();
        for (int i = 0; i < FILL_CHECKS; i++) {
            village.heart().ensureGarrison(now + i);
        }
    }

    /** One economic cycle with the active jobs, with the inputs of the smiths in the stock. */
    private static CycleReport cycle(Settlement settlement) {
        settlement.economy().stock().set("minecraft:iron_ingot", 60);
        LevelTraits traits = LevelTables.get().traits(LEVEL);
        return Economy.runCycle(settlement.economy(), Economies.activeJobs(settlement), traits.maxPopulation(),
                traits.stockCapacity(), EconomyData.catalog(), Economies.settings(), new SplittableRandom(7));
    }

    private static void assertSmiths(GameTestHelper helper, Village village, int expected) {
        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), village.heart().getBlockPos());
        long smiths = inhabitants.stream().filter(villager -> villager.getVillagerData().getProfession()
                == VillagerProfession.WEAPONSMITH).count();
        helper.assertTrue(inhabitants.size() == village.inhabitants(),
                "Expected " + village.inhabitants() + " inhabitants, found " + inhabitants.size());
        helper.assertTrue(smiths == expected, "Expected " + expected + " smiths, found " + smiths);
    }

    /** Removes the villagers, the garrison and the heart: other tests near this test must not find them. */
    private static void end(GameTestHelper helper, Village village) {
        BlockPos heartPos = village.heart().getBlockPos();
        village.heart().getUnits().forEach(Entity::discard);
        village.heart().getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heartPos).forEach(Entity::discard);
        VillageFolk.inhabitants(helper.getLevel(), heartPos).forEach(Entity::discard);
        helper.setBlock(HEART, Blocks.AIR);
    }
}
