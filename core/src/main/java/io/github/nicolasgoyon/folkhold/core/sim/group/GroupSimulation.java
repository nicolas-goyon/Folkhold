package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Moves the mobile groups on each abstract tick.
 *
 * <ul>
 *   <li>A waiting group starts to travel at its departure tick, if its route is ready. Else it waits for its
 *       route.</li>
 *   <li>A traveling group moves a fixed distance on its route. At the end of the route, it arrives, or the
 *       listener keeps it (for example a traveling merchant that stays at a village).</li>
 *   <li>A stopped group does not move.</li>
 *   <li>An abstract group has no risk. Only a death in the world makes a group lost.</li>
 *   <li>The simulation gives each arrived group and each lost group to the listener, then removes it.</li>
 * </ul>
 */
public final class GroupSimulation {

    /** Receives the groups that finish their journey. */
    public interface Listener {
        /**
         * Called when a traveling group is at the end of its route.
         *
         * @param group      the group
         * @param tickNumber the number of the abstract tick
         * @return true if the group arrives. False if the listener keeps the group and changes its state.
         */
        default boolean onEndOfRoute(MobileGroup group, long tickNumber) {
            return true;
        }

        /**
         * Called when a group arrives at its destination, before the simulation removes it.
         *
         * @param group the group, in the state {@link GroupState#ARRIVED}
         */
        void onArrival(MobileGroup group);

        /**
         * Called for a lost group, before the simulation removes it.
         *
         * @param group the group, in the state {@link GroupState#LOST}
         */
        void onLost(MobileGroup group);
    }

    /**
     * Result of one abstract tick.
     *
     * @param moved   the number of groups that moved
     * @param arrived the number of groups that arrived
     * @param lost    the number of lost groups
     */
    public record Report(int moved, int arrived, int lost) {
    }

    private GroupSimulation() {
    }

    /**
     * Runs one abstract tick for all groups.
     *
     * @param groups        the groups
     * @param tickNumber    the number of the abstract tick
     * @param blocksPerTick the distance that a traveling group moves on each abstract tick
     * @param frozen        the groups that the simulation must not move (for example the groups with entities).
     *                      A frozen group still leaves at its departure tick, and the simulation still removes it
     *                      when it is finished.
     * @param listener      the listener
     * @return the report
     */
    public static Report tick(MobileGroups groups, long tickNumber, double blocksPerTick,
            Predicate<MobileGroup> frozen, Listener listener) {
        int moved = 0;
        List<MobileGroup> finished = new ArrayList<>();
        // The listener can add or remove groups: the loop runs on a snapshot.
        for (MobileGroup group : List.copyOf(groups.all())) {
            if (group.isFinished()) {
                finished.add(group);
                continue;
            }
            if (frozen.test(group)) {
                depart(group, tickNumber);
            } else if (advance(group, tickNumber, blocksPerTick)) {
                moved++;
                if (group.state() == GroupState.TRAVELING && group.progress() >= group.route().length()
                        && listener.onEndOfRoute(group, tickNumber)) {
                    group.setState(GroupState.ARRIVED);
                }
            }
            if (group.isFinished()) {
                finished.add(group);
            }
        }
        int arrived = 0;
        int lost = 0;
        for (MobileGroup group : finished) {
            if (group.state() == GroupState.ARRIVED) {
                listener.onArrival(group);
                arrived++;
            } else {
                listener.onLost(group);
                lost++;
            }
            groups.remove(group.id());
        }
        return new Report(moved, arrived, lost);
    }

    /**
     * Moves one group.
     *
     * @return true if the group moved
     */
    static boolean advance(MobileGroup group, long tickNumber, double blocksPerTick) {
        depart(group, tickNumber);
        if (group.state() != GroupState.TRAVELING) {
            return false;
        }
        group.setProgress(group.progress() + blocksPerTick);
        return true;
    }

    /** A waiting group starts to travel at its departure tick, if its route is ready. */
    private static void depart(MobileGroup group, long tickNumber) {
        if (group.state() == GroupState.WAITING && tickNumber >= group.departureTick() && group.isRouteReady()) {
            group.setState(GroupState.TRAVELING);
        }
    }
}
