package io.github.nicolasgoyon.folkhold.balance.gametest;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks.LinkResult;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the links when a minecart of a train is at the edge of the simulation distance, or not loaded.
 *
 * <p>The back minecart of each test is in a chunk far from the test. A ticket of level 33 loads this chunk at the
 * status "full": the level gives its entities, but does not tick them. This is the state of a chunk at the edge of
 * the simulation distance. The removal of the ticket unloads the chunk.
 */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class TrainLoadingGameTests {
    /** A ticket that loads one chunk at the status "full", with no ticks. */
    private static final TicketType<ChunkPos> FAR_CHUNK =
            TicketType.create("folkhold_balance_far_chunk", Comparator.comparingLong(ChunkPos::toLong));
    /** The distance in blocks along X from the test to the far chunk. */
    private static final int FAR_OFFSET = 2048;
    /** The largest distance for the links of these tests, in blocks: the two minecarts are far apart. */
    private static final double FAR_LINK_DISTANCE = 4096.0;

    private TrainLoadingGameTests() {
    }

    /**
     * The back minecart is in a chunk that the level gives but does not tick. The fueled furnace minecart in front
     * does not move, and the link does not break after more than the break ticks.
     */
    @GameTest(template = "bench", timeoutTicks = 300, batch = "balance_rails_loading")
    public static void trainStopsAtTheEdgeOfTheSimulationDistance(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        bench(helper);
        ChunkPos far = loadFarChunk(helper);
        MinecartFurnace furnace = helper.spawn(EntityType.FURNACE_MINECART,
                new Vec3(6.5, TrainGameTests.CART_Y, TrainGameTests.BENCH_Z + 0.5));
        AbstractMinecart wagon = spawnFar(level, far, EntityType.CHEST_MINECART);
        link(helper, furnace, wagon);
        TrainGameTests.fuel(helper, furnace, new Vec3(5.5, TrainGameTests.CART_Y, TrainGameTests.BENCH_Z + 0.5));
        Vec3 start = furnace.position();
        int wait = 2 * BalanceConfig.CHAIN_BREAK_TICKS.get();
        helper.startSequence()
                .thenIdle(wait)
                .thenExecute(() -> {
                    helper.assertTrue(level.getEntity(wagon.getUUID()) == wagon
                            && !level.isPositionEntityTicking(wagon.blockPosition()),
                            "The far chunk is not at the edge of the simulation distance");
                    helper.assertTrue(CartLinks.get(furnace).back().equals(Optional.of(wagon.getUUID()))
                            && CartLinks.get(wagon).front().equals(Optional.of(furnace.getUUID())),
                            "The link breaks at the edge of the simulation distance");
                    helper.assertTrue(furnace.position().distanceTo(start) < 0.5,
                            "The train does not stop: the furnace minecart moves "
                                    + furnace.position().distanceTo(start) + " blocks");
                    helper.assertTrue(TrainGameTests.chains(helper) == 0, "A chain drops");
                    TrainGameTests.clear(helper, List.of(furnace, wagon));
                    unloadFarChunk(level, far);
                })
                .thenSucceed();
    }

    /**
     * The chunk of the back minecart unloads, then loads again. The two minecarts keep the link, and the minecart that
     * loads again has its link from the saved data of the chunk.
     */
    @GameTest(template = "bench", timeoutTicks = 600, batch = "balance_rails_loading")
    public static void linkStaysWhenAMinecartUnloadsAndLoadsAgain(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        bench(helper);
        ChunkPos far = loadFarChunk(helper);
        AbstractMinecart front = helper.spawn(EntityType.MINECART,
                new Vec3(6.5, TrainGameTests.CART_Y, TrainGameTests.BENCH_Z + 0.5));
        AbstractMinecart back = spawnFar(level, far, EntityType.MINECART);
        UUID backId = back.getUUID();
        link(helper, front, back);
        helper.startSequence()
                .thenExecute(() -> unloadFarChunk(level, far))
                // The chunk is first not accessible, then it unloads and saves its entities.
                .thenWaitUntil(() -> helper.assertTrue(back.isRemoved() && level.getEntity(backId) == null,
                        "The back minecart does not unload"))
                .thenExecute(() -> {
                    helper.assertTrue(CartLinks.get(front).back().equals(Optional.of(backId)),
                            "The front minecart forgets the link when the back minecart unloads");
                    loadFarChunk(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getEntity(backId) instanceof AbstractMinecart,
                        "The back minecart does not load again"))
                .thenExecute(() -> {
                    AbstractMinecart loaded = (AbstractMinecart) level.getEntity(backId);
                    helper.assertTrue(loaded != back, "The back minecart does not come from the saved data");
                    helper.assertTrue(CartLinks.get(loaded).front().equals(Optional.of(front.getUUID())),
                            "The back minecart loads without its link");
                    helper.assertTrue(CartLinks.get(front).back().equals(Optional.of(backId)),
                            "The front minecart loses its link");
                    helper.assertTrue(TrainGameTests.chains(helper) == 0, "A chain drops");
                    TrainGameTests.clear(helper, List.of(front, loaded));
                    unloadFarChunk(level, far);
                })
                .thenSucceed();
    }

    /**
     * The chunk of the back minecart unloads, then the front minecart is destroyed: a chain drops at the front
     * minecart. When the back minecart loads again, it forgets the link and drops no chain.
     */
    @GameTest(template = "bench", timeoutTicks = 600, batch = "balance_rails_loading")
    public static void minecartForgetsTheLinkOfADestroyedMinecartWhenItLoads(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        bench(helper);
        ChunkPos far = loadFarChunk(helper);
        AbstractMinecart front = helper.spawn(EntityType.MINECART,
                new Vec3(6.5, TrainGameTests.CART_Y, TrainGameTests.BENCH_Z + 0.5));
        AbstractMinecart back = spawnFar(level, far, EntityType.MINECART);
        UUID backId = back.getUUID();
        link(helper, front, back);
        helper.startSequence()
                .thenExecute(() -> unloadFarChunk(level, far))
                // The chunk is first not accessible, then it unloads and saves its entities.
                .thenWaitUntil(() -> helper.assertTrue(back.isRemoved() && level.getEntity(backId) == null,
                        "The back minecart does not unload"))
                .thenExecute(() -> {
                    front.kill();
                    helper.assertTrue(TrainGameTests.chains(helper) == 1, "The destroyed minecart drops no chain");
                    loadFarChunk(helper);
                })
                .thenWaitUntil(() -> helper.assertTrue(level.getEntity(backId) instanceof AbstractMinecart,
                        "The back minecart does not load again"))
                .thenExecute(() -> {
                    AbstractMinecart loaded = (AbstractMinecart) level.getEntity(backId);
                    helper.assertTrue(loaded != back, "The back minecart does not come from the saved data");
                    helper.assertTrue(CartLinks.get(loaded).isEmpty(),
                            "The back minecart keeps the link to the destroyed minecart");
                    int farChains = level.getEntitiesOfClass(ItemEntity.class, new AABB(loaded.blockPosition())
                            .inflate(8.0), item -> item.getItem().is(Items.CHAIN)).size();
                    helper.assertTrue(farChains == 0, "The back minecart drops a second chain");
                    TrainGameTests.clear(helper, List.of(loaded));
                    unloadFarChunk(level, far);
                })
                .thenSucceed();
    }

    /** A floor and a straight rail on the bench. */
    private static void bench(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, TrainGameTests.BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
    }

    private static void link(GameTestHelper helper, AbstractMinecart front, AbstractMinecart back) {
        LinkResult result = CartLinks.link(helper.getLevel(), front, back, FAR_LINK_DISTANCE,
                BalanceConfig.TRAIN_MAX_CARTS.get());
        helper.assertTrue(result == LinkResult.LINKED, "The link fails: " + result);
    }

    /** Loads the far chunk of a test at the status "full", with no ticks. */
    private static ChunkPos loadFarChunk(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        ChunkPos pos = new ChunkPos(helper.absolutePos(BlockPos.ZERO).offset(FAR_OFFSET, 0, 0));
        level.getChunkSource().addRegionTicket(FAR_CHUNK, pos, 0, pos);
        level.getChunk(pos.x, pos.z);
        return pos;
    }

    private static void unloadFarChunk(ServerLevel level, ChunkPos pos) {
        level.getChunkSource().removeRegionTicket(FAR_CHUNK, pos, 0, pos);
    }

    /** Adds a minecart on the ground at the middle of the far chunk. */
    private static <T extends AbstractMinecart> T spawnFar(ServerLevel level, ChunkPos pos, EntityType<T> type) {
        T cart = type.create(level);
        if (cart == null) {
            throw new IllegalStateException("No minecart of type " + type);
        }
        int x = pos.getMiddleBlockX();
        int z = pos.getMiddleBlockZ();
        cart.moveTo(x + 0.5, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z + 0.5);
        level.addFreshEntity(cart);
        return cart;
    }
}
