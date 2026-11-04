package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.rail;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Minecart;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the speed of the trains.
 *
 * <p>Template {@code folkhold_balance:express} (made with {@code tools/make_test_structure.py}): 48 × 5 × 48 blocks of
 * air. Each test builds a floor of stone at y = 0 and its rails at y = 1 or above.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainSpeedGameTests {
    private static final int SIZE = 48;
    private static final int LINE_Z = 2;
    private static final int SIDE_Z = 6;
    private static final int JUNCTION_X = 23;
    private static final int CURVE_X = 40;
    private static final int TURN_X = 46;
    private static final int SLOPE_Z = 8;
    private static final double EPSILON = 1.0E-6;

    private TrainSpeedGameTests() {
    }

    /**
     * A heavy locomotive with no wagons gets near its maximum speed on a straight rail, and never goes faster. The
     * pull decreases to 0 at the maximum speed, so the speed gets near it but does not reach it.
     */
    @GameTest(template = "express", timeoutTicks = 200, batch = "balance_rails")
    public static void heavyLocomotiveReachesMaxSpeed(GameTestHelper helper) {
        straight(helper, SIDE_Z);
        List<AbstractMinecart> train = LocomotiveGameTests.train(helper, ModEntities.HEAVY_LOCOMOTIVE.get(), 2.5,
                SIDE_Z, 0, false);
        Locomotive locomotive = (Locomotive) train.get(0);
        double maxSpeed = locomotive.stats().maxSpeed();
        AtomicReference<Double> fastest = fastest(helper, List.of(locomotive));
        LocomotiveGameTests.start(helper, locomotive);
        helper.runAtTickTime(60, () -> {
            helper.assertTrue(fastest.get() <= maxSpeed + EPSILON, "The speed is " + fastest.get());
            helper.assertTrue(fastest.get() >= 0.97 * maxSpeed, "The largest speed is " + fastest.get());
            LocomotiveGameTests.clear(helper, train);
            helper.succeed();
        });
    }

    /**
     * A heavy locomotive with 8 wagons goes through a junction at more than 0.5 blocks per tick, then through an S
     * curve, a curve, up a slope and down again. No minecart leaves the rails, no link breaks, and no minecart goes
     * faster than the maximum speed. The test measures the last wagon in the tick where it crosses the junction, while
     * all the minecarts are on straight rails.
     */
    @GameTest(template = "express", timeoutTicks = 400, batch = "balance_rails")
    public static void eightWagonsStayOnTheRailsAtMaxSpeed(GameTestHelper helper) {
        TrainGameTests.floor(helper, SIZE, SIZE);
        for (int x = 1; x < CURVE_X; x++) {
            rail(helper, x, LINE_Z, RailShape.EAST_WEST);
        }
        // A branch to the south makes a junction. The train goes straight through it.
        for (int z = LINE_Z + 1; z < SIDE_Z; z++) {
            rail(helper, JUNCTION_X, z, RailShape.NORTH_SOUTH);
        }
        rail(helper, CURVE_X, LINE_Z, RailShape.SOUTH_WEST);
        rail(helper, CURVE_X, LINE_Z + 1, RailShape.NORTH_EAST);
        for (int x = CURVE_X + 1; x < TURN_X; x++) {
            rail(helper, x, LINE_Z + 1, RailShape.EAST_WEST);
        }
        rail(helper, TURN_X, LINE_Z + 1, RailShape.SOUTH_WEST);
        // To the south: flat, a slope up of 1 block, flat, a slope down, flat.
        for (int z = LINE_Z + 2; z < SIZE - 1; z++) {
            if (z == SLOPE_Z) {
                rail(helper, TURN_X, z, RailShape.ASCENDING_SOUTH);
            } else if (z > SLOPE_Z && z < SLOPE_Z + 5) {
                helper.setBlock(new BlockPos(TURN_X, 1, z), Blocks.STONE);
                rail(helper, TURN_X, 2, z, RailShape.NORTH_SOUTH);
            } else if (z == SLOPE_Z + 5) {
                rail(helper, TURN_X, z, RailShape.ASCENDING_NORTH);
            } else {
                rail(helper, TURN_X, z, RailShape.NORTH_SOUTH);
            }
        }
        TrainGameTests.fixRails(helper);
        List<AbstractMinecart> train = LocomotiveGameTests.train(helper, ModEntities.HEAVY_LOCOMOTIVE.get(), 14.5,
                LINE_Z, 8, false);
        AbstractMinecart last = train.get(train.size() - 1);
        double junction = helper.absoluteVec(new Vec3(JUNCTION_X + 0.5, 0, 0)).x;
        double finish = helper.absoluteVec(new Vec3(0, 0, SLOPE_Z + 7)).z;
        double maxSpeed = ((Locomotive) train.get(0)).stats().maxSpeed();
        AtomicReference<Double> fastest = fastest(helper, train);
        AtomicReference<Vec3> before = new AtomicReference<>(last.position());
        AtomicReference<Double> atJunction = new AtomicReference<>(0.0);
        AtomicReference<Double> largest = new AtomicReference<>(0.0);
        AtomicReference<Boolean> derailed = new AtomicReference<>(false);
        helper.onEachTick(() -> {
            if (before.get().x < junction && last.getX() >= junction) {
                boolean straight = train.stream().allMatch(cart -> helper.getLevel()
                        .getBlockState(cart.getCurrentRailPosition()).getOptionalValue(RailBlock.SHAPE)
                        .equals(Optional.of(RailShape.EAST_WEST)));
                atJunction.set(straight ? last.position().subtract(before.get()).horizontalDistance() : -1.0);
            }
            before.set(last.position());
            for (int i = 0; i + 1 < train.size(); i++) {
                largest.set(Math.max(largest.get(), train.get(i).distanceTo(train.get(i + 1))));
            }
            if (train.stream().anyMatch(cart -> !cart.isOnRails() && cart.tickCount > 2)) {
                derailed.set(true);
            }
        });
        LocomotiveGameTests.start(helper, train.get(0));
        double maxDistance = BalanceConfig.coupling().maxDistance();
        helper.succeedWhen(() -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                helper.assertTrue(CartLinks.get(train.get(i)).back().equals(Optional.of(train.get(i + 1).getUUID())),
                        "The link " + i + " of the train is broken");
            }
            helper.assertTrue(!derailed.get(), "A minecart of the train leaves the rails");
            helper.assertTrue(largest.get() <= maxDistance + 0.5,
                    "The largest distance between two minecarts is " + largest.get());
            helper.assertTrue(fastest.get() <= maxSpeed + EPSILON, "A minecart goes at " + fastest.get());
            helper.assertTrue(atJunction.get() > 0.5, "The last wagon goes through the junction at "
                    + atJunction.get() + " (-1: a minecart is not on a straight rail)");
            helper.assertTrue(last.getZ() > finish, "The last wagon is at z " + last.getZ() + ", not after the slope");
            LocomotiveGameTests.clear(helper, train);
        });
    }

    /**
     * A vanilla minecart that is not in a train keeps the vanilla speed cap of 0.4 blocks per tick, also when a heavy
     * locomotive runs at more than 0.4 near it. A wagon goes back to 0.4 when its link breaks.
     */
    @GameTest(template = "express", timeoutTicks = 100, batch = "balance_rails")
    public static void vanillaMinecartKeepsVanillaSpeed(GameTestHelper helper) {
        straight(helper, LINE_Z);
        straight(helper, SIDE_Z);
        List<AbstractMinecart> train = LocomotiveGameTests.train(helper, ModEntities.HEAVY_LOCOMOTIVE.get(), 3.5,
                LINE_Z, 1, false);
        AbstractMinecart wagon = train.get(1);
        Minecart minecart = helper.spawn(EntityType.MINECART, new Vec3(2.5, CART_Y, SIDE_Z + 0.5));
        AtomicReference<Double> locomotive = fastest(helper, List.of(train.get(0)));
        AtomicReference<Double> vanilla = fastest(helper, List.of(minecart));
        AtomicReference<Double> unlinked = new AtomicReference<>(0.0);
        AtomicReference<Vec3> wagonBefore = new AtomicReference<>(wagon.position());
        // The cap of the train stays for one tick after the link breaks at tick 20.
        helper.onEachTick(() -> {
            if (helper.getTick() > 22) {
                unlinked.set(Math.max(unlinked.get(), wagon.position().subtract(wagonBefore.get()).horizontalDistance()));
            }
            wagonBefore.set(wagon.position());
        });
        LocomotiveGameTests.start(helper, train.get(0));
        helper.runAtTickTime(20, () -> {
            minecart.setDeltaMovement(2.0, 0.0, 0.0);
            helper.assertTrue(wagon.getMaxSpeedWithRail() > Locomotive.RAIL_SPEED_CAP,
                    "The speed cap of the wagon is " + wagon.getMaxSpeedWithRail());
            CartLinks.unlink(helper.getLevel(), wagon, train.get(0).getUUID(), false);
            wagon.setDeltaMovement(2.0, 0.0, 0.0);
        });
        helper.runAtTickTime(40, () -> {
            helper.assertTrue(locomotive.get() > Locomotive.RAIL_SPEED_CAP + 0.05,
                    "The locomotive goes at " + locomotive.get());
            for (AbstractMinecart cart : List.of(minecart, wagon)) {
                helper.assertTrue(Math.abs(cart.getMaxSpeedWithRail() - Locomotive.RAIL_SPEED_CAP) < EPSILON,
                        "The speed cap of a minecart is " + cart.getMaxSpeedWithRail());
            }
            helper.assertTrue(Math.abs(vanilla.get() - Locomotive.RAIL_SPEED_CAP) < EPSILON,
                    "The minecart goes at " + vanilla.get());
            helper.assertTrue(unlinked.get() > Locomotive.RAIL_SPEED_CAP - EPSILON
                    && unlinked.get() < Locomotive.RAIL_SPEED_CAP + EPSILON, "The unlinked wagon goes at " + unlinked.get());
            minecart.discard();
            LocomotiveGameTests.clear(helper, train);
            helper.succeed();
        });
    }

    /** Places a floor and straight rails from x = 1 to the end of the template. */
    private static void straight(GameTestHelper helper, int z) {
        TrainGameTests.floor(helper, SIZE, SIDE_Z + 2);
        for (int x = 1; x < SIZE - 1; x++) {
            rail(helper, x, z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
    }

    /** The largest horizontal movement of one of the minecarts in one tick, from now. */
    private static AtomicReference<Double> fastest(GameTestHelper helper, List<AbstractMinecart> carts) {
        AtomicReference<Double> fastest = new AtomicReference<>(0.0);
        List<Vec3> last = new ArrayList<>(carts.stream().map(AbstractMinecart::position).toList());
        helper.onEachTick(() -> {
            for (int i = 0; i < carts.size(); i++) {
                Vec3 position = carts.get(i).position();
                fastest.set(Math.max(fastest.get(), position.subtract(last.get(i)).horizontalDistance()));
                last.set(i, position);
            }
        });
        return fastest;
    }
}
