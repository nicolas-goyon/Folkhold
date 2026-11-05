package io.github.nicolasgoyon.folkhold.balance.gametest;

import com.mojang.authlib.GameProfile;
import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLink;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks.LinkResult;
import io.github.nicolasgoyon.folkhold.balance.registry.ModAttachments;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.gametest.framework.GameTestRunner;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.players.PlayerList;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the chain links between minecarts.
 *
 * <p>Templates (made with {@code tools/make_test_structure.py}):
 * <ul>
 *   <li>{@code folkhold_balance:track}: 32 × 5 × 32 blocks of air, for a track with a straight part, a curve and
 *       a slope.</li>
 *   <li>{@code folkhold_balance:bench}: 24 × 4 × 8 blocks of air, for one straight track.</li>
 * </ul>
 * Each test builds a floor of stone at y = 0 and its rails at y = 1 or above.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainGameTests {
    /** The height of a minecart on a rail at y = 1. */
    static final double CART_Y = 1.0625;
    /** The row of the rails on the bench. */
    static final int BENCH_Z = 3;
    /** The row of the straight part of the track. */
    private static final int TRACK_Z = 2;
    /** The column of the curve and of the second straight part of the track. */
    private static final int TRACK_TURN_X = 25;
    /** The row of the slope of the track, after the curve. The rails after the slope are at y = 2. */
    private static final int TRACK_SLOPE_Z = 10;
    /** The largest X and Z of the loop. The smallest X and Z are 1. */
    private static final int LOOP_HIGH = 30;
    /** The rows of the slope up and of the slope down on the east side of the loop. */
    private static final int LOOP_SLOPE_UP_Z = 10;
    private static final int LOOP_SLOPE_DOWN_Z = 20;
    /**
     * The real-time limit of a wait for a state of a train, in seconds. The GameTest server does not wait between two
     * ticks, so a number of ticks is not a real time.
     */
    private static final long STATE_SECONDS = 30;
    /** The tick limit of a test that waits with {@link #STATE_SECONDS}: a safety limit only. */
    private static final int STATE_TEST_TICKS = 2_000_000;
    /** The distance that a furnace minecart moves its linked minecart in the tests, in blocks. */
    private static final double MOVE_DISTANCE = 6.0;
    /** The smallest speed of a minecart that a furnace minecart moves, in blocks per tick. The vanilla cap is 0.2. */
    private static final double MOVE_SPEED = 0.15;
    /** The shape of each rail that a test places, by absolute position. The tests run on the server thread. */
    private static final Map<BlockPos, RailShape> RAILS = new HashMap<>();

    private TrainGameTests() {
    }

    /**
     * A furnace minecart pulls 3 linked chest minecarts along a straight rail, through a curve, then up a slope. No
     * link breaks, and the chains stay near their maximum distance or below it.
     */
    @GameTest(template = "track", timeoutTicks = 500, batch = "balance_rails")
    public static void furnacePullsThreeWagonsThroughACurve(GameTestHelper helper) {
        floor(helper, 32, 32);
        for (int x = 1; x < TRACK_TURN_X; x++) {
            rail(helper, x, TRACK_Z, RailShape.EAST_WEST);
        }
        rail(helper, TRACK_TURN_X, TRACK_Z, RailShape.SOUTH_WEST);
        for (int z = TRACK_Z + 1; z < TRACK_SLOPE_Z; z++) {
            rail(helper, TRACK_TURN_X, z, RailShape.NORTH_SOUTH);
        }
        rail(helper, TRACK_TURN_X, 1, TRACK_SLOPE_Z, RailShape.ASCENDING_SOUTH);
        for (int z = TRACK_SLOPE_Z + 1; z < 31; z++) {
            helper.setBlock(new BlockPos(TRACK_TURN_X, 1, z), Blocks.STONE);
            rail(helper, TRACK_TURN_X, 2, z, RailShape.NORTH_SOUTH);
        }
        fixRails(helper);
        double spacing = BalanceConfig.coupling().restDistance();
        MinecartFurnace furnace = helper.spawn(EntityType.FURNACE_MINECART, new Vec3(9.5, CART_Y, TRACK_Z + 0.5));
        List<AbstractMinecart> train = List.of(furnace,
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(9.5 - spacing, CART_Y, TRACK_Z + 0.5)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(9.5 - 2 * spacing, CART_Y, TRACK_Z + 0.5)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(9.5 - 3 * spacing, CART_Y, TRACK_Z + 0.5)));
        for (int i = 0; i + 1 < train.size(); i++) {
            assertLinked(helper, train.get(i), train.get(i + 1));
        }
        fuel(helper, furnace, new Vec3(8.5, CART_Y, TRACK_Z + 0.5));
        double maxDistance = BalanceConfig.coupling().maxDistance();
        AtomicReference<Double> largest = new AtomicReference<>(0.0);
        helper.onEachTick(() -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                largest.set(Math.max(largest.get(), train.get(i).distanceTo(train.get(i + 1))));
            }
        });
        AbstractMinecart last = train.get(train.size() - 1);
        double finish = helper.absoluteVec(new Vec3(TRACK_TURN_X, 0, TRACK_SLOPE_Z + 3)).z;
        helper.succeedWhen(() -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                helper.assertTrue(CartLinks.get(train.get(i)).back().equals(Optional.of(train.get(i + 1).getUUID())),
                        "The link " + i + " of the train is broken");
            }
            for (AbstractMinecart cart : train) {
                helper.assertTrue(cart.isOnRails(), "A minecart of the train leaves the rails");
            }
            helper.assertTrue(last.getZ() > finish, "The last wagon is at z " + last.getZ() + ", not after the slope");
            helper.assertTrue(largest.get() <= maxDistance + 0.5,
                    "The largest distance between two minecarts is " + largest.get());
            clear(helper, train);
        });
    }

    /**
     * A furnace minecart pulls 3 chest minecarts for 100 blocks around a loop with 4 curves, a slope up and a slope
     * down. No link breaks, and no minecart leaves the rails.
     */
    @GameTest(template = "track", timeoutTicks = 1200, batch = "balance_rails")
    public static void furnacePullsThreeWagonsForAHundredBlocks(GameTestHelper helper) {
        floor(helper, 32, 32);
        int low = 1;
        int high = LOOP_HIGH;
        rail(helper, low, low, RailShape.SOUTH_EAST);
        rail(helper, high, low, RailShape.SOUTH_WEST);
        rail(helper, high, high, RailShape.NORTH_WEST);
        rail(helper, low, high, RailShape.NORTH_EAST);
        for (int i = low + 1; i < high; i++) {
            rail(helper, i, low, RailShape.EAST_WEST);
            rail(helper, i, high, RailShape.EAST_WEST);
            rail(helper, low, i, RailShape.NORTH_SOUTH);
            if (i < LOOP_SLOPE_UP_Z || i > LOOP_SLOPE_DOWN_Z) {
                rail(helper, high, i, RailShape.NORTH_SOUTH);
            }
        }
        // The east side goes up one block, then down again.
        rail(helper, high, LOOP_SLOPE_UP_Z, RailShape.ASCENDING_SOUTH);
        for (int z = LOOP_SLOPE_UP_Z + 1; z < LOOP_SLOPE_DOWN_Z; z++) {
            helper.setBlock(new BlockPos(high, 1, z), Blocks.STONE);
            rail(helper, high, 2, z, RailShape.NORTH_SOUTH);
        }
        rail(helper, high, LOOP_SLOPE_DOWN_Z, RailShape.ASCENDING_NORTH);
        fixRails(helper);
        double spacing = BalanceConfig.coupling().restDistance();
        double z = low + 0.5;
        MinecartFurnace furnace = helper.spawn(EntityType.FURNACE_MINECART, new Vec3(9.5, CART_Y, z));
        List<AbstractMinecart> train = List.of(furnace,
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(9.5 - spacing, CART_Y, z)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(9.5 - 2 * spacing, CART_Y, z)),
                helper.spawn(EntityType.CHEST_MINECART, new Vec3(9.5 - 3 * spacing, CART_Y, z)));
        for (int i = 0; i + 1 < train.size(); i++) {
            assertLinked(helper, train.get(i), train.get(i + 1));
        }
        fuel(helper, furnace, new Vec3(8.5, CART_Y, z));
        AtomicReference<Vec3> last = new AtomicReference<>(furnace.position());
        AtomicReference<Double> travel = new AtomicReference<>(0.0);
        AtomicReference<Double> largest = new AtomicReference<>(0.0);
        AtomicReference<Boolean> derailed = new AtomicReference<>(false);
        helper.onEachTick(() -> {
            travel.set(travel.get() + furnace.position().subtract(last.get()).horizontalDistance());
            last.set(furnace.position());
            for (int i = 0; i + 1 < train.size(); i++) {
                largest.set(Math.max(largest.get(), train.get(i).distanceTo(train.get(i + 1))));
            }
            if (train.stream().anyMatch(cart -> !cart.isOnRails() && cart.tickCount > 2)) {
                derailed.set(true);
            }
        });
        double maxDistance = BalanceConfig.coupling().maxDistance();
        helper.succeedWhen(() -> {
            for (int i = 0; i + 1 < train.size(); i++) {
                helper.assertTrue(CartLinks.get(train.get(i)).back().equals(Optional.of(train.get(i + 1).getUUID())),
                        "The link " + i + " of the train is broken");
            }
            helper.assertTrue(!derailed.get(), "A minecart of the train leaves the rails");
            helper.assertTrue(largest.get() <= maxDistance + 0.5,
                    "The largest distance between two minecarts is " + largest.get());
            helper.assertTrue(travel.get() >= 100.0, "The train travels " + travel.get() + " blocks");
            clear(helper, train);
        });
    }

    /** The furnace minecart pulls a wagon that a block stops. The link breaks and drops a chain. */
    @GameTest(template = "bench", timeoutTicks = 300, batch = "balance_rails")
    public static void linkBreaksWhenAWagonIsBlocked(GameTestHelper helper) {
        floor(helper, 24, 8);
        rail(helper, 1, BENCH_Z, RailShape.EAST_WEST);
        helper.setBlock(new BlockPos(2, 1, BENCH_Z), Blocks.STONE);
        for (int x = 3; x < 23; x++) {
            rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        fixRails(helper);
        MinecartChest wagon = helper.spawn(EntityType.CHEST_MINECART, new Vec3(1.5, CART_Y, BENCH_Z + 0.5));
        MinecartFurnace furnace = helper.spawn(EntityType.FURNACE_MINECART, new Vec3(3.5, CART_Y, BENCH_Z + 0.5));
        assertLinked(helper, furnace, wagon);
        fuel(helper, furnace, new Vec3(2.5, CART_Y, BENCH_Z + 0.5));
        double wagonX = wagon.getX();
        helper.succeedWhen(() -> {
            helper.assertTrue(CartLinks.get(wagon).isEmpty() && CartLinks.get(furnace).isEmpty(),
                    "The link is not broken");
            helper.assertTrue(chains(helper) == 1, "Expected 1 chain on the ground, found " + chains(helper));
            helper.assertTrue(wagon.getX() < wagonX + 0.5, "The block does not stop the wagon");
            clear(helper, List.of(wagon, furnace));
        });
    }

    /**
     * The player links a minecart, then a furnace minecart: the furnace minecart is behind the minecart in the train.
     * The player gives coal to the furnace minecart from behind the minecart: the furnace minecart goes away from the
     * minecart and pulls it.
     */
    @GameTest(template = "bench", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void furnaceBehindPullsItsLinkedMinecart(GameTestHelper helper) {
        furnaceBehindMovesItsLinkedMinecart(helper, 5.5, 4.5, 1.0);
    }

    /**
     * The player links a minecart, then a furnace minecart: the furnace minecart is behind the minecart in the train.
     * The player gives coal to the furnace minecart from behind the furnace minecart: the furnace minecart goes to the
     * minecart and pushes it.
     */
    @GameTest(template = "bench", timeoutTicks = STATE_TEST_TICKS, batch = "balance_rails")
    public static void furnaceBehindPushesItsLinkedMinecart(GameTestHelper helper) {
        furnaceBehindMovesItsLinkedMinecart(helper, 15.5, 18.0, -1.0);
    }

    /**
     * A minecart and a furnace minecart on the bench, linked with a chain by a player in this order. The furnace
     * minecart is to the east of the minecart, at the rest distance. The player gives coal to the furnace minecart,
     * and the furnace minecart moves the minecart along the rails, with a real-time limit of {@link #STATE_SECONDS}.
     *
     * @param cartX     the X of the minecart, relative to the test
     * @param playerX   the X of the player that gives the coal, relative to the test
     * @param direction 1 when the train goes to the east, -1 when it goes to the west
     */
    private static void furnaceBehindMovesItsLinkedMinecart(GameTestHelper helper, double cartX, double playerX,
                                                            double direction) {
        floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        fixRails(helper);
        double spacing = BalanceConfig.coupling().restDistance();
        AbstractMinecart cart = helper.spawn(EntityType.MINECART, new Vec3(cartX, CART_Y, BENCH_Z + 0.5));
        MinecartFurnace furnace = helper.spawn(EntityType.FURNACE_MINECART,
                new Vec3(cartX + spacing, CART_Y, BENCH_Z + 0.5));
        ServerPlayer player = survivalPlayer(helper, new Vec3(cartX + spacing / 2.0, 1.0, BENCH_Z + 1.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHAIN));
        player.interactOn(cart, InteractionHand.MAIN_HAND);
        player.interactOn(furnace, InteractionHand.MAIN_HAND);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(CartLinks.get(cart).back().equals(Optional.of(furnace.getUUID())),
                "The furnace minecart is not behind the minecart");
        fuel(helper, furnace, new Vec3(playerX, CART_Y, BENCH_Z + 0.5));
        double startX = cart.getX();
        Runnable moves = () -> {
            double travel = (cart.getX() - startX) * direction;
            double speed = (cart.getX() - cart.xo) * direction;
            helper.assertTrue(CartLinks.get(cart).back().equals(Optional.of(furnace.getUUID())), "The link breaks");
            helper.assertTrue(travel >= MOVE_DISTANCE && speed >= MOVE_SPEED,
                    "The minecart moves " + travel + " blocks, at " + speed + " blocks per tick");
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
                .thenExecute(() -> clear(helper, List.of(cart, furnace)))
                .thenSucceed();
    }

    /**
     * Shears remove a link and drop the chain. An empty hand while the player sneaks also removes a link. An empty
     * hand without sneak lets the player ride and keeps the link.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void shearsAndEmptyHandRemoveALink(GameTestHelper helper) {
        floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        fixRails(helper);
        AbstractMinecart front = helper.spawn(EntityType.MINECART, new Vec3(5.5, CART_Y, BENCH_Z + 0.5));
        AbstractMinecart back = helper.spawn(EntityType.MINECART, new Vec3(4.0, CART_Y, BENCH_Z + 0.5));
        ServerPlayer player = survivalPlayer(helper, new Vec3(4.5, 1.0, BENCH_Z + 1.5));

        assertLinked(helper, front, back);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHEARS));
        player.interactOn(back, InteractionHand.MAIN_HAND);
        helper.assertTrue(CartLinks.get(front).isEmpty() && CartLinks.get(back).isEmpty(),
                "The shears do not remove the link");
        helper.assertTrue(player.getVehicle() == null, "The player rides the minecart with the shears");
        helper.assertTrue(chains(helper) == 1, "The shears do not drop the chain");

        assertLinked(helper, front, back);
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(back, InteractionHand.MAIN_HAND);
        helper.assertTrue(CartLinks.get(back).front().isPresent(), "An empty hand without sneak removes the link");
        helper.assertTrue(player.getVehicle() == back, "An empty hand without sneak does not let the player ride");
        player.stopRiding();

        player.setShiftKeyDown(true);
        player.interactOn(back, InteractionHand.MAIN_HAND);
        helper.assertTrue(CartLinks.get(front).isEmpty() && CartLinks.get(back).isEmpty(),
                "An empty hand with sneak does not remove the link");
        helper.assertTrue(chains(helper) == 2, "An empty hand with sneak does not drop the chain");

        helper.getLevel().getServer().getPlayerList().remove(player);
        clear(helper, List.of(front, back));
        helper.succeed();
    }

    /**
     * The player uses a chain on a first minecart, then on a second minecart. The first minecart goes in front. The
     * link uses 1 chain, and the player does not ride.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void chainLinksTwoMinecarts(GameTestHelper helper) {
        floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        fixRails(helper);
        AbstractMinecart first = helper.spawn(EntityType.MINECART, new Vec3(6.0, CART_Y, BENCH_Z + 0.5));
        AbstractMinecart second = helper.spawn(EntityType.CHEST_MINECART, new Vec3(4.0, CART_Y, BENCH_Z + 0.5));
        ServerPlayer player = survivalPlayer(helper, new Vec3(5.0, 1.0, BENCH_Z + 1.5));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.CHAIN, 3));

        player.interactOn(first, InteractionHand.MAIN_HAND);
        helper.assertTrue(CartLinks.get(first).isEmpty(), "One minecart makes a link");
        helper.assertTrue(player.getVehicle() == null, "The chain lets the player ride");
        player.interactOn(second, InteractionHand.MAIN_HAND);
        helper.assertTrue(CartLinks.get(first).back().equals(Optional.of(second.getUUID())),
                "The first minecart is not in front of the second minecart");
        helper.assertTrue(CartLinks.get(second).front().equals(Optional.of(first.getUUID())),
                "The second minecart is not behind the first minecart");
        helper.assertTrue(player.getMainHandItem().getCount() == 2, "The link does not use 1 chain");
        helper.assertTrue(player.containerMenu == player.inventoryMenu, "The chain opens the chest of the minecart");

        helper.getLevel().getServer().getPlayerList().remove(player);
        clear(helper, List.of(first, second));
        helper.succeed();
    }

    /**
     * The links follow the rules: a minecart has one front link and one back link, a train has no loop and a
     * maximum number of minecarts, and the two minecarts must be near.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void linkRules(GameTestHelper helper) {
        floor(helper, 24, 8);
        ServerLevel level = helper.getLevel();
        AbstractMinecart a = helper.spawn(EntityType.MINECART, new Vec3(3.0, 1.0, 2.5));
        AbstractMinecart b = helper.spawn(EntityType.MINECART, new Vec3(4.0, 1.0, 2.5));
        AbstractMinecart c = helper.spawn(EntityType.MINECART, new Vec3(5.0, 1.0, 2.5));
        AbstractMinecart d = helper.spawn(EntityType.MINECART, new Vec3(4.0, 1.0, 4.0));
        AbstractMinecart far = helper.spawn(EntityType.MINECART, new Vec3(12.5, 1.0, 2.5));
        double max = BalanceConfig.coupling().maxDistance();

        helper.assertTrue(CartLinks.link(level, a, a, max, 16) == LinkResult.SAME_CART, "A minecart links to itself");
        helper.assertTrue(CartLinks.link(level, a, far, max, 16) == LinkResult.TOO_FAR, "Far minecarts link");
        helper.assertTrue(CartLinks.link(level, a, b, max, 16) == LinkResult.LINKED, "a-b");
        helper.assertTrue(CartLinks.link(level, b, c, max, 16) == LinkResult.LINKED, "b-c");
        helper.assertTrue(CartLinks.link(level, d, b, max, 16) == LinkResult.NO_FREE_END,
                "A minecart gets a third link");
        helper.assertTrue(CartLinks.link(level, c, a, max, 16) == LinkResult.LOOP, "A train makes a loop");
        helper.assertTrue(CartLinks.link(level, c, d, max, 3) == LinkResult.TOO_LONG,
                "A train gets more minecarts than the maximum");
        helper.assertTrue(CartLinks.link(level, c, d, max, 4) == LinkResult.LINKED, "c-d");

        clear(helper, List.of(a, b, c, d, far));
        helper.succeed();
    }

    /**
     * The links stay in the saved data of a minecart. A destroyed minecart breaks its links and drops the chains.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void linksAreSavedAndBreakWhenAMinecartIsDestroyed(GameTestHelper helper) {
        floor(helper, 24, 8);
        ServerLevel level = helper.getLevel();
        AbstractMinecart front = helper.spawn(EntityType.MINECART, new Vec3(2.5, 1.0, 2.5));
        AbstractMinecart middle = helper.spawn(EntityType.CHEST_MINECART, new Vec3(4.0, 1.0, 2.5));
        AbstractMinecart back = helper.spawn(EntityType.MINECART, new Vec3(5.5, 1.0, 2.5));
        assertLinked(helper, front, middle);
        assertLinked(helper, middle, back);

        CompoundTag tag = middle.saveWithoutId(new CompoundTag());
        MinecartChest copy = new MinecartChest(EntityType.CHEST_MINECART, level);
        copy.load(tag);
        CartLink saved = copy.getData(ModAttachments.CART_LINK);
        helper.assertTrue(saved.equals(CartLinks.get(middle)), "The saved links are " + saved);

        middle.kill();
        helper.assertTrue(CartLinks.get(front).isEmpty() && CartLinks.get(back).isEmpty(),
                "The destroyed minecart keeps its links");
        helper.assertTrue(chains(helper) == 2, "Expected 2 chains on the ground, found " + chains(helper));

        clear(helper, List.of(front, back));
        helper.succeed();
    }

    static void floor(GameTestHelper helper, int sizeX, int sizeZ) {
        for (int x = 0; x < sizeX; x++) {
            for (int z = 0; z < sizeZ; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
    }

    /** Places a rail at y = 1. {@link #fixRails(GameTestHelper)} gives it its shape again. */
    static void rail(GameTestHelper helper, int x, int z, RailShape shape) {
        rail(helper, x, 1, z, shape);
    }

    /** Places a rail. {@link #fixRails(GameTestHelper)} gives it its shape again. */
    static void rail(GameTestHelper helper, int x, int y, int z, RailShape shape) {
        BlockPos pos = new BlockPos(x, y, z);
        helper.setBlock(pos, Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape));
        RAILS.put(helper.absolutePos(pos), shape);
    }

    /**
     * Sets the shape of each rail of the test again. A rail changes its shape when the test places a rail next to it.
     * A rail that gets the same block again keeps the given shape.
     */
    static void fixRails(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        RAILS.forEach((pos, shape) -> {
            BlockState state = level.getBlockState(pos);
            if (state.is(Blocks.RAIL)) {
                level.setBlock(pos, state.setValue(RailBlock.SHAPE, shape), Block.UPDATE_CLIENTS);
            }
        });
        RAILS.clear();
    }

    /** Links two minecarts with the default distance and fails the test when the link fails. */
    static void assertLinked(GameTestHelper helper, AbstractMinecart front, AbstractMinecart back) {
        LinkResult result = CartLinks.link(helper.getLevel(), front, back, BalanceConfig.coupling().maxDistance(),
                BalanceConfig.TRAIN_MAX_CARTS.get());
        helper.assertTrue(result == LinkResult.LINKED, "The link fails: " + result);
    }

    /**
     * Gives coal to a furnace minecart. The furnace minecart moves away from the player.
     *
     * @param player the position of the player, relative to the test
     */
    static void fuel(GameTestHelper helper, MinecartFurnace furnace, Vec3 player) {
        ServerPlayer mock = survivalPlayer(helper, player);
        mock.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 4));
        mock.interactOn(furnace, InteractionHand.MAIN_HAND);
        helper.getLevel().getServer().getPlayerList().remove(mock);
    }

    /**
     * A mock player in survival mode at a position relative to the test. A test can remove it with
     * {@code getPlayerList().remove(player)}. At the end of the test, also after a failure, a listener removes the
     * player and its operator entry.
     */
    static ServerPlayer survivalPlayer(GameTestHelper helper, Vec3 position) {
        ServerLevel level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        // The default game mode of the GameTest server is creative.
        player.setGameMode(GameType.SURVIVAL);
        Vec3 absolute = helper.absoluteVec(position);
        player.moveTo(absolute.x, absolute.y, absolute.z);
        logOutAtEnd(helper, player);
        return player;
    }

    /**
     * Removes a mock player and its operator entry when the test passes or fails. A failed test does not get to its
     * own logout: without this listener, the player stays online for the next tests. The listener removes only this
     * player: a player of another test can have the same id.
     */
    private static void logOutAtEnd(GameTestHelper helper, ServerPlayer player) {
        helper.testInfo.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo info) {
            }

            @Override
            public void testPassed(GameTestInfo info, GameTestRunner runner) {
                logOut(player);
            }

            @Override
            public void testFailed(GameTestInfo info, GameTestRunner runner) {
                logOut(player);
            }

            @Override
            public void testAddedForRerun(GameTestInfo info, GameTestInfo rerun, GameTestRunner runner) {
            }
        });
    }

    private static void logOut(ServerPlayer player) {
        PlayerList players = player.server.getPlayerList();
        ServerPlayer online = players.getPlayer(player.getUUID());
        if (online != null && online != player) {
            return;
        }
        if (players.getOps().get(player.getGameProfile()) != null) {
            players.getOps().remove(player.getGameProfile());
        }
        if (online == player) {
            players.remove(player);
        }
    }

    /** The number of chains on the ground in the test area. */
    static int chains(GameTestHelper helper) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0),
                item -> item.getItem().is(Items.CHAIN)).stream().mapToInt(item -> item.getItem().getCount()).sum();
    }

    /** Removes the minecarts without chains, and the chains on the ground. */
    static void clear(GameTestHelper helper, List<AbstractMinecart> carts) {
        for (AbstractMinecart cart : carts) {
            cart.removeData(ModAttachments.CART_LINK);
            cart.discard();
        }
        helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0),
                item -> item.getItem().is(Items.CHAIN)).forEach(ItemEntity::discard);
    }
}
