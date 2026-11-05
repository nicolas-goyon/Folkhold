package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.BENCH_Z;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;

import com.mojang.logging.LogUtils;
import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.CartEnds;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import java.util.List;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * In-game tests for the gap between two linked minecarts (see {@link CartEnds}). A furnace minecart pulls its wagons,
 * and the test examines the gap of each link and the distance between the ends of its minecarts in each tick.
 *
 * <p>Each test waits for the end of the travel with a real-time limit of {@link #STATE_SECONDS}: the GameTest server
 * does not wait between two ticks, so a number of ticks is not a real time.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainGapGameTests {
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The real-time limit of a wait for the end of the travel, in seconds. */
    private static final long STATE_SECONDS = 30;
    /** The tick limit of a test that waits with {@link #STATE_SECONDS}: a safety limit only. */
    private static final int STATE_TEST_TICKS = 2_000_000;
    /** The gap that the links keep, in blocks. */
    private static final double TARGET_GAP = 0.2;
    /** The largest difference between the gap of a link and {@link #TARGET_GAP} on a straight rail, in blocks. */
    private static final double STRAIGHT_TOLERANCE = 0.1;
    /** The largest difference between the gap of a link and {@link #TARGET_GAP} in a curve and on a slope. */
    private static final double CURVE_TOLERANCE = 0.15;
    /** The smallest distance between the ends of two linked minecarts: the minecarts do not touch, in blocks. */
    private static final double MIN_END_DISTANCE = 0.05;
    /**
     * The largest distance between the ends of two linked minecarts, in blocks. On a curve rail, the vanilla minecart
     * turns 45 degrees, so its end goes away from the other minecart.
     */
    private static final double MAX_END_DISTANCE = 0.75;
    /** The distance that the last wagon moves before the test examines the gaps, in blocks. */
    private static final double START_DISTANCE = 2.0;
    /** The row of the straight part of the track. */
    private static final int TRACK_Z = 2;
    /** The column of the curve and of the second straight part of the track. */
    private static final int TRACK_TURN_X = 25;
    /** The row of the slope of the track, after the curve. The rails after the slope are at y = 2. */
    private static final int TRACK_SLOPE_Z = 10;

    private TrainGapGameTests() {
    }

    /** A furnace minecart pulls 2 chest minecarts on a straight rail. Each link keeps a gap of about 0.2 blocks. */
    @GameTest(template = "bench", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void linkedMinecartsKeepAGapOnAStraightRail(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
        List<AbstractMinecart> train = spawnTrain(helper, 6.5, BENCH_Z + 0.5);
        double finish = helper.absoluteVec(new Vec3(17.0, 0.0, 0.0)).x;
        travel(helper, train, () -> train.get(train.size() - 1).getX() > finish, STRAIGHT_TOLERANCE, "straight");
    }

    /**
     * A furnace minecart pulls 2 chest minecarts on a straight rail, through a curve, then up a slope. Each link
     * keeps a gap of about 0.2 blocks, and no minecart leaves the rails.
     */
    @GameTest(template = "track", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void linkedMinecartsKeepAGapInACurve(GameTestHelper helper) {
        TrainGameTests.floor(helper, 32, 32);
        for (int x = 1; x < TRACK_TURN_X; x++) {
            TrainGameTests.rail(helper, x, TRACK_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.rail(helper, TRACK_TURN_X, TRACK_Z, RailShape.SOUTH_WEST);
        for (int z = TRACK_Z + 1; z < TRACK_SLOPE_Z; z++) {
            TrainGameTests.rail(helper, TRACK_TURN_X, z, RailShape.NORTH_SOUTH);
        }
        TrainGameTests.rail(helper, TRACK_TURN_X, 1, TRACK_SLOPE_Z, RailShape.ASCENDING_SOUTH);
        for (int z = TRACK_SLOPE_Z + 1; z < 31; z++) {
            helper.setBlock(new BlockPos(TRACK_TURN_X, 1, z), Blocks.STONE);
            TrainGameTests.rail(helper, TRACK_TURN_X, 2, z, RailShape.NORTH_SOUTH);
        }
        TrainGameTests.fixRails(helper);
        List<AbstractMinecart> train = spawnTrain(helper, 17.5, TRACK_Z + 0.5);
        double finish = helper.absoluteVec(new Vec3(0.0, 0.0, TRACK_SLOPE_Z + 4)).z;
        travel(helper, train, () -> train.get(train.size() - 1).getZ() > finish, CURVE_TOLERANCE, "curve");
    }

    /**
     * Spawns a furnace minecart and 2 chest minecarts to the west of it at the rest gap, links them, and gives coal
     * to the furnace minecart. The train goes to the east.
     */
    private static List<AbstractMinecart> spawnTrain(GameTestHelper helper, double furnaceX, double z) {
        double spacing = BalanceConfig.coupling().restGap() + 2.0 * CartEnds.HALF_LENGTH;
        MinecartFurnace furnace = helper.spawn(EntityType.FURNACE_MINECART, new Vec3(furnaceX, CART_Y, z));
        List<AbstractMinecart> train = List.of(furnace,
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(furnaceX - spacing, CART_Y, z)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(furnaceX - 2 * spacing, CART_Y, z)));
        for (int i = 0; i + 1 < train.size(); i++) {
            TrainGameTests.assertLinked(helper, train.get(i), train.get(i + 1));
        }
        TrainGameTests.fuel(helper, furnace, new Vec3(furnaceX - 1.0, CART_Y, z));
        return train;
    }

    /**
     * Examines the gap of each link in each tick after the last wagon moves {@link #START_DISTANCE} blocks, until the
     * train gets to its finish, with a real-time limit of {@link #STATE_SECONDS}. The links must stay, no minecart
     * leaves the rails, each gap stays near {@link #TARGET_GAP}, and the ends of two linked minecarts do not touch.
     */
    private static void travel(GameTestHelper helper, List<AbstractMinecart> train, BooleanSupplier finished,
                               double tolerance, String name) {
        AbstractMinecart last = train.get(train.size() - 1);
        Vec3 start = last.position();
        double[] range = {Double.MAX_VALUE, -Double.MAX_VALUE};
        double[] ends = {Double.MAX_VALUE, -Double.MAX_VALUE};
        boolean[] derailed = {false};
        helper.onEachTick(() -> {
            if (finished.getAsBoolean()) {
                // The train stops at the end of the rails after the finish: the test does not examine it.
                return;
            }
            if (train.stream().anyMatch(cart -> !cart.isOnRails() && cart.tickCount > 2)) {
                derailed[0] = true;
            }
            if (last.position().distanceTo(start) < START_DISTANCE) {
                return;
            }
            for (int i = 0; i + 1 < train.size(); i++) {
                double gap = CartEnds.gap(train.get(i), train.get(i + 1));
                range[0] = Math.min(range[0], gap);
                range[1] = Math.max(range[1], gap);
                double endDistance = CartEnds.endDistance(train.get(i), train.get(i + 1));
                ends[0] = Math.min(ends[0], endDistance);
                ends[1] = Math.max(ends[1], endDistance);
            }
        });
        Runnable state = () -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                helper.assertTrue(CartLinks.get(train.get(i)).back().equals(Optional.of(train.get(i + 1).getUUID())),
                        "The link " + i + " of the train is broken");
            }
            helper.assertTrue(!derailed[0], "A minecart of the train leaves the rails");
            helper.assertTrue(finished.getAsBoolean(), "The last wagon is at " + last.position()
                    + ", not at the finish");
            helper.assertTrue(range[0] >= TARGET_GAP - tolerance && range[1] <= TARGET_GAP + tolerance,
                    "The gaps are from " + range[0] + " to " + range[1] + " blocks");
            helper.assertTrue(ends[0] >= MIN_END_DISTANCE && ends[1] <= MAX_END_DISTANCE,
                    "The distances between the ends are from " + ends[0] + " to " + ends[1] + " blocks");
        };
        long[] deadline = {0L};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    long now = System.nanoTime();
                    if (deadline[0] == 0L) {
                        deadline[0] = now + STATE_SECONDS * 1_000_000_000L;
                    }
                    if (now - deadline[0] < 0) {
                        state.run();
                    }
                })
                .thenExecute(state)
                .thenExecute(() -> {
                    LOGGER.info("Gaps of the {} train: from {} to {} blocks; distances between the ends: from {} to {}"
                            + " blocks", name, range[0], range[1], ends[0], ends[1]);
                    TrainGameTests.clear(helper, train);
                })
                .thenSucceed();
    }
}
