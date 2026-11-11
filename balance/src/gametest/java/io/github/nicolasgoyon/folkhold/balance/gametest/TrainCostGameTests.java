package io.github.nicolasgoyon.folkhold.balance.gametest;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.rail.TrainChunks;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * The server cost of 20 trains of 16 minecarts (a furnace locomotive and 15 chest minecarts: {@code maxCarts} is 16).
 *
 * <p>The test measures with {@link System#nanoTime()} and writes the result to the log:
 * <ul>
 *   <li>the pass of the trains: the time between a listener of {@link LevelTickEvent.Post} with the highest priority
 *       and a listener with the lowest priority. This time also contains the other listeners of this event;</li>
 *   <li>the ticks of the locomotives: their vanilla movement and their drive (forces, look-ahead, speed cap);</li>
 *   <li>the ticks of the wagons: their vanilla movement with the speed cap of the train.</li>
 * </ul>
 * The code of the trains is the pass of the trains and the ticks of the locomotives. Its target is 2 ms for each tick.
 * The test fails only above {@link #FAIL_MS}, because the speed of the test machines is not always the same.
 *
 * <p>A second test makes the same measure with the option {@code loadChunks}.
 *
 * <p>Template {@code folkhold_balance:express}: 48 × 5 × 48 blocks of air.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainCostGameTests {
    private static final int SIZE = 48;
    private static final int TRAINS = 20;
    private static final int WAGONS = 15;
    private static final int WARMUP_TICKS = 20;
    private static final int MEASURED_TICKS = 40;
    /** The test fails when the code of the trains uses more than this time for each tick, in milliseconds. */
    private static final double FAIL_MS = 10.0;

    private TrainCostGameTests() {
    }

    @GameTest(template = "express", timeoutTicks = 200, batch = "balance_rails_cost")
    public static void twentyTrainsOfSixteenMinecarts(GameTestHelper helper) {
        TrainChunkGameTests.resetOptions(helper);
        measure(helper, false);
    }

    /** The same measure with the option {@code loadChunks}: each locomotive gives its chunk tickets each tick. */
    @GameTest(template = "express", timeoutTicks = 200, batch = "balance_rails_cost_chunks")
    public static void twentyTrainsOfSixteenMinecartsThatLoadChunks(GameTestHelper helper) {
        TrainChunkGameTests.resetOptions(helper);
        BalanceTestSupport.setConfigForTest(helper, BalanceConfig.TRAIN_LOAD_CHUNKS, true);
        BalanceTestSupport.setConfigForTest(helper, BalanceConfig.TRAIN_MAX_LOADING, TRAINS);
        measure(helper, true);
    }

    private static void measure(GameTestHelper helper, boolean loadChunks) {
        TrainGameTests.floor(helper, SIZE, SIZE);
        for (int i = 0; i < TRAINS; i++) {
            for (int x = 1; x < SIZE - 1; x++) {
                TrainGameTests.rail(helper, x, 2 + 2 * i, RailShape.EAST_WEST);
            }
        }
        TrainGameTests.fixRails(helper);
        List<List<AbstractMinecart>> trains = new ArrayList<>();
        Set<Entity> locomotives = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Entity> wagons = Collections.newSetFromMap(new IdentityHashMap<>());
        for (int i = 0; i < TRAINS; i++) {
            List<AbstractMinecart> train = LocomotiveGameTests.train(helper, ModEntities.FURNACE_LOCOMOTIVE.get(),
                    24.5, 2 + 2 * i, WAGONS, false);
            trains.add(train);
            locomotives.add(train.get(0));
            wagons.addAll(train.subList(1, train.size()));
        }
        Vec3 start = trains.get(0).get(0).position();
        // Index 0: start of the measured part. Then the sums for the pass, the locomotives and the wagons.
        long[] times = new long[5];
        boolean[] measuring = {false};
        Consumer<LevelTickEvent.Post> passStart = event -> {
            if (event.getLevel() == helper.getLevel()) {
                times[0] = System.nanoTime();
            }
        };
        Consumer<LevelTickEvent.Post> passEnd = event -> {
            if (measuring[0] && event.getLevel() == helper.getLevel()) {
                times[1] += System.nanoTime() - times[0];
            }
        };
        Consumer<EntityTickEvent.Pre> tickStart = event -> times[4] = System.nanoTime();
        Consumer<EntityTickEvent.Post> tickEnd = event -> {
            if (measuring[0] && locomotives.contains(event.getEntity())) {
                times[2] += System.nanoTime() - times[4];
            } else if (measuring[0] && wagons.contains(event.getEntity())) {
                times[3] += System.nanoTime() - times[4];
            }
        };
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, passStart);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, passEnd);
        NeoForge.EVENT_BUS.addListener(EventPriority.LOWEST, tickStart);
        NeoForge.EVENT_BUS.addListener(EventPriority.HIGHEST, tickEnd);
        helper.startSequence()
                .thenExecute(() -> trains.forEach(train -> ((Locomotive) train.get(0)).setRunning(true, Vec3.ZERO)))
                .thenIdle(WARMUP_TICKS)
                .thenExecute(() -> measuring[0] = true)
                .thenIdle(MEASURED_TICKS)
                .thenExecute(() -> {
                    measuring[0] = false;
                    NeoForge.EVENT_BUS.unregister(passStart);
                    NeoForge.EVENT_BUS.unregister(passEnd);
                    NeoForge.EVENT_BUS.unregister(tickStart);
                    NeoForge.EVENT_BUS.unregister(tickEnd);
                    double pass = millisPerTick(times[1]);
                    double drive = millisPerTick(times[2]);
                    double wagonTicks = millisPerTick(times[3]);
                    long loading = trains.stream().filter(train -> TrainChunks.isLoading(train.get(0))).count();
                    FolkholdBalance.LOGGER.info("Train cost, {} trains of {} minecarts, loadChunks {} ({} give"
                                    + " tickets), {} ticks: pass of the trains {} ms, ticks of the locomotives {} ms,"
                                    + " ticks of the wagons {} ms, for each tick",
                            TRAINS, WAGONS + 1, loadChunks, loading, MEASURED_TICKS, String.format("%.3f", pass),
                            String.format("%.3f", drive), String.format("%.3f", wagonTicks));
                    double moved = trains.get(0).get(0).position().distanceTo(start);
                    helper.assertTrue(moved > 4.0, "The first train moves only " + moved + " blocks");
                    for (List<AbstractMinecart> train : trains) {
                        for (int i = 0; i + 1 < train.size(); i++) {
                            helper.assertTrue(CartLinks.get(train.get(i)).back().isPresent(), "A link breaks");
                        }
                    }
                    helper.assertTrue(pass + drive < FAIL_MS, "The code of the trains uses " + (pass + drive)
                            + " ms for each tick");
                    helper.assertTrue(loading == (loadChunks ? TRAINS : 0), loading + " locomotives give tickets");
                    TrainChunkGameTests.resetOptions(helper);
                    trains.forEach(train -> LocomotiveGameTests.clear(helper, train));
                })
                .thenSucceed();
    }

    private static double millisPerTick(long nanos) {
        return nanos / 1.0E6 / MEASURED_TICKS;
    }
}
