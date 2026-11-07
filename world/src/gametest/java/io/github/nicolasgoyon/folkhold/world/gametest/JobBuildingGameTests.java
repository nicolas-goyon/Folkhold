package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.JobSignBlockEntity;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.core.sim.economy.CycleReport;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.economy.Economies;
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
 * In-game tests for the job buildings: a job works only when the village has the building of the job. The tests set
 * {@link WorldConfig#ECONOMY_ALL_JOBS_NEED_BUILDINGS}, so the blacksmith job needs its building.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class JobBuildingGameTests {
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos SIGN = new BlockPos(4, 1, 4);
    private static final String BLACKSMITH = "folkhold_world:blacksmith";
    /** The minimum level of the blacksmith job. */
    private static final int LEVEL = 5;
    private static final int POPULATION = 20;
    /** The tests call the checks of the heart this number of times to create all the villagers. */
    private static final int FILL_CHECKS = 30;

    private JobBuildingGameTests() {
    }

    /** A scanned village with blacksmith jobs and no blacksmith building has no blacksmith and no production. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "job_building_missing")
    public static void jobWithNoBuildingIsInactive(GameTestHelper helper) {
        run(helper, false);
    }

    /** A scanned village with a blacksmith building has its blacksmiths and their production. */
    @GameTest(template = "arena", timeoutTicks = 40, batch = "job_building_present")
    public static void jobWithItsBuildingIsActive(GameTestHelper helper) {
        run(helper, true);
    }

    private static void run(GameTestHelper helper, boolean building) {
        boolean allJobs = WorldConfig.ECONOMY_ALL_JOBS_NEED_BUILDINGS.get();
        WorldConfig.ECONOMY_ALL_JOBS_NEED_BUILDINGS.set(true);
        try {
            check(helper, building);
        } finally {
            WorldConfig.ECONOMY_ALL_JOBS_NEED_BUILDINGS.set(allJobs);
            // The heart has no garrison: other tests near this test must not find it.
            helper.setBlock(HEART, Blocks.AIR);
        }
        helper.succeed();
    }

    private static void check(GameTestHelper helper, boolean building) {
        TestSupport.buildFloor(helper);
        if (building) {
            helper.setBlock(SIGN, ModBlocks.JOB_SIGN.get());
            JobSignBlockEntity sign = helper.getBlockEntity(SIGN);
            sign.setJob("blacksmith", null);
        }
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.ensureSettlement();
        Settlement settlement = Settlements.of(helper.getLevel().getServer(), heart).orElseThrow();
        settlement.setProgress(LEVEL, 0);
        settlement.economy().setPopulation(POPULATION);
        int slots = LevelTables.get().traits(LEVEL).jobSlots();
        settlement.economy().setJobs(Collections.nCopies(slots, BLACKSMITH));
        TestSupport.emptyStock(settlement);
        int expected = POPULATION - Merchants.merchantCount(settlement);
        TestSupport.placeBeds(helper, expected - VillageBeds.staff());

        long now = helper.getLevel().getGameTime();
        for (int i = 0; i < FILL_CHECKS; i++) {
            heart.ensureGarrison(now + i);
        }
        BlockPos heartPos = heart.getBlockPos();
        List<Villager> inhabitants = VillageFolk.inhabitants(helper.getLevel(), heartPos);
        long blacksmiths = inhabitants.stream().filter(villager -> villager.getVillagerData().getProfession()
                == VillagerProfession.WEAPONSMITH).count();
        heart.getUnits().forEach(Entity::discard);
        heart.getCats().forEach(Entity::discard);
        Merchants.merchantsOf(helper.getLevel(), heartPos).forEach(Entity::discard);
        inhabitants.forEach(Entity::discard);

        List.of("minecraft:iron_ingot", "minecraft:coal", "minecraft:stick", "minecraft:oak_planks")
                .forEach(item -> settlement.economy().stock().set(item, 60));
        CycleReport report = Economies.runCycle(helper.getLevel().getServer(), settlement, new SplittableRandom(7));
        int workers = Math.min(expected - WorldConfig.BUILDERS.get() - WorldConfig.HANDYMEN.get(),
                VillageFolk.workerCount(slots, POPULATION, WorldConfig.ECONOMY_WORKERS_PER_JOB.get()));

        Optional<Map<String, Integer>> counts = settlement.jobBuildings();
        helper.assertTrue(counts.isPresent(), "The scan must count the job buildings");
        helper.assertTrue(inhabitants.size() == expected,
                "Expected " + expected + " inhabitants, found " + inhabitants.size());
        if (building) {
            helper.assertTrue(counts.get().equals(Map.of(BLACKSMITH, 1)), "Expected 1 blacksmith building: " + counts);
            helper.assertTrue(blacksmiths == workers, "Expected " + workers + " blacksmiths, found " + blacksmiths);
            helper.assertTrue(report.activeJobs() > 0 && !report.produced().isEmpty(),
                    "The blacksmiths must produce: " + report);
        } else {
            helper.assertTrue(counts.get().isEmpty(), "Expected no job building: " + counts);
            helper.assertTrue(blacksmiths == 0, "Expected no blacksmith, found " + blacksmiths);
            helper.assertTrue(report.activeJobs() == 0 && report.produced().isEmpty(),
                    "A job with no building must not produce: " + report);
        }
    }
}
