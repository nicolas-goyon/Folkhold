package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.BENCH_Z;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the furnace locomotive.
 *
 * <p>Template {@code folkhold_balance:climb} (made with {@code tools/make_test_structure.py}): 48 × 16 × 5 blocks of
 * air. The test builds flat rails at y = 1 from x = 1 to x = 29, then a slope of 1 block in 1 that goes up 12 blocks
 * to the east, then flat rails at y = 13.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class LocomotiveGameTests {
    private static final int CLIMB_Z = 2;
    private static final int SLOPE_X = 30;
    private static final int SLOPE_HEIGHT = 12;
    private static final double SPACING = 1.5;

    private LocomotiveGameTests() {
    }

    /** A locomotive with 2 full wagons goes up the slope. */
    @GameTest(template = "climb", timeoutTicks = 800, batch = "balance_rails")
    public static void twoFullWagonsClimbTheSlope(GameTestHelper helper) {
        climbTrack(helper);
        List<AbstractMinecart> train = train(helper, 24.5, CLIMB_Z, 2, true);
        start(helper, train.get(0));
        double top = helper.absoluteVec(new Vec3(0, SLOPE_HEIGHT + 1, 0)).y;
        helper.succeedWhen(() -> {
            helper.assertTrue(train.get(0).getY() >= top, "The locomotive is at y " + train.get(0).getY());
            clear(helper, train);
        });
    }

    /** A locomotive with 12 full wagons stops on the slope and does not slide back. */
    @GameTest(template = "climb", timeoutTicks = 500, batch = "balance_rails")
    public static void twelveFullWagonsStopOnTheSlope(GameTestHelper helper) {
        climbTrack(helper);
        List<AbstractMinecart> train = train(helper, 28.5, CLIMB_Z, 12, true);
        Locomotive locomotive = (Locomotive) train.get(0);
        start(helper, locomotive);
        AtomicReference<Double> farthest = new AtomicReference<>(locomotive.getX());
        helper.onEachTick(() -> farthest.set(Math.max(farthest.get(), locomotive.getX())));
        double top = helper.absoluteVec(new Vec3(0, SLOPE_HEIGHT + 1, 0)).y;
        double slopeStart = helper.absoluteVec(new Vec3(SLOPE_X, 0, 0)).x;
        helper.runAtTickTime(400, () -> {
            helper.assertTrue(locomotive.getX() > slopeStart, "The locomotive does not reach the slope");
            helper.assertTrue(locomotive.getY() < top, "The locomotive reaches the top of the slope");
            helper.assertTrue(locomotive.getDeltaMovement().horizontalDistance() < 1.0E-6,
                    "The locomotive does not stop");
            helper.assertTrue(locomotive.getX() > farthest.get() - 0.2, "The train slides back");
            clear(helper, train);
            helper.succeed();
        });
    }

    /**
     * A locomotive with fuel for 2 blocks starts with a right click, stops when the fuel ends, and continues when the
     * player adds coal.
     */
    @GameTest(template = "bench", timeoutTicks = 400, batch = "balance_rails")
    public static void noFuelStopsAndCoalRestarts(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
        List<AbstractMinecart> train = train(helper, 4.5, BENCH_Z, 1, false);
        Locomotive locomotive = (Locomotive) train.get(0);
        locomotive.setFuel(2 * locomotive.stats().fuelPerBlock());
        double startX = locomotive.getX();
        start(helper, locomotive);
        AtomicReference<Double> stopX = new AtomicReference<>(0.0);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    helper.assertTrue(locomotive.fuel() == 0.0, "The fuel does not end");
                    helper.assertTrue(locomotive.getDeltaMovement().horizontalDistance() < 1.0E-6,
                            "The locomotive with no fuel does not stop");
                })
                .thenExecute(() -> {
                    helper.assertTrue(locomotive.getX() - startX < 10.0,
                            "The locomotive moves " + (locomotive.getX() - startX) + " blocks with fuel for 2");
                    stopX.set(locomotive.getX());
                    ServerPlayer player = TrainGameTests.survivalPlayer(helper, new Vec3(1.5, 1.0, BENCH_Z + 1.5));
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 2));
                    player.interactOn(locomotive, InteractionHand.MAIN_HAND);
                    helper.assertTrue(player.getMainHandItem().getCount() == 1, "The fuel does not use 1 coal");
                    helper.getLevel().getServer().getPlayerList().remove(player);
                })
                .thenWaitUntil(() -> helper.assertTrue(locomotive.getX() > stopX.get() + 1.0,
                        "The locomotive does not continue with coal"))
                .thenExecute(() -> clear(helper, train))
                .thenSucceed();
    }

    /**
     * A lava bucket gives its fuel and the player gets the empty bucket. A chest minecart with a loot table behind
     * the running locomotive keeps its loot table: the mass of the train does not generate the loot.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void lavaBucketAndLootWagon(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
        List<AbstractMinecart> train = train(helper, 4.5, BENCH_Z, 1, false);
        Locomotive locomotive = (Locomotive) train.get(0);
        MinecartChest wagon = (MinecartChest) train.get(1);
        wagon.setLootTable(BuiltInLootTables.ABANDONED_MINESHAFT);
        locomotive.setFuel(0.0);
        ServerPlayer player = TrainGameTests.survivalPlayer(helper, new Vec3(1.5, 1.0, BENCH_Z + 1.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.LAVA_BUCKET));
        player.interactOn(locomotive, InteractionHand.MAIN_HAND);
        helper.assertTrue(player.getMainHandItem().is(Items.BUCKET), "The player does not get the empty bucket");
        helper.assertTrue(locomotive.fuel() == 20000.0, "The lava bucket gives " + locomotive.fuel() + " fuel");
        helper.getLevel().getServer().getPlayerList().remove(player);
        start(helper, locomotive);
        helper.runAtTickTime(20, () -> {
            helper.assertTrue(locomotive.getX() > 5.0 + helper.absoluteVec(Vec3.ZERO).x, "The locomotive does not move");
            helper.assertTrue(wagon.getLootTable() != null, "The locomotive generates the loot of the wagon");
            wagon.setLootTable(null);
            clear(helper, train);
            helper.succeed();
        });
    }

    /** A locomotive with 8 empty wagons goes farther in 60 ticks than a locomotive with 8 full wagons. */
    @GameTest(template = "track", timeoutTicks = 200, batch = "balance_rails")
    public static void emptyTrainIsFasterThanFullTrain(GameTestHelper helper) {
        TrainGameTests.floor(helper, 32, 32);
        for (int x = 1; x < 31; x++) {
            TrainGameTests.rail(helper, x, 2, RailShape.EAST_WEST);
            TrainGameTests.rail(helper, x, 8, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
        List<AbstractMinecart> empty = train(helper, 14.5, 2, 8, false);
        List<AbstractMinecart> full = train(helper, 14.5, 8, 8, true);
        start(helper, empty.get(0));
        start(helper, full.get(0));
        double startX = empty.get(0).getX();
        helper.runAtTickTime(60, () -> {
            double emptyDistance = empty.get(0).getX() - startX;
            double fullDistance = full.get(0).getX() - startX;
            helper.assertTrue(emptyDistance > fullDistance + 1.0,
                    "Empty train: " + emptyDistance + " blocks, full train: " + fullDistance + " blocks");
            helper.assertTrue(fullDistance > 0.5, "The full train does not move");
            clear(helper, empty);
            clear(helper, full);
            helper.succeed();
        });
    }

    /** Builds the flat rails, the slope and the rails at the top. */
    private static void climbTrack(GameTestHelper helper) {
        TrainGameTests.floor(helper, 48, 5);
        for (int x = 1; x < SLOPE_X; x++) {
            TrainGameTests.rail(helper, x, CLIMB_Z, RailShape.EAST_WEST);
        }
        for (int i = 0; i < SLOPE_HEIGHT; i++) {
            helper.setBlock(new BlockPos(SLOPE_X + i, i, CLIMB_Z), Blocks.STONE);
            TrainGameTests.rail(helper, SLOPE_X + i, 1 + i, CLIMB_Z, RailShape.ASCENDING_EAST);
        }
        for (int x = SLOPE_X + SLOPE_HEIGHT; x < 47; x++) {
            helper.setBlock(new BlockPos(x, SLOPE_HEIGHT, CLIMB_Z), Blocks.STONE);
            TrainGameTests.rail(helper, x, SLOPE_HEIGHT + 1, CLIMB_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
    }

    /**
     * A furnace locomotive with fuel for 2000 blocks and linked chest minecarts behind it, to the west.
     *
     * @return the locomotive, then the wagons
     */
    private static List<AbstractMinecart> train(GameTestHelper helper, double x, int z, int wagons, boolean full) {
        return train(helper, ModEntities.FURNACE_LOCOMOTIVE.get(), x, z, wagons, full);
    }

    /**
     * A locomotive with fuel for 2000 blocks and linked chest minecarts behind it, to the west.
     *
     * @return the locomotive, then the wagons
     */
    static List<AbstractMinecart> train(GameTestHelper helper, EntityType<? extends Locomotive> type, double x, int z,
                                        int wagons, boolean full) {
        Locomotive locomotive = helper.spawn(type, new Vec3(x, CART_Y, z + 0.5));
        locomotive.setFuel(2000 * locomotive.stats().fuelPerBlock());
        List<AbstractMinecart> train = new ArrayList<>(List.of(locomotive));
        for (int i = 1; i <= wagons; i++) {
            MinecartChest wagon = helper.spawn(EntityType.CHEST_MINECART, new Vec3(x - i * SPACING, CART_Y, z + 0.5));
            if (full) {
                for (int slot = 0; slot < wagon.getContainerSize(); slot++) {
                    wagon.setItem(slot, new ItemStack(Items.COBBLESTONE, 64));
                }
            }
            TrainGameTests.assertLinked(helper, train.get(i - 1), wagon);
            train.add(wagon);
        }
        return train;
    }

    /** Empties the wagons, so that they drop no items, then removes the minecarts. */
    static void clear(GameTestHelper helper, List<AbstractMinecart> train) {
        for (AbstractMinecart cart : train) {
            if (cart instanceof Container container) {
                container.clearContent();
            }
        }
        TrainGameTests.clear(helper, train);
    }

    /** A player starts the locomotive with a right click and an empty hand. */
    static void start(GameTestHelper helper, AbstractMinecart locomotive) {
        Vec3 at = helper.relativeVec(locomotive.position()).add(-1.0, 0.0, 1.0);
        ServerPlayer player = TrainGameTests.survivalPlayer(helper, at);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(locomotive, InteractionHand.MAIN_HAND);
        helper.assertTrue(((Locomotive) locomotive).isRunning(), "The right click does not start the locomotive");
        helper.getLevel().getServer().getPlayerList().remove(player);
    }
}
