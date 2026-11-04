package io.github.nicolasgoyon.folkhold.balance.gametest;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RailBlock;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for a train at the edge of the loaded area.
 *
 * <p>Each test uses 2 chunks far from the test, side by side along X: the chunk {@code west} and the chunk
 * {@code east}. The front minecart is near the east edge of {@code west}, and the back minecart is near the west edge
 * of {@code east}. Region tickets give the state of each chunk:
 * <ul>
 *   <li>a ticket of distance 2 on {@code west}: {@code west} ticks its entities, and {@code east} is at the edge of
 *       the simulation distance (the level gives its entities, but does not tick them);</li>
 *   <li>a ticket of distance 3 on a chunk: the chunk and the chunks next to it tick their entities;</li>
 *   <li>a ticket of distance 0 on a chunk: the chunk is loaded and does not tick. The removal of the ticket unloads
 *       it.</li>
 * </ul>
 *
 * <p>A ticket loads the chunks around its chunk. Each test has its own batch: the tests do not run at the same time,
 * and the tickets of a test do not load the chunks of another test. A test does not use the chunks of an earlier
 * test: the rails of the earlier test stay in the chunks after they unload.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainLoadingGameTests {
    private static final TicketType<ChunkPos> FAR_CHUNK =
            TicketType.create("folkhold_balance_far_chunk", Comparator.comparingLong(ChunkPos::toLong));
    /** The distance in blocks along X from the test to the chunk {@code west}. */
    private static final int FAR_OFFSET = 2048;
    /** The radius in chunks with no loaded chunk around the 2 chunks of a test, before the test. */
    private static final int FREE_RADIUS = 4;
    /** The X of the front minecart and of the back minecart, relative to the west side of {@code west}. */
    private static final double FRONT_X = 15.2;
    private static final double BACK_X = 16.7;
    /** The number of ticks of a test: more than the break ticks and more than 2 checks of the dead links. */
    private static final int WAIT_TICKS = 80;
    /** The chunks that the earlier tests used, as {@link ChunkPos#toLong()}. Only for the server thread. */
    private static final Set<Long> USED = new HashSet<>();

    private TrainLoadingGameTests() {
    }

    /**
     * The back minecart is at the edge of the simulation distance. The fueled furnace minecart in front does not
     * move, and the link does not break.
     */
    @GameTest(template = "bench", timeoutTicks = 2400, batch = "balance_rails_edge")
    public static void trainStopsAtTheEdgeOfTheSimulationDistance(GameTestHelper helper) {
        Far far = new Far(helper);
        far.ticket(far.west, 2);
        for (int x = 1; x < 24; x++) {
            far.rail(x, 0, RailShape.EAST_WEST);
        }
        far.fixRails();
        List<AbstractMinecart> carts = new ArrayList<>();
        List<Vec3> start = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(far::assertEdgeReady)
                .thenExecute(() -> {
                    MinecartFurnace furnace = far.spawn(EntityType.FURNACE_MINECART, FRONT_X, 0);
                    carts.add(furnace);
                    carts.add(far.spawn(EntityType.CHEST_MINECART, BACK_X, 0));
                    TrainGameTests.assertLinked(helper, carts.get(0), carts.get(1));
                    // The furnace minecart moves away from the player: toward the west.
                    ServerPlayer player = TrainGameTests.survivalPlayer(helper, Vec3.ZERO);
                    player.moveTo(furnace.getX() + 1.0, furnace.getY(), furnace.getZ());
                    player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.COAL, 4));
                    player.interactOn(furnace, InteractionHand.MAIN_HAND);
                    helper.getLevel().getServer().getPlayerList().remove(player);
                    start.add(furnace.position());
                })
                .thenIdle(WAIT_TICKS)
                .thenExecute(() -> {
                    far.assertEdge(carts.get(1));
                    far.assertLinked(carts.get(0), carts.get(1));
                    double moved = carts.get(0).position().distanceTo(start.get(0));
                    helper.assertTrue(moved < 0.1, "The train does not stop: the furnace minecart moves " + moved
                            + " blocks");
                    helper.assertTrue(far.chains() == 0, "A chain drops");
                    far.clear(carts);
                })
                .thenSucceed();
    }

    /**
     * The back minecart is at the edge of the simulation distance. The running locomotive in front does not move and
     * burns no fuel.
     */
    @GameTest(template = "bench", timeoutTicks = 2400, batch = "balance_rails_locomotive_edge")
    public static void waitingLocomotiveBurnsNoFuel(GameTestHelper helper) {
        Far far = new Far(helper);
        far.ticket(far.west, 2);
        for (int x = 1; x < 24; x++) {
            far.rail(x, 0, RailShape.EAST_WEST);
        }
        far.fixRails();
        List<AbstractMinecart> carts = new ArrayList<>();
        List<Vec3> start = new ArrayList<>();
        double fuel = 1000.0;
        helper.startSequence()
                .thenWaitUntil(far::assertEdgeReady)
                .thenExecute(() -> {
                    Locomotive locomotive = far.spawn(ModEntities.FURNACE_LOCOMOTIVE.get(), FRONT_X, 0);
                    locomotive.setFuel(fuel);
                    carts.add(locomotive);
                    carts.add(far.spawn(EntityType.CHEST_MINECART, BACK_X, 0));
                    TrainGameTests.assertLinked(helper, carts.get(0), carts.get(1));
                    // The locomotive goes away from the minecart behind it: toward the west.
                    locomotive.setRunning(true, locomotive.position());
                    start.add(locomotive.position());
                })
                .thenIdle(WAIT_TICKS)
                .thenExecute(() -> {
                    far.assertEdge(carts.get(1));
                    far.assertLinked(carts.get(0), carts.get(1));
                    double moved = carts.get(0).position().distanceTo(start.get(0));
                    helper.assertTrue(moved < 0.1, "The locomotive moves " + moved + " blocks");
                    double left = ((Locomotive) carts.get(0)).fuel();
                    helper.assertTrue(left == fuel, "The waiting locomotive burns " + (fuel - left) + " fuel");
                    far.clear(carts);
                })
                .thenSucceed();
    }

    /**
     * The front minecart is on a slope, and the back minecart is at the edge of the simulation distance. The front
     * minecart does not roll down the slope, and the link does not break.
     */
    @GameTest(template = "bench", timeoutTicks = 2400, batch = "balance_rails_slope")
    public static void trainOnASlopeStopsAtTheEdgeOfTheSimulationDistance(GameTestHelper helper) {
        Far far = new Far(helper);
        far.ticket(far.west, 2);
        for (int x = 8; x < 15; x++) {
            far.rail(x, 0, RailShape.EAST_WEST);
        }
        // The rail of the front minecart goes up toward the east. The rails after it are one block higher.
        far.rail(15, 0, RailShape.ASCENDING_EAST);
        for (int x = 16; x < 20; x++) {
            far.level.setBlock(far.pos(x, 0), Blocks.STONE.defaultBlockState(), Block.UPDATE_CLIENTS);
            far.rail(x, 1, RailShape.EAST_WEST);
        }
        far.fixRails();
        List<AbstractMinecart> carts = new ArrayList<>();
        List<Vec3> start = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(far::assertEdgeReady)
                .thenExecute(() -> {
                    carts.add(far.spawn(EntityType.MINECART, 15.5, 0.5));
                    carts.add(far.spawn(EntityType.MINECART, BACK_X, 1));
                    TrainGameTests.assertLinked(helper, carts.get(0), carts.get(1));
                    start.add(carts.get(0).position());
                })
                .thenIdle(WAIT_TICKS)
                .thenExecute(() -> {
                    far.assertEdge(carts.get(1));
                    far.assertLinked(carts.get(0), carts.get(1));
                    double moved = carts.get(0).position().distanceTo(start.get(0));
                    helper.assertTrue(moved < 0.1, "The front minecart rolls " + moved + " blocks");
                    helper.assertTrue(far.chains() == 0, "A chain drops");
                    far.clear(carts);
                })
                .thenSucceed();
    }

    /**
     * The chunk of the back minecart unloads, then loads again. The two minecarts keep the link, and the minecart that
     * loads again has its link from the saved data of the chunk.
     */
    @GameTest(template = "bench", timeoutTicks = 2400, batch = "balance_rails_unload")
    public static void linkStaysWhenAMinecartUnloadsAndLoadsAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Far far = new Far(helper);
        far.ticket(far.west, 0);
        far.ticket(far.east, 0);
        List<AbstractMinecart> carts = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(far::assertLoaded)
                .thenExecute(() -> {
                    carts.add(far.spawn(EntityType.MINECART, FRONT_X, 0));
                    carts.add(far.spawn(EntityType.MINECART, BACK_X, 0));
                    TrainGameTests.assertLinked(helper, carts.get(0), carts.get(1));
                    far.removeTicket(far.east, 0);
                })
                .thenWaitUntil(() -> assertUnloaded(helper, carts.get(1)))
                .thenExecute(() -> {
                    helper.assertTrue(CartLinks.get(carts.get(0)).back().equals(Optional.of(carts.get(1).getUUID())),
                            "The front minecart forgets the link when the back minecart unloads");
                    far.ticket(far.east, 0);
                })
                .thenWaitUntil(() -> assertLoadedAgain(helper, carts.get(1)))
                .thenIdle(WAIT_TICKS)
                .thenExecute(() -> {
                    AbstractMinecart loaded = loaded(level, carts.get(1));
                    helper.assertTrue(loaded != carts.get(1), "The back minecart does not come from the saved data");
                    far.assertLinked(carts.get(0), loaded);
                    helper.assertTrue(far.chains() == 0, "A chain drops");
                    far.clear(List.of(carts.get(0), loaded));
                })
                .thenSucceed();
    }

    /**
     * The chunk of the back minecart unloads, then the front minecart is destroyed: one chain drops. The back minecart
     * loads again in an area that ticks. The check of the dead links removes its link, and no other chain drops.
     */
    @GameTest(template = "bench", timeoutTicks = 2400, batch = "balance_rails_dead_link")
    public static void deadLinkGoesAwayWhenTheMinecartLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        Far far = new Far(helper);
        far.ticket(far.west, 0);
        far.ticket(far.east, 0);
        List<AbstractMinecart> carts = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(far::assertLoaded)
                .thenExecute(() -> {
                    carts.add(far.spawn(EntityType.MINECART, FRONT_X, 0));
                    carts.add(far.spawn(EntityType.MINECART, BACK_X, 0));
                    TrainGameTests.assertLinked(helper, carts.get(0), carts.get(1));
                    far.removeTicket(far.east, 0);
                })
                .thenWaitUntil(() -> assertUnloaded(helper, carts.get(1)))
                .thenExecute(() -> {
                    carts.get(0).kill();
                    helper.assertTrue(far.chains() == 1, "The destroyed minecart drops " + far.chains() + " chains");
                    // The chunks next to east also tick their entities.
                    far.ticket(far.east, 3);
                })
                .thenWaitUntil(() -> assertLoadedAgain(helper, carts.get(1)))
                .thenExecute(() -> helper.assertTrue(CartLinks.get(loaded(level, carts.get(1))).front()
                        .equals(Optional.of(carts.get(0).getUUID())), "The back minecart loads without its link"))
                .thenWaitUntil(() -> helper.assertTrue(CartLinks.get(loaded(level, carts.get(1))).isEmpty(),
                        "The back minecart keeps the link to the destroyed minecart"))
                .thenExecute(() -> {
                    helper.assertTrue(far.chains() == 1, "Expected 1 chain in total, found " + far.chains());
                    far.clear(List.of(loaded(level, carts.get(1))));
                })
                .thenSucceed();
    }

    /** Fails until a minecart is unloaded: removed, and not in the level. */
    private static void assertUnloaded(GameTestHelper helper, AbstractMinecart cart) {
        helper.assertTrue(cart.isRemoved() && helper.getLevel().getEntity(cart.getUUID()) == null,
                "The minecart does not unload");
    }

    /** Fails until the level gives a minecart with the UUID of a minecart that unloaded. */
    private static void assertLoadedAgain(GameTestHelper helper, AbstractMinecart unloaded) {
        helper.assertTrue(helper.getLevel().getEntity(unloaded.getUUID()) instanceof AbstractMinecart,
                "The minecart does not load again");
    }

    /** The minecart in the level with the UUID of a minecart that unloaded. */
    private static AbstractMinecart loaded(ServerLevel level, AbstractMinecart unloaded) {
        return (AbstractMinecart) level.getEntity(unloaded.getUUID());
    }

    /** The 2 far chunks of a test, and the tickets of the test. */
    private static final class Far {
        final GameTestHelper helper;
        final ServerLevel level;
        final ChunkPos west;
        final ChunkPos east;
        /** The Y of the ground surface and the Z of the rails. */
        final int y;
        final int z;
        private final List<int[]> tickets = new ArrayList<>();
        private final Map<BlockPos, RailShape> shapes = new HashMap<>();

        Far(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            this.west = freeChunk(level, new ChunkPos(helper.absolutePos(BlockPos.ZERO).offset(FAR_OFFSET, 0, 0)));
            this.east = new ChunkPos(west.x + 1, west.z);
            level.getChunk(west.x, west.z);
            level.getChunk(east.x, east.z);
            this.z = west.getMiddleBlockZ();
            this.y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, west.getMinBlockX(), z);
        }

        /**
         * The first chunk from a start chunk, along Z, with no loaded chunk and no chunk of an earlier test within
         * {@link #FREE_RADIUS} chunks of it and of the chunk east of it. The other tests and systems do not change the
         * state of these chunks. The tests are near each other, so an earlier test can use a chunk near the start
         * chunk. The server unloads the chunks of an earlier test after a time that is not always the same, so a check
         * of the loaded chunks only does not find them.
         */
        private static ChunkPos freeChunk(ServerLevel level, ChunkPos start) {
            for (int step = 0; step < 64; step++) {
                ChunkPos candidate = new ChunkPos(start.x, start.z + step * (2 * FREE_RADIUS + 1));
                if (isFree(level, candidate)) {
                    USED.add(candidate.toLong());
                    USED.add(ChunkPos.asLong(candidate.x + 1, candidate.z));
                    return candidate;
                }
            }
            throw new IllegalStateException("No free chunk near " + start);
        }

        private static boolean isFree(ServerLevel level, ChunkPos chunk) {
            for (int x = chunk.x - FREE_RADIUS; x <= chunk.x + 1 + FREE_RADIUS; x++) {
                for (int z = chunk.z - FREE_RADIUS; z <= chunk.z + FREE_RADIUS; z++) {
                    if (level.getChunkSource().hasChunk(x, z) || USED.contains(ChunkPos.asLong(x, z))) {
                        return false;
                    }
                }
            }
            return true;
        }

        /** A position relative to the west side of {@code west}, at the ground surface plus dy. */
        BlockPos pos(int x, int dy) {
            return new BlockPos(west.getMinBlockX() + x, y + dy, z);
        }

        void ticket(ChunkPos chunk, int distance) {
            level.getChunkSource().addRegionTicket(FAR_CHUNK, chunk, distance, chunk);
            level.getChunk(chunk.x, chunk.z);
            tickets.add(new int[] {chunk.x, chunk.z, distance});
        }

        void removeTicket(ChunkPos chunk, int distance) {
            level.getChunkSource().removeRegionTicket(FAR_CHUNK, chunk, distance, chunk);
            tickets.removeIf(t -> t[0] == chunk.x && t[1] == chunk.z && t[2] == distance);
        }

        /** Places a rail. {@link #fixRails()} gives it its shape again. */
        void rail(int x, int dy, RailShape shape) {
            level.setBlock(pos(x, dy), Blocks.RAIL.defaultBlockState().setValue(RailBlock.SHAPE, shape),
                    Block.UPDATE_CLIENTS);
            shapes.put(pos(x, dy), shape);
        }

        /**
         * Sets the shape of each rail again. A rail changes its shape when a rail is placed next to it. A rail that
         * gets the same block again keeps the given shape.
         */
        void fixRails() {
            shapes.forEach((pos, shape) -> level.setBlock(pos,
                    level.getBlockState(pos).setValue(RailBlock.SHAPE, shape), Block.UPDATE_CLIENTS));
        }

        /** Adds a minecart at X relative to the west side of {@code west}, at the ground surface plus dy. */
        <T extends AbstractMinecart> T spawn(EntityType<T> type, double x, double dy) {
            T cart = type.create(level);
            if (cart == null) {
                throw new IllegalStateException("No minecart of type " + type);
            }
            cart.moveTo(west.getMinBlockX() + x, y + dy, z + 0.5);
            level.addFreshEntity(cart);
            return cart;
        }

        /** Fails until the 2 chunks have their entities loaded. */
        void assertLoaded() {
            helper.assertTrue(level.areEntitiesLoaded(west.toLong()) && level.areEntitiesLoaded(east.toLong()),
                    "The entities of the far chunks are not loaded");
        }

        /** Fails until {@code west} ticks its entities, and {@code east} has its entities loaded but does not tick. */
        void assertEdgeReady() {
            BlockPos westPos = west.getMiddleBlockPosition(y);
            BlockPos eastPos = east.getMiddleBlockPosition(y);
            helper.assertTrue(level.isPositionEntityTicking(westPos) && level.areEntitiesLoaded(east.toLong())
                            && !level.isPositionEntityTicking(eastPos),
                    "The far chunks are not ready: west ticks " + level.isPositionEntityTicking(westPos)
                            + ", east loaded " + level.areEntitiesLoaded(east.toLong()) + ", east ticks "
                            + level.isPositionEntityTicking(eastPos));
        }

        void assertEdge(AbstractMinecart cart) {
            helper.assertTrue(level.getEntity(cart.getUUID()) == cart
                            && !level.isPositionEntityTicking(cart.blockPosition()),
                    "The back minecart is not at the edge of the simulation distance: in the level "
                            + (level.getEntity(cart.getUUID()) == cart) + ", removed " + cart.isRemoved()
                            + ", ticks " + level.isPositionEntityTicking(cart.blockPosition()) + ", at "
                            + cart.blockPosition() + ", east " + east);
        }

        void assertLinked(AbstractMinecart front, AbstractMinecart back) {
            helper.assertTrue(CartLinks.get(front).back().equals(Optional.of(back.getUUID()))
                            && CartLinks.get(back).front().equals(Optional.of(front.getUUID())),
                    "The link breaks");
        }

        /** The number of chains on the ground in the 2 chunks. */
        int chains() {
            AABB area = new AABB(west.getMinBlockX(), level.getMinBuildHeight(), west.getMinBlockZ(),
                    east.getMaxBlockX() + 1, level.getMaxBuildHeight(), east.getMaxBlockZ() + 1);
            return level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(Items.CHAIN))
                    .stream().mapToInt(item -> item.getItem().getCount()).sum();
        }

        /** Removes the minecarts and the chains, then the tickets. */
        void clear(List<AbstractMinecart> carts) {
            TrainGameTests.clear(helper, carts);
            AABB area = new AABB(west.getMinBlockX(), level.getMinBuildHeight(), west.getMinBlockZ(),
                    east.getMaxBlockX() + 1, level.getMaxBuildHeight(), east.getMaxBlockZ() + 1);
            level.getEntitiesOfClass(ItemEntity.class, area, item -> item.getItem().is(Items.CHAIN))
                    .forEach(ItemEntity::discard);
            for (int[] t : List.copyOf(tickets)) {
                removeTicket(new ChunkPos(t[0], t[1]), t[2]);
            }
        }
    }
}
