package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.group.RouteFinder;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.group.RoadNetwork;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the road network of the caravan routes. See {@link TestSupport} for the templates and the
 * helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RoadGameTests {
    /** The size of the template {@code arena_large}, in blocks. */
    private static final int LARGE = 32;
    private static final BlockPos PATH = new BlockPos(2, 0, 2);
    private static final BlockPos BRIDGE = new BlockPos(5, 3, 5);
    private static final BlockPos TUNNEL = new BlockPos(8, 1, 8);
    private static final BlockPos BLOCKED = new BlockPos(11, 1, 11);
    private static final BlockPos FLOODED = new BlockPos(13, 1, 13);

    private RoadGameTests() {
    }

    /**
     * A path on the ground, a bridge in the air and a road under a roof are roads. A road block with a block or water
     * above it is not a road. The network keeps the roads in its saved data. The test has access to the sky: the
     * GameTest server puts no roof of barriers above the bridge.
     */
    @GameTest(template = "arena", batch = "road_scan", skyAccess = true)
    public static void scanFindsPathsBridgesAndTunnels(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(PATH, Blocks.DIRT_PATH);
        helper.setBlock(BRIDGE, Blocks.OAK_PLANKS);
        helper.setBlock(TUNNEL, Blocks.COBBLESTONE);
        helper.setBlock(TUNNEL.above(3), Blocks.STONE);
        helper.setBlock(TUNNEL.above(4), Blocks.STONE);
        helper.setBlock(BLOCKED, Blocks.STONE_BRICKS);
        helper.setBlock(BLOCKED.above(), Blocks.STONE);
        helper.setBlock(FLOODED, Blocks.GRAVEL);
        helper.setBlock(FLOODED.above(), Blocks.WATER);
        ServerLevel level = helper.getLevel();
        RoadNetwork network = RoadNetwork.get(level);
        scanArea(helper, network, TestSupport.ARENA_SIZE);

        int path = roadAt(helper, network, PATH);
        int bridge = roadAt(helper, network, BRIDGE);
        int tunnel = roadAt(helper, network, TUNNEL);
        int blocked = roadAt(helper, network, BLOCKED);
        int flooded = roadAt(helper, network, FLOODED);
        RoadNetwork loaded = RoadNetwork.load(network.save(new CompoundTag(), level.registryAccess()),
                level.registryAccess());
        int loadedTunnel = roadAt(helper, loaded, TUNNEL);
        for (BlockPos pos : List.of(PATH, BRIDGE, TUNNEL, TUNNEL.above(3), TUNNEL.above(4), BLOCKED, BLOCKED.above(),
                FLOODED, FLOODED.above())) {
            helper.setBlock(pos, pos.getY() == 0 ? Blocks.SMOOTH_STONE : Blocks.AIR);
        }
        scanArea(helper, network, TestSupport.ARENA_SIZE);

        helper.assertTrue(path == helper.absolutePos(PATH).getY(), "The path must be a road: " + path);
        helper.assertTrue(bridge == helper.absolutePos(BRIDGE).getY(), "The bridge must be a road: " + bridge);
        helper.assertTrue(tunnel == helper.absolutePos(TUNNEL).getY(), "The road under a roof must be a road: "
                + tunnel);
        helper.assertTrue(blocked == RouteFinder.NO_ROAD, "A road block under a block is not a road: " + blocked);
        helper.assertTrue(flooded == RouteFinder.NO_ROAD, "A road block under water is not a road: " + flooded);
        helper.assertTrue(loadedTunnel == tunnel, "The saved data must keep the roads: " + loadedTunnel);
        helper.assertTrue(roadAt(helper, network, TUNNEL) == RouteFinder.NO_ROAD,
                "After the removal of the blocks, the network must forget the road");
        helper.succeed();
    }

    /**
     * A deep river with no boats: the route crosses it on a bridge of planks. The terrain of the search is the river,
     * and the bridge comes from the road network.
     */
    @GameTest(template = "arena_large", batch = "road_bridge")
    public static void routeCrossesTheRiverOnTheBridge(GameTestHelper helper) {
        TestSupport.buildFloor(helper, LARGE);
        for (int x = 4; x <= 27; x++) {
            for (int z = 15; z <= 17; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.OAK_PLANKS);
            }
        }
        RoadNetwork network = RoadNetwork.get(helper.getLevel());
        scanArea(helper, network, LARGE);
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        int ground = origin.getY();
        RouteFinder.HeightSource river = (x, z) -> {
            int local = x - origin.getX();
            return local >= 10 && local <= 21 ? new RouteFinder.Column(ground - 20, 20)
                    : new RouteFinder.Column(ground, 0);
        };
        RouteFinder.Settings settings = new RouteFinder.Settings(2, 2, 3, 2.0, 3.0, false, 1.0, 64.0, 500.0,
                100_000, 1.0, 0.7);
        BlockPos start = helper.absolutePos(new BlockPos(1, 1, 4));
        BlockPos end = helper.absolutePos(new BlockPos(30, 1, 28));

        boolean noBridge = RouteFinder.find(start.getX(), start.getZ(), end.getX(), end.getZ(), river, settings)
                .route().isEmpty();
        Route route = RouteFinder.find(start.getX(), start.getZ(), end.getX(), end.getZ(),
                CaravanRoutes.withRoads(river, network, 1), settings).route().orElse(null);
        for (int x = 4; x <= 27; x++) {
            for (int z = 15; z <= 17; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.SMOOTH_STONE);
            }
        }
        scanArea(helper, network, LARGE);

        helper.assertTrue(noBridge, "With no boats and no bridge, the river stops the route");
        helper.assertTrue(route != null, "The route must cross on the bridge");
        Route.Position middle = route.positionAt(route.progressNear(origin.getX() + 16, origin.getZ() + 16));
        double localZ = middle.z() - origin.getZ();
        helper.assertTrue(localZ >= 14 && localZ <= 18, "The route must cross the river on the bridge: " + middle);
        helper.assertTrue(!route.surfaces().contains(Route.Surface.WATER), "The route must not go into the water");
        helper.assertTrue(route.surfaces().contains(Route.Surface.ROAD), "The bridge must be a road of the route");
        helper.succeed();
    }

    /** Reads the roads of the chunks of the test area again. */
    private static void scanArea(GameTestHelper helper, RoadNetwork network, int size) {
        for (int x = 0; x < size; x += 8) {
            for (int z = 0; z < size; z += 8) {
                network.scan(helper.getLevel().getChunkAt(helper.absolutePos(new BlockPos(x, 0, z))));
            }
        }
    }

    private static int roadAt(GameTestHelper helper, RoadNetwork network, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return network.roadAt(pos.getX(), pos.getZ());
    }
}
