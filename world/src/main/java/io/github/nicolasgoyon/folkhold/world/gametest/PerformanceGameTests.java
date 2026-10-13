package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.group.GroupMaterializer;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import io.github.nicolasgoyon.folkhold.world.settlement.SimulationProfiler;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the cost of the simulation on the server thread: routes on the worker thread, no chunk load by
 * the materialization, time measurements. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PerformanceGameTests {
    private static final int FAR_X = 26_000_000;

    private PerformanceGameTests() {
    }

    /** A settlement in the state ruins, so the simulation and the claims ignore it. */
    private static Settlement ruins(MinecraftServer server, int x, int z) {
        Settlement settlement = new Settlement(UUID.randomUUID().toString(), x >> 4, z >> 4, "test_faction",
                "Test route", x, z);
        settlement.setState(SettlementState.RUINS);
        SettlementData data = SettlementData.get(server);
        data.registry().add(settlement);
        data.setDirty();
        return settlement;
    }

    /** A requested route comes from the worker thread some ticks later. The server thread does not wait. */
    @GameTest(template = "empty", timeoutTicks = 400, batch = "performance_route")
    public static void routeComesFromTheWorkerThread(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        Settlement from = ruins(server, FAR_X, 0);
        Settlement to = ruins(server, FAR_X + 700, 300);

        boolean requested = CaravanRoutes.request(server, from, to);
        boolean notYet = CaravanRoutes.cached(server, from, to) == null;

        helper.assertTrue(requested, "The request must go to the worker thread");
        helper.assertTrue(notYet, "The route must not be in the data in the tick of the request");
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(CaravanRoutes.cached(server, from, to) != null,
                        "The route must come from the worker thread"))
                .thenExecute(() -> {
                    Optional<Route> route = CaravanRoutes.cached(server, from, to);
                    helper.assertTrue(route != null && route.isPresent(), "The flat test world must have a route");
                    helper.assertTrue(CaravanRoutes.pending() == 0, "No route must stay on the way");
                    helper.assertTrue(SimulationProfiler.entries().stream()
                            .anyMatch(entry -> entry.name().equals(CaravanRoutes.PROFILER_NAME)),
                            "The profiler must record the time of the route");
                })
                .thenSucceed();
    }

    /** The materialization does not load the chunk of a group far from all players. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "performance_chunk")
    public static void materializationDoesNotLoadChunks(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        int x = FAR_X + 5000;
        Route route = new Route(List.of(new Route.Point(x, 5000), new Route.Point(x + 100, 5000)));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", "test_destination", route);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        boolean loadedBefore = level.hasChunk(x >> 4, 5000 >> 4);

        boolean materialized = GroupMaterializer.materialize(level, group);

        boolean loadedAfter = level.hasChunk(x >> 4, 5000 >> 4);
        GroupMaterializer.dematerialize(level, group, false);
        helper.assertTrue(!loadedBefore, "The test needs a chunk that is not loaded");
        helper.assertTrue(!materialized && !loadedAfter, "The materialization must not load the chunk");
        helper.succeed();
    }

    /** The profiler records the time of a call, and the profile command shows it. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "performance_profiler")
    public static void profilerRecordsTheTime(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        String name = "test system " + UUID.randomUUID();

        SimulationProfiler.time(name, () -> {
        });
        SimulationProfiler.record(name, 3_000_000L);
        CommandSourceStack source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        server.getCommands().performPrefixedCommand(source, "folkhold sim profile");

        Optional<SimulationProfiler.Entry> entry = SimulationProfiler.entries().stream()
                .filter(found -> found.name().equals(name)).findFirst();
        helper.assertTrue(entry.isPresent() && entry.get().calls() == 2, "The profiler must count the 2 calls");
        helper.assertTrue(entry.get().maxNanos() >= 3_000_000L && entry.get().lastNanos() == 3_000_000L,
                "The profiler must keep the maximum and the last time");
        helper.succeed();
    }
}
