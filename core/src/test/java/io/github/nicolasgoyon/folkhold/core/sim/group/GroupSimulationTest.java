package io.github.nicolasgoyon.folkhold.core.sim.group;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class GroupSimulationTest {
    private static final double EPSILON = 1e-9;

    /** A straight route of 500 blocks. */
    private static final Route ROUTE = new Route(List.of(new Route.Point(0, 0), new Route.Point(500, 0)));

    private static MobileGroup caravan(String id) {
        MobileGroup group = new MobileGroup(id, GroupKind.CARAVAN, "faction", "origin", "destination", ROUTE);
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.MERCHANT, 20.0F));
        group.addMember(new GroupMember(UUID.randomUUID(), MemberRole.GUARD, 30.0F));
        return group;
    }

    /** Records the groups that the simulation gives to the listener. */
    private static class Recorder implements GroupSimulation.Listener {
        private final List<MobileGroup> arrived = new ArrayList<>();
        private final List<MobileGroup> lost = new ArrayList<>();

        @Override
        public void onArrival(MobileGroup group) {
            arrived.add(group);
        }

        @Override
        public void onLost(MobileGroup group) {
            lost.add(group);
        }
    }

    @Test
    void waitingGroupLeavesAtItsDepartureTick() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setDepartureTick(5);
        groups.add(group);

        GroupSimulation.tick(groups, 4, 200.0, g -> false, new Recorder());
        assertEquals(GroupState.WAITING, group.state());
        assertEquals(0.0, group.progress(), EPSILON);

        GroupSimulation.tick(groups, 5, 200.0, g -> false, new Recorder());
        assertEquals(GroupState.TRAVELING, group.state());
        assertEquals(200.0, group.progress(), EPSILON);
    }

    @Test
    void waitingGroupWaitsForItsRoute() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setDepartureTick(5);
        group.setRouteReady(false);
        groups.add(group);

        GroupSimulation.tick(groups, 9, 200.0, g -> false, new Recorder());
        assertEquals(GroupState.WAITING, group.state(), "A group with no ready route must wait");

        group.setRoute(new Route(List.of(new Route.Point(0, 0), new Route.Point(300, 0))));
        group.setRouteReady(true);
        GroupSimulation.tick(groups, 10, 200.0, g -> false, new Recorder());
        assertEquals(GroupState.TRAVELING, group.state());
        assertEquals(300.0, group.route().length(), EPSILON);
        assertEquals(200.0, group.progress(), EPSILON);
    }

    @Test
    void travelingGroupMovesAndArrives() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setState(GroupState.TRAVELING);
        groups.add(group);
        Recorder recorder = new Recorder();

        GroupSimulation.Report first = GroupSimulation.tick(groups, 1, 200.0, g -> false, recorder);
        GroupSimulation.tick(groups, 2, 200.0, g -> false, recorder);
        assertEquals(1, first.moved());
        assertEquals(400.0, group.progress(), EPSILON);
        assertTrue(recorder.arrived.isEmpty());

        GroupSimulation.Report last = GroupSimulation.tick(groups, 3, 200.0, g -> false, recorder);
        assertEquals(1, last.arrived());
        assertEquals(List.of(group), recorder.arrived);
        assertEquals(GroupState.ARRIVED, group.state());
        assertEquals(500.0, group.progress(), EPSILON);
        assertTrue(groups.byId("a").isEmpty(), "The simulation removes an arrived group");
    }

    @Test
    void stoppedAndFrozenGroupsDoNotMove() {
        MobileGroups groups = new MobileGroups();
        MobileGroup stopped = caravan("stopped");
        stopped.setState(GroupState.STOPPED);
        stopped.setProgress(100.0);
        MobileGroup frozen = caravan("frozen");
        frozen.setState(GroupState.TRAVELING);
        frozen.setProgress(100.0);
        groups.add(stopped);
        groups.add(frozen);

        GroupSimulation.Report report = GroupSimulation.tick(groups, 1, 200.0, g -> g == frozen, new Recorder());

        assertEquals(0, report.moved());
        assertEquals(100.0, stopped.progress(), EPSILON);
        assertEquals(100.0, frozen.progress(), EPSILON);
        assertEquals(2, groups.size());
    }

    @Test
    void listenerCanKeepAGroupAtTheEndOfItsRoute() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setState(GroupState.TRAVELING);
        group.setProgress(450.0);
        groups.add(group);
        Recorder recorder = new Recorder() {
            @Override
            public boolean onEndOfRoute(MobileGroup ended, long tickNumber) {
                ended.setState(GroupState.STOPPED);
                return false;
            }
        };

        GroupSimulation.tick(groups, 1, 200.0, g -> false, recorder);

        assertEquals(GroupState.STOPPED, group.state());
        assertEquals(500.0, group.progress(), EPSILON);
        assertEquals(1, groups.size(), "A kept group stays");
    }

    @Test
    void frozenWaitingGroupLeavesWithNoMovement() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setDepartureTick(3);
        groups.add(group);

        GroupSimulation.tick(groups, 3, 200.0, g -> true, new Recorder());

        assertEquals(GroupState.TRAVELING, group.state());
        assertEquals(0.0, group.progress(), EPSILON);
    }

    @Test
    void deadMerchantMakesTheGroupLost() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setState(GroupState.TRAVELING);
        groups.add(group);
        UUID guard = group.members().get(1).id();
        UUID merchant = group.merchant().orElseThrow().id();

        assertTrue(group.onMemberDied(guard));
        assertEquals(GroupState.TRAVELING, group.state(), "A dead guard does not stop the group");
        assertFalse(group.member(guard).orElseThrow().isAlive());

        assertTrue(group.onMemberDied(merchant));
        assertEquals(GroupState.LOST, group.state());

        Recorder recorder = new Recorder();
        GroupSimulation.Report report = GroupSimulation.tick(groups, 1, 200.0, g -> false, recorder);
        assertEquals(1, report.lost());
        assertEquals(List.of(group), recorder.lost);
        assertTrue(recorder.arrived.isEmpty());
        assertEquals(0, groups.size());
    }

    @Test
    void frozenGroupIsRemovedWhenItIsFinished() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        group.setState(GroupState.LOST);
        groups.add(group);

        GroupSimulation.tick(groups, 1, 200.0, g -> true, new Recorder());

        assertEquals(0, groups.size());
    }

    @Test
    void unknownMemberIsIgnored() {
        MobileGroup group = caravan("a");
        assertFalse(group.onMemberDied(UUID.randomUUID()));
        assertEquals(GroupState.WAITING, group.state());
    }

    @Test
    void registryFindsTheGroupOfAMemberAndOfASettlement() {
        MobileGroups groups = new MobileGroups();
        MobileGroup group = caravan("a");
        groups.add(group);

        assertSame(group, groups.byMember(group.members().get(1).id()).orElseThrow());
        assertTrue(groups.byMember(UUID.randomUUID()).isEmpty());
        assertEquals(List.of(group), groups.bySettlement("destination"));
        assertTrue(groups.bySettlement("other").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> groups.add(caravan("a")));
    }

    @Test
    void newJourneyStartsAtTheBeginningOfTheRoute() {
        MobileGroup group = caravan("a");
        group.setProgress(300.0);
        Route back = new Route(List.of(new Route.Point(500, 0), new Route.Point(0, 0)));

        group.setJourney("destination", "origin", back);

        assertEquals(0.0, group.progress(), EPSILON);
        assertEquals("destination", group.originId());
        assertEquals("origin", group.destinationId());
        assertEquals(new Route.Position(500.0, 0.0), group.position());
    }
}
