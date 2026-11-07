package io.github.nicolasgoyon.folkhold.world.gametest;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.economy.Merchants;
import io.github.nicolasgoyon.folkhold.world.registry.ModPoiTypes;
import io.github.nicolasgoyon.folkhold.world.registry.ModSchedules;
import io.github.nicolasgoyon.folkhold.world.village.schedule.WorkerBrains;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.schedule.Activity;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Prototype of the spike #640: a worker of a faction village with the worker schedule. The vanilla brain walks it to
 * its job sign (activity {@code WORK}, memory {@code JOB_SITE}) and to the market bell (activity {@code MEET}, memory
 * {@code MEETING_POINT}).
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class WorkerScheduleGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long MORNING = 3_000L;
    private static final long NOON = 6_500L;
    private static final double NEAR_SIGN = 10.0;
    private static final double NEAR_MARKET = 8.0;
    private static final int WORKERS = 48;

    private WorkerScheduleGameTests() {
    }

    /** A worker of a faction village: weaponsmith with 1 experience point, with the tag of a village. */
    private static Villager worker(GameTestHelper helper, BlockPos relative) {
        Villager villager = helper.spawn(EntityType.VILLAGER, relative);
        villager.getPersistentData().putLong(Merchants.VILLAGE_TAG, helper.absolutePos(BlockPos.ZERO).asLong());
        villager.setVillagerData(villager.getVillagerData().setProfession(VillagerProfession.WEAPONSMITH).setLevel(1));
        villager.setVillagerXp(1);
        // The goal packages of the brain use the profession: refresh the brain after a change of profession.
        villager.refreshBrain(helper.getLevel());
        return villager;
    }

    private static GlobalPos global(GameTestHelper helper, BlockPos relative) {
        return GlobalPos.of(helper.getLevel().dimension(), helper.absolutePos(relative));
    }

    private static boolean near(GameTestHelper helper, Villager villager, BlockPos relative, double distance) {
        return villager.position().closerThan(helper.absolutePos(relative).getCenter(), distance);
    }

    /**
     * A worker walks to its job sign in the morning and to the market bell at noon. The worker schedule survives a
     * refresh of the brain, and the job sign stays its job site.
     */
    @GameTest(template = "arena_large", timeoutTicks = 1200, batch = "worker_schedule")
    public static void workerWalksToJobSignThenMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos sign = new BlockPos(27, 1, 4);
        BlockPos plazaBell = new BlockPos(4, 1, 6);
        BlockPos marketBell = new BlockPos(27, 1, 27);
        helper.setBlock(sign, ModBlocks.JOB_SIGN.get());
        helper.setBlock(plazaBell, Blocks.BELL);
        helper.setBlock(marketBell, Blocks.BELL);
        long[] dayTime = {MORNING};
        helper.onEachTick(() -> level.setDayTime(dayTime[0]));

        Villager villager = worker(helper, new BlockPos(3, 1, 3));
        WorkerBrains.assign(villager, global(helper, sign), global(helper, marketBell));
        villager.refreshBrain(level);

        helper.assertTrue(level.getPoiManager().exists(helper.absolutePos(sign),
                holder -> holder.is(ModPoiTypes.JOB_SIGN_KEY)), "The job sign must be a point of interest");
        helper.assertTrue(villager.getBrain().getSchedule() == ModSchedules.WORKER.get(),
                "The worker schedule must survive refreshBrain");
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.getBrain().isActive(Activity.WORK), "Expected WORK in the morning");
                    helper.assertTrue(near(helper, villager, sign, NEAR_SIGN), "The worker is not at its job sign: "
                            + villager.blockPosition());
                })
                .thenIdle(40)
                .thenExecute(() -> {
                    helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.JOB_SITE)
                            .filter(pos -> pos.equals(global(helper, sign))).isPresent(),
                            "The job sign must stay the job site near the sign (ValidateNearbyPoi)");
                    dayTime[0] = NOON;
                })
                .thenWaitUntil(() -> {
                    helper.assertTrue(villager.getBrain().isActive(Activity.MEET), "Expected MEET at noon");
                    helper.assertTrue(near(helper, villager, marketBell, NEAR_MARKET),
                            "The worker is not at the market: " + villager.blockPosition());
                })
                .thenExecute(() -> helper.assertTrue(villager.getBrain().getMemory(MemoryModuleType.MEETING_POINT)
                        .filter(pos -> pos.equals(global(helper, marketBell))).isPresent(),
                        "The market bell must stay the meeting point, not the plaza bell"))
                .thenExecute(villager::discard)
                .thenSucceed();
    }

    /**
     * Cost probe: {@value #WORKERS} workers walk from their job signs to one market bell at noon. The test writes the
     * mean and the maximum tick time before and during the walk to the log.
     */
    @GameTest(template = "arena_huge", timeoutTicks = 1600, batch = "worker_schedule_cost")
    public static void manyWorkersWalkToTheMarket(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos marketBell = new BlockPos(50, 1, 50);
        helper.setBlock(marketBell, Blocks.BELL);
        long[] dayTime = {MORNING};
        helper.onEachTick(() -> level.setDayTime(dayTime[0]));
        List<Villager> workers = new ArrayList<>();
        for (int i = 0; i < WORKERS; i++) {
            int x = 3 + (i % 12) * 4;
            int z = 3 + (i / 12) * 4;
            BlockPos sign = new BlockPos(x, 1, z);
            helper.setBlock(sign, ModBlocks.JOB_SIGN.get());
            Villager villager = worker(helper, sign.east());
            WorkerBrains.assign(villager, global(helper, sign), global(helper, marketBell));
            workers.add(villager);
        }
        long[][] samples = new long[2][];
        helper.startSequence()
                .thenIdle(120)
                .thenExecute(() -> {
                    samples[0] = level.getServer().getTickTimesNanos().clone();
                    dayTime[0] = NOON;
                })
                .thenIdle(100)
                .thenExecute(() -> samples[1] = level.getServer().getTickTimesNanos().clone())
                .thenWaitUntil(() -> {
                    long arrived = workers.stream().filter(v -> near(helper, v, marketBell, NEAR_MARKET)).count();
                    helper.assertTrue(arrived >= WORKERS * 9L / 10, "Workers at the market: " + arrived);
                })
                .thenExecute(() -> {
                    LOGGER.info("Spike #640 cost probe, {} workers: work phase mean {} ms, max {} ms; walk to the "
                            + "market mean {} ms, max {} ms", WORKERS, millis(mean(samples[0])),
                            millis(max(samples[0])), millis(mean(samples[1])), millis(max(samples[1])));
                    workers.forEach(Villager::discard);
                })
                .thenSucceed();
    }

    private static long mean(long[] values) {
        return (long) Arrays.stream(values).average().orElse(0);
    }

    private static long max(long[] values) {
        return Arrays.stream(values).max().orElse(0);
    }

    private static String millis(long nanos) {
        return String.format("%.2f", nanos / 1_000_000.0);
    }
}
