package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.group.CaravanRoutes;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the routes between the settlements. The heights come from the chunk generator of the test
 * world. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteGameTests {
    /** Block X of the test settlements, far from the test area, so they claim no territory near the tests. */
    private static final int FAR_X = 21_000_000;

    private RouteGameTests() {
    }

    /** Two settlements, 800 blocks apart, in the state ruins, so the simulation and the claims ignore them. */
    private static List<Settlement> settlements(MinecraftServer server, int z) {
        SettlementData data = SettlementData.get(server);
        Settlement from = new Settlement(UUID.randomUUID().toString(), FAR_X >> 4, z >> 4, "test_faction",
                "Test origin", FAR_X, z);
        Settlement to = new Settlement(UUID.randomUUID().toString(), (FAR_X + 800) >> 4, (z + 300) >> 4,
                "test_faction", "Test destination", FAR_X + 800, z + 300);
        from.setState(SettlementState.RUINS);
        to.setState(SettlementState.RUINS);
        data.registry().add(from);
        data.registry().add(to);
        data.setDirty();
        return List.of(from, to);
    }

    /** The route goes from the first settlement to the second one, with a length near the direct line. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "route_find")
    public static void routeBetweenTwoSettlements(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Settlement> ends = settlements(server, 0);

        CaravanRoutes.Calculation calculation = CaravanRoutes.calculate(server, ends.get(0), ends.get(1));

        FolkholdWorld.LOGGER.info("Route test: {} cells examined in {} ms", calculation.examined(),
                String.format("%.2f", calculation.nanos() / 1_000_000.0));
        helper.assertTrue(calculation.route().isPresent(),
                "The flat test world must have a route: " + calculation.examined() + " cells examined");
        Route route = calculation.route().get();
        List<Route.Point> points = route.points();
        helper.assertTrue(points.get(0).equals(new Route.Point(FAR_X, 0)), "The route must start at the origin");
        helper.assertTrue(points.get(points.size() - 1).equals(new Route.Point(FAR_X + 800, 300)),
                "The route must end at the destination");
        double direct = Math.hypot(800, 300);
        helper.assertTrue(route.length() >= direct && route.length() < direct * 1.1,
                "The route must follow the direct line on flat ground: " + route.length());
        helper.succeed();
    }

    /** The data keeps the route for the two directions, until a settlement moves. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "route_cache")
    public static void routeIsKeptForTheTwoDirections(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Settlement> ends = settlements(server, 5000);
        Settlement from = ends.get(0);
        Settlement to = ends.get(1);

        helper.assertTrue(CaravanRoutes.cached(server, from, to) == null, "The route must not be calculated yet");
        Route forward = CaravanRoutes.route(server, from, to).orElse(null);
        helper.assertTrue(forward != null, "The flat test world must have a route");

        Optional<Route> again = CaravanRoutes.cached(server, from, to);
        Optional<Route> back = CaravanRoutes.cached(server, to, from);
        helper.assertTrue(again != null && again.isPresent() && again.get().points().equals(forward.points()),
                "The data must keep the route");
        helper.assertTrue(back != null && back.isPresent()
                && back.get().points().equals(CaravanRoutes.reverse(forward).points()),
                "The route in the other direction must be the reverse route");

        to.setHeart(to.x() + WorldConfig.ROUTE_STEP.get() * 4, -60, to.z());
        helper.assertTrue(CaravanRoutes.cached(server, from, to) == null,
                "When a settlement moves, the route must be calculated again");
        helper.succeed();
    }
}
