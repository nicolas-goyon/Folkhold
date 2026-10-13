package io.github.nicolasgoyon.folkhold.core.sim.group;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Moves the mobile groups on each abstract tick.
 *
 * <ul>
 *   <li>A waiting group starts to travel at its departure tick.</li>
 *   <li>A traveling group moves a fixed distance on its route. At the end of the route, it arrives.</li>
 *   <li>A stopped group does not move.</li>
 *   <li>An abstract group has no risk. Only a death in the world makes a group lost.</li>
 *   <li>The simulation gives each arrived group and each lost group to the listener, then removes it.</li>
 * </ul>
 */
public final class GroupSimulation {

    /** Receives the groups that finish their journey. */
    public interface Listener {
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
     *                      The simulation still removes them when they are finished.
     * @param listener      the listener
     * @return the report
     */
    public static Report tick(MobileGroups groups, long tickNumber, double blocksPerTick,
            Predicate<MobileGroup> frozen, Listener listener) {
        int moved = 0;
        List<MobileGroup> finished = new ArrayList<>();
        for (MobileGroup group : groups.all()) {
            if (!group.isFinished() && !frozen.test(group) && advance(group, tickNumber, blocksPerTick)) {
                moved++;
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
        if (group.state() == GroupState.WAITING) {
            if (tickNumber < group.departureTick()) {
                return false;
            }
            group.setState(GroupState.TRAVELING);
        }
        if (group.state() != GroupState.TRAVELING) {
            return false;
        }
        group.setProgress(group.progress() + blocksPerTick);
        if (group.progress() >= group.route().length()) {
            group.setState(GroupState.ARRIVED);
        }
        return true;
    }
}
