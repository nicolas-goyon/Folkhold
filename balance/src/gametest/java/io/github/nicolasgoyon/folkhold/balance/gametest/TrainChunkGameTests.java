package io.github.nicolasgoyon.folkhold.balance.gametest;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.rail.TrainChunks;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the rules of the optional chunk loading of the trains (config value {@code rails.loadChunks}):
 * the tickets need a movement, they cost fuel, and the number of locomotives that load chunks has a limit.
 *
 * <p>Template {@code folkhold_balance:express}: 48 × 5 × 48 blocks of air. Each test changes the shared config values,
 * so each test has its own batch and sets the values again at its start and at its end.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainChunkGameTests {
    private static final int SIZE = 48;
    private static final int LINE_Z = 2;
    private static final int BRAKE_X = 10;

    private TrainChunkGameTests() {
    }

    /** Sets the config values of the chunk loading to their defaults. */
    static void resetOptions() {
        BalanceConfig.TRAIN_LOAD_CHUNKS.set(false);
        BalanceConfig.TRAIN_MAX_LOADING.set(BalanceConfig.TRAIN_MAX_LOADING.getDefault());
    }

    /**
     * A running locomotive with fuel stops on an unpowered powered rail. After {@link TrainChunks#MOVE_GRACE} ticks,
     * it gives no tickets, and it gives none after that.
     */
    @GameTest(template = "express", timeoutTicks = 400, batch = "balance_rails_chunks_move")
    public static void runningLocomotiveThatCannotMoveGivesNoTickets(GameTestHelper helper) {
        resetOptions();
        BalanceConfig.TRAIN_LOAD_CHUNKS.set(true);
        brakeTrack(helper);
        List<AbstractMinecart> train = LocomotiveGameTests.train(helper, ModEntities.FURNACE_LOCOMOTIVE.get(), 2.5,
                LINE_Z, 0, false);
        Locomotive locomotive = (Locomotive) train.get(0);
        List<Vec3> stop = new ArrayList<>();
        helper.startSequence()
                .thenExecute(() -> locomotive.setRunning(true, helper.absoluteVec(new Vec3(0.0, 1.0, LINE_Z + 0.5))))
                .thenWaitUntil(() -> helper.assertTrue(TrainChunks.isLoading(locomotive),
                        "The moving locomotive gives no tickets"))
                .thenWaitUntil(() -> helper.assertTrue(stopped(helper, locomotive),
                        "The locomotive does not stop on the powered rail"))
                .thenExecute(() -> stop.add(locomotive.position()))
                .thenIdle(TrainChunks.MOVE_GRACE + 2)
                .thenExecute(() -> helper.assertTrue(!TrainChunks.isLoading(locomotive),
                        "The locomotive that does not move gives tickets"))
                .thenIdle(2 * TrainChunks.LIFESPAN)
                .thenExecute(() -> {
                    helper.assertTrue(!TrainChunks.isLoading(locomotive), "The locomotive gives tickets again");
                    helper.assertTrue(locomotive.isRunning() && locomotive.fuel() > 0.0,
                            "The locomotive does not run or has no fuel");
                    helper.assertTrue(locomotive.position().distanceTo(stop.get(0)) < 1.0E-3,
                            "The locomotive moves after its stop");
                    resetOptions();
                    LocomotiveGameTests.clear(helper, train);
                })
                .thenSucceed();
    }

    /**
     * A running locomotive stops on an unpowered powered rail. While it still gives tickets, it burns its fuel in each
     * tick as at its maximum speed. When the fuel ends, it gives no tickets.
     */
    @GameTest(template = "express", timeoutTicks = 400, batch = "balance_rails_chunks_fuel")
    public static void locomotiveThatGivesTicketsBurnsFuelAtItsMaximumSpeed(GameTestHelper helper) {
        resetOptions();
        BalanceConfig.TRAIN_LOAD_CHUNKS.set(true);
        brakeTrack(helper);
        List<AbstractMinecart> train = LocomotiveGameTests.train(helper, ModEntities.FURNACE_LOCOMOTIVE.get(), 2.5,
                LINE_Z, 0, false);
        Locomotive locomotive = (Locomotive) train.get(0);
        double perTick = locomotive.stats().maxSpeed() * locomotive.stats().fuelPerBlock();
        double fuel = 5.5 * perTick;
        helper.startSequence()
                .thenExecute(() -> locomotive.setRunning(true, helper.absoluteVec(new Vec3(0.0, 1.0, LINE_Z + 0.5))))
                .thenWaitUntil(() -> helper.assertTrue(stopped(helper, locomotive),
                        "The locomotive does not stop on the powered rail"))
                .thenIdle(1)
                .thenExecute(() -> {
                    helper.assertTrue(TrainChunks.isLoading(locomotive), "The locomotive gives no tickets");
                    locomotive.setFuel(fuel);
                })
                .thenIdle(1)
                .thenExecute(() -> {
                    double burned = fuel - locomotive.fuel();
                    helper.assertTrue(Math.abs(burned - perTick) < 1.0E-6, "The locomotive that does not move burns "
                            + burned + " fuel in one tick, expected " + perTick);
                })
                .thenWaitUntil(() -> helper.assertTrue(locomotive.fuel() == 0.0, "The fuel does not end"))
                .thenIdle(2)
                .thenExecute(() -> {
                    helper.assertTrue(!TrainChunks.isLoading(locomotive), "The locomotive with no fuel gives tickets");
                    resetOptions();
                    LocomotiveGameTests.clear(helper, train);
                })
                .thenSucceed();
    }

    /** True when the locomotive is on an unpowered powered rail and has no motion. */
    private static boolean stopped(GameTestHelper helper, Locomotive locomotive) {
        return helper.getLevel().getBlockState(locomotive.getCurrentRailPosition()).is(Blocks.POWERED_RAIL)
                && locomotive.getDeltaMovement().lengthSqr() == 0.0;
    }

    /** A floor, a line of rails along X with 5 unpowered powered rails from {@link #BRAKE_X}. */
    private static void brakeTrack(GameTestHelper helper) {
        TrainGameTests.floor(helper, SIZE, SIZE);
        for (int x = 1; x < SIZE - 1; x++) {
            if (x < BRAKE_X || x > BRAKE_X + 4) {
                TrainGameTests.rail(helper, x, LINE_Z, RailShape.EAST_WEST);
            }
        }
        TrainGameTests.fixRails(helper);
        for (int x = BRAKE_X; x <= BRAKE_X + 4; x++) {
            helper.setBlock(new BlockPos(x, 1, LINE_Z),
                    Blocks.POWERED_RAIL.defaultBlockState().setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST));
        }
    }

    /**
     * {@code maxLoadingTrains} is 2 and 3 locomotives run: only 2 give tickets. When one of them stops, the third
     * locomotive gets its place.
     */
    @GameTest(template = "express", timeoutTicks = 400, batch = "balance_rails_chunks_limit")
    public static void onlyMaxLoadingTrainsLocomotivesLoadChunks(GameTestHelper helper) {
        resetOptions();
        BalanceConfig.TRAIN_LOAD_CHUNKS.set(true);
        BalanceConfig.TRAIN_MAX_LOADING.set(2);
        TrainGameTests.floor(helper, SIZE, SIZE);
        List<List<AbstractMinecart>> trains = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            int z = LINE_Z + 2 * i;
            for (int x = 1; x < SIZE - 1; x++) {
                TrainGameTests.rail(helper, x, z, RailShape.EAST_WEST);
            }
            trains.add(LocomotiveGameTests.train(helper, ModEntities.FURNACE_LOCOMOTIVE.get(), 2.5, z, 0, false));
        }
        TrainGameTests.fixRails(helper);
        List<Locomotive> locomotives = trains.stream().map(train -> (Locomotive) train.get(0)).toList();
        List<Locomotive> waiting = new ArrayList<>();
        helper.startSequence()
                .thenExecute(() -> locomotives.forEach(locomotive -> locomotive.setRunning(true,
                        locomotive.position().add(-1.0, 0.0, 0.0))))
                // More than the lifespan: the places of the locomotives of an earlier test are free.
                .thenIdle(TrainChunks.LIFESPAN + 10)
                .thenExecute(() -> {
                    List<Locomotive> loading = locomotives.stream().filter(TrainChunks::isLoading).toList();
                    helper.assertTrue(loading.size() == 2, loading.size() + " locomotives give tickets, expected 2");
                    waiting.addAll(locomotives.stream().filter(locomotive -> !loading.contains(locomotive)).toList());
                    loading.get(0).setRunning(false, Vec3.ZERO);
                    waiting.add(loading.get(0));
                })
                .thenWaitUntil(() -> helper.assertTrue(TrainChunks.isLoading(waiting.get(0)),
                        "The third locomotive does not get the free place"))
                .thenExecute(() -> {
                    helper.assertTrue(!TrainChunks.isLoading(waiting.get(1)), "The stopped locomotive gives tickets");
                    helper.assertTrue(locomotives.stream().filter(TrainChunks::isLoading).count() == 2,
                            "More than 2 locomotives give tickets");
                    resetOptions();
                    trains.forEach(train -> LocomotiveGameTests.clear(helper, train));
                })
                .thenSucceed();
    }
}
