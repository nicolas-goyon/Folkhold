package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.BENCH_Z;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.CartEnds;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.TrainPush;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PoweredRailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * In-game tests for a train with no locomotive and no furnace minecart with fuel (see {@link TrainPush}). The
 * minecart that gets a push drives the train, also when it is the back minecart of the train.
 *
 * <p>Each test waits for a state of the train with a real-time limit of {@link #STATE_SECONDS}: the GameTest server
 * does not wait between two ticks, so a number of ticks is not a real time.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainPushGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The real-time limit of a wait for a state of a train, in seconds. */
    private static final long STATE_SECONDS = 30;
    /** The tick limit of a test that waits with {@link #STATE_SECONDS}: a safety limit only. */
    private static final int STATE_TEST_TICKS = 2_000_000;
    /** The distance that each minecart of the train moves in the direction of the push, in blocks. */
    private static final double MOVE_DISTANCE = 4.0;
    /** The number of ticks that the player pushes the back minecart. */
    private static final int PUSH_TICKS = 20;
    /** The smallest gap between two linked minecarts: the buffer gap minus a margin of 0.1 blocks, in blocks. */
    private static final double MIN_GAP = 0.05;
    /** The distance from the player to the minecart that it pushes, in blocks. */
    private static final double PUSH_DISTANCE = 0.5;

    private TrainPushGameTests() {
    }

    /** A player pushes the back minecart of a train of 3 chest minecarts to the front: all the train moves. */
    @GameTest(template = "bench", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void pushOnTheBackMinecartMovesTheTrainForward(GameTestHelper helper) {
        playerPushMovesTheTrain(helper, 3.5, 1.0);
    }

    /** A player pulls the back minecart of a train of 3 chest minecarts away from the train: all the train moves. */
    @GameTest(template = "bench", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void pushOnTheBackMinecartMovesTheTrainBackward(GameTestHelper helper) {
        playerPushMovesTheTrain(helper, 15.5, -1.0);
    }

    /**
     * A train of 3 chest minecarts with no fuel. A player pushes the back minecart (the last minecart of the links)
     * for {@link #PUSH_TICKS} ticks with the vanilla push of an entity.
     *
     * @param backX     the X of the back minecart, relative to the test. The other minecarts are to the east.
     * @param direction 1 when the player pushes to the east (to the front), -1 when the player pushes to the west
     */
    private static void playerPushMovesTheTrain(GameTestHelper helper, double backX, double direction) {
        bench(helper);
        List<AbstractMinecart> train = spawnTrain(helper, backX);
        AbstractMinecart back = train.get(train.size() - 1);
        ServerPlayer player = TrainGameTests.survivalPlayer(helper,
                new Vec3(backX - direction * PUSH_DISTANCE, CART_Y, BENCH_Z + 0.5));
        int[] pushes = {0};
        helper.onEachTick(() -> {
            if (pushes[0] < PUSH_TICKS && !back.isRemoved()) {
                pushes[0]++;
                player.moveTo(back.getX() - direction * PUSH_DISTANCE, back.getY(), back.getZ());
                back.push(player);
            }
        });
        waitForTravel(helper, train, direction, "push " + (direction > 0 ? "forward" : "backward"));
    }

    /**
     * A powered rail under the back minecart of a train of 3 chest minecarts with no fuel. A block to the west of the
     * powered rail starts the minecart to the east (vanilla), then the powered rail accelerates it. All the train
     * moves.
     */
    @GameTest(template = "bench", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void poweredRailUnderTheBackMinecartMovesTheTrain(GameTestHelper helper) {
        bench(helper);
        helper.setBlock(new BlockPos(1, 1, BENCH_Z), Blocks.STONE);
        helper.setBlock(new BlockPos(2, 0, BENCH_Z), Blocks.REDSTONE_BLOCK);
        helper.setBlock(new BlockPos(2, 1, BENCH_Z), Blocks.POWERED_RAIL.defaultBlockState()
                .setValue(PoweredRailBlock.SHAPE, RailShape.EAST_WEST).setValue(PoweredRailBlock.POWERED, true));
        List<AbstractMinecart> train = spawnTrain(helper, 2.5);
        waitForTravel(helper, train, 1.0, "powered rail");
    }

    /** A floor and a straight rail from x = 1 to x = 22. */
    private static void bench(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
    }

    /**
     * Spawns 3 chest minecarts at the rest gap and links them from east to west. The front minecart of the links is
     * at the east end, and the back minecart is at {@code backX}.
     *
     * @return the minecarts in the order of the links: the front minecart first
     */
    private static List<AbstractMinecart> spawnTrain(GameTestHelper helper, double backX) {
        double spacing = BalanceConfig.coupling().restGap() + 2.0 * CartEnds.HALF_LENGTH;
        List<AbstractMinecart> train = List.of(
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(backX + 2 * spacing, CART_Y, BENCH_Z + 0.5)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(backX + spacing, CART_Y, BENCH_Z + 0.5)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(backX, CART_Y, BENCH_Z + 0.5)));
        for (int i = 0; i + 1 < train.size(); i++) {
            TrainGameTests.assertLinked(helper, train.get(i), train.get(i + 1));
        }
        return train;
    }

    /**
     * Waits until each minecart of the train moves {@link #MOVE_DISTANCE} blocks in a direction, with a real-time
     * limit of {@link #STATE_SECONDS}. The links stay, and the gap of each link stays at {@link #MIN_GAP} or more.
     *
     * @param direction 1 for the east, -1 for the west
     * @param name      the name of the case in the log line
     */
    private static void waitForTravel(GameTestHelper helper, List<AbstractMinecart> train, double direction,
                                      String name) {
        double[] startX = train.stream().mapToDouble(AbstractMinecart::getX).toArray();
        double[] smallestGap = {Double.MAX_VALUE};
        helper.onEachTick(() -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                smallestGap[0] = Math.min(smallestGap[0], CartEnds.gap(train.get(i), train.get(i + 1)));
            }
        });
        Runnable moves = () -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                helper.assertTrue(CartLinks.get(train.get(i)).back().equals(Optional.of(train.get(i + 1).getUUID())),
                        "The link " + i + " of the train is broken");
            }
            helper.assertTrue(smallestGap[0] >= MIN_GAP, "The smallest gap is " + smallestGap[0] + " blocks");
            for (int i = 0; i < train.size(); i++) {
                double travel = (train.get(i).getX() - startX[i]) * direction;
                helper.assertTrue(travel >= MOVE_DISTANCE, "The minecart " + i + " moves " + travel + " blocks");
            }
        };
        long[] deadline = {0L};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    long now = System.nanoTime();
                    if (deadline[0] == 0L) {
                        deadline[0] = now + STATE_SECONDS * 1_000_000_000L;
                    }
                    if (now - deadline[0] < 0) {
                        moves.run();
                    }
                })
                .thenExecute(moves)
                .thenExecute(() -> {
                    LOGGER.info("Train push ({}): smallest gap {} blocks", name, smallestGap[0]);
                    TrainGameTests.clear(helper, train);
                })
                .thenSucceed();
    }
}
