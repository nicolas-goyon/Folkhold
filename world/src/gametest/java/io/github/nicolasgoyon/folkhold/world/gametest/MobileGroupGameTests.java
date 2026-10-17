package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupMember;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MemberRole;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.SettlementState;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupData;
import io.github.nicolasgoyon.folkhold.world.group.MobileGroupSystem;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.List;
import java.util.UUID;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the mobile groups: saved data, arrival and loss. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MobileGroupGameTests {
    /** Block X of the test settlements, far from the test area, so they claim no territory near the tests. */
    private static final int FAR_X = 20_000_000;

    private MobileGroupGameTests() {
    }

    private static MobileGroup caravan(String destinationId) {
        Route route = new Route(List.of(new Route.Point(FAR_X, 0), new Route.Point(FAR_X + 300, 40),
                new Route.Point(FAR_X + 600, 0)));
        MobileGroup group = new MobileGroup(UUID.randomUUID().toString(), GroupKind.CARAVAN, "test_faction",
                "test_origin", destinationId, route);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.PACK_ANIMAL, 15.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 12.5F));
        return group;
    }

    /** A group keeps its state after a save and a load. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "group_save")
    public static void groupKeepsItsStateAfterSaveAndLoad(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        MobileGroup group = caravan("test_destination");
        group.setState(GroupState.STOPPED);
        group.setProgress(321.5);
        group.setDepartureTick(42);
        group.setPayment(57);
        group.setRoute(new Route(group.route().points(), List.of(Route.Surface.LAND, Route.Surface.WATER)));
        group.setProgress(321.5);
        group.setRouteReady(false);
        group.cargo().set("minecraft:bread", 17);
        group.cargo().set("minecraft:iron_ingot", 3);
        group.members().get(2).setHealth(0.0F);
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().add(group);

        CompoundTag tag = data.save(new CompoundTag(), server.registryAccess());
        MobileGroup loaded = MobileGroupData.load(tag, server.registryAccess()).groups().byId(group.id())
                .orElse(null);
        data.groups().remove(group.id());

        helper.assertTrue(loaded != null, "The loaded data must have the group");
        helper.assertTrue(loaded.kind() == GroupKind.CARAVAN && loaded.factionId().equals("test_faction")
                && loaded.originId().equals("test_origin") && loaded.destinationId().equals("test_destination"),
                "The group must keep its kind, faction, origin and destination");
        helper.assertTrue(loaded.route().points().equals(group.route().points())
                && loaded.route().surfaces().equals(group.route().surfaces()),
                "The group must keep its route and the surfaces of the route");
        helper.assertTrue(!loaded.isRouteReady(), "The group must keep the state of its detailed route");
        helper.assertTrue(loaded.state() == GroupState.STOPPED && loaded.departureTick() == 42
                && loaded.payment() == 57 && Math.abs(loaded.progress() - 321.5) < 1e-6,
                "The group must keep its state, departure, payment and progress");
        helper.assertTrue(loaded.cargo().asMap().equals(group.cargo().asMap()), "The group must keep its cargo");
        helper.assertTrue(loaded.members().size() == 3, "The group must keep its members");
        for (int i = 0; i < 3; i++) {
            GroupMember expected = group.members().get(i);
            GroupMember actual = loaded.members().get(i);
            helper.assertTrue(actual.id().equals(expected.id()) && actual.role() == expected.role()
                    && actual.health() == expected.health(), "Member " + i + " must keep its id, role and health");
        }
        helper.succeed();
    }

    /** At the end of its route, a group puts its cargo into the stock of its destination and disappears. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "group_arrival")
    public static void arrivalPutsTheCargoIntoTheStock(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SettlementData settlements = SettlementData.get(server);
        Settlement destination = new Settlement(UUID.randomUUID().toString(), FAR_X >> 4, 0, "test_faction",
                "Test destination", FAR_X + 600, 0);
        settlements.registry().add(destination);
        settlements.setDirty();
        int before = destination.economy().stock().count("minecraft:bread");

        MobileGroup group = caravan(destination.id());
        group.setState(GroupState.TRAVELING);
        group.setProgress(group.route().length() - 1.0);
        group.cargo().set("minecraft:bread", 10);
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().add(group);

        MobileGroupSystem.tick(server, new TickInfo(settlements.tickNumber() + 1, false));

        int after = destination.economy().stock().count("minecraft:bread");
        destination.setState(SettlementState.RUINS);
        settlements.setDirty();
        helper.assertTrue(data.groups().byId(group.id()).isEmpty(), "The arrived group must disappear");
        helper.assertTrue(after - before == 10, "The stock must get the cargo: " + before + " -> " + after);
        helper.succeed();
    }

    /** The cargo of a lost group does not arrive. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "group_lost")
    public static void lostGroupDeliversNothing(GameTestHelper helper) {
        MinecraftServer server = helper.getLevel().getServer();
        SettlementData settlements = SettlementData.get(server);
        Settlement destination = new Settlement(UUID.randomUUID().toString(), FAR_X >> 4, 0, "test_faction",
                "Test destination", FAR_X + 600, 0);
        settlements.registry().add(destination);
        settlements.setDirty();
        int before = destination.economy().stock().count("minecraft:bread");

        MobileGroup group = caravan(destination.id());
        group.setState(GroupState.TRAVELING);
        group.setProgress(group.route().length() - 1.0);
        group.cargo().set("minecraft:bread", 10);
        group.onMemberDied(group.merchant().orElseThrow().id());
        MobileGroupData data = MobileGroupData.get(server);
        data.groups().add(group);

        MobileGroupSystem.tick(server, new TickInfo(settlements.tickNumber() + 1, false));

        int after = destination.economy().stock().count("minecraft:bread");
        destination.setState(SettlementState.RUINS);
        settlements.setDirty();
        helper.assertTrue(data.groups().byId(group.id()).isEmpty(), "The lost group must disappear");
        helper.assertTrue(after == before, "The stock must not get the cargo: " + before + " -> " + after);
        helper.succeed();
    }
}
