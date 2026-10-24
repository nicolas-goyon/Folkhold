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
import io.github.nicolasgoyon.folkhold.world.journal.JournalData;
import io.github.nicolasgoyon.folkhold.world.journal.RouteViews;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload.Status;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the routes of the journal ({@link RouteViews}). The heights come from the chunk generator of the
 * test world. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteViewGameTests {
    /** Block X of the test settlements, far from the test area, so they claim no territory near the tests. */
    private static final int FAR_X = 21_000_000;

    private RouteViewGameTests() {
    }

    /** Two settlements, 800 blocks apart, in the state ruins, so the simulation and the claims ignore them. */
    private static List<Settlement> settlements(MinecraftServer server, int z) {
        SettlementData data = SettlementData.get(server);
        Settlement from = new Settlement(UUID.randomUUID().toString(), FAR_X >> 4, z >> 4, "test_faction",
                "Test start", FAR_X, z);
        Settlement to = new Settlement(UUID.randomUUID().toString(), (FAR_X + 800) >> 4, (z + 300) >> 4,
                "test_faction", "Test end", FAR_X + 800, z + 300);
        from.setState(SettlementState.RUINS);
        to.setState(SettlementState.RUINS);
        data.registry().add(from);
        data.registry().add(to);
        data.setDirty();
        return List.of(from, to);
    }

    /** The server does not show a route to a village that is not in the journal of the player. */
    @GameTest(template = "empty", batch = "route_view_refused")
    public static void villageOutsideTheJournalIsRefused(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Settlement> ends = settlements(server, 30_000);
        Settlement from = ends.get(0);
        Settlement to = ends.get(1);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        JournalData journal = JournalData.get(server);
        journal.discover(player.getUUID(), from.id(), 0);

        Status oneEnd = RouteViews.answer(player, from.id(), to.id()).status();
        journal.discover(player.getUUID(), to.id(), 0);
        Status sameVillage = RouteViews.answer(player, from.id(), from.id()).status();
        Status unknown = RouteViews.answer(player, from.id(), "route-view-unknown").status();
        server.getPlayerList().remove(player);

        helper.assertTrue(oneEnd == Status.REFUSED, "A village outside the journal must be refused: " + oneEnd);
        helper.assertTrue(sameVillage == Status.REFUSED, "A route to the same village must be refused");
        helper.assertTrue(unknown == Status.REFUSED, "An unknown village must be refused");
        helper.assertTrue(CaravanRoutes.cached(server, from, to) == null,
                "A refused request must not calculate the route");
        helper.succeed();
    }

    /**
     * The first answer is "pending": the worker thread calculates the route. The next answers (the journal asks again)
     * come with the route. The route is the route of the next caravan between the two villages, in both directions.
     */
    @GameTest(template = "empty", timeoutTicks = 600, batch = "route_view_ready")
    public static void pendingRouteBecomesTheCaravanRoute(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        List<Settlement> ends = settlements(server, 34_000);
        Settlement from = ends.get(0);
        Settlement to = ends.get(1);
        ServerPlayer player = TestSupport.mockPlayer(helper);
        JournalData journal = JournalData.get(server);
        journal.discover(player.getUUID(), from.id(), 0);
        journal.discover(player.getUUID(), to.id(), 0);

        RoutePayload first = RouteViews.answer(player, from.id(), to.id());
        helper.assertTrue(first.status() == Status.PENDING && first.points().isEmpty(),
                "The first answer must be pending, with no route: " + first.status());
        AtomicReference<RoutePayload> ready = new AtomicReference<>();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    RoutePayload answer = RouteViews.answer(player, from.id(), to.id());
                    helper.assertTrue(answer.status() == Status.READY, "The route must become ready");
                    ready.set(answer);
                })
                .thenExecute(() -> {
                    RoutePayload payload = ready.get();
                    RoutePayload reversed = RouteViews.answer(player, to.id(), from.id());
                    server.getPlayerList().remove(player);
                    List<Route.Point> points = payload.points();
                    helper.assertTrue(payload.detailed(), "The flat test world must have a detailed route");
                    helper.assertTrue(points.get(0).equals(new Route.Point(from.x(), from.z()))
                            && points.get(points.size() - 1).equals(new Route.Point(to.x(), to.z())),
                            "The route must go from the start to the end: " + points.get(0));
                    Optional<Route> route = payload.route();
                    helper.assertTrue(route.isPresent(), "The payload must make a route");
                    helper.assertTrue(reversed.status() == Status.READY
                            && reversed.points().equals(route.get().reversed().points()),
                            "The other direction must give the same route, reversed");

                    Route coarse = CaravanRoutes.cached(server, from, to).orElseThrow();
                    MobileGroup caravan = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN,
                            "test_faction", from.id(), to.id(), coarse);
                    caravan.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
                    CaravanRoutes.prepare(server, caravan);
                    helper.assertTrue(caravan.isRouteReady() && caravan.route().points().equals(points),
                            "The next caravan must take the route of the journal");
                })
                .thenSucceed();
    }
}
