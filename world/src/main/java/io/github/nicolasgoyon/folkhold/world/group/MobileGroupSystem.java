package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupKind;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupSimulation;
import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.settlement.Settlement;
import io.github.nicolasgoyon.folkhold.core.sim.tick.TickScheduler.TickInfo;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.quest.Escorts;
import io.github.nicolasgoyon.folkhold.world.settlement.LevelTables;
import io.github.nicolasgoyon.folkhold.world.settlement.SettlementData;
import java.util.Map;
import java.util.Optional;
import net.minecraft.server.MinecraftServer;

/**
 * The {@link io.github.nicolasgoyon.folkhold.world.settlement.WorldSystem} of the mobile groups. On each abstract
 * tick, it moves the groups on their routes (see {@link GroupSimulation}). A group with entities does not move: its
 * entities move (see {@link GroupMaterializer}).
 *
 * <ul>
 *   <li>A waiting group with no detailed route asks for it (see {@link CaravanRoutes#requestDetail}).</li>
 *   <li>A traveling merchant at the end of its route stays near the village. It does not arrive.</li>
 *   <li>When a group arrives, its cargo goes into the stock of the destination settlement, up to the stock capacity
 *       of the settlement. A destination that is not active gets nothing.</li>
 *   <li>When a caravan arrives, the destination pays the origin (see {@link VillageTrade#onArrival}).</li>
 *   <li>The cargo of a lost group does not arrive.</li>
 *   <li>The entities of an arrived group stay in the village for some time (see
 *       {@link GroupMaterializer#release}). The entities of a lost group disappear. The escort quest of the group
 *       ends (see {@link Escorts#onGroupFinished}).</li>
 * </ul>
 */
public final class MobileGroupSystem {

    private MobileGroupSystem() {
    }

    /** Runs one abstract tick of the mobile groups. */
    public static void tick(MinecraftServer server, TickInfo tick) {
        MobileGroupData data = MobileGroupData.get(server);
        if (data.groups().size() == 0) {
            return;
        }
        for (MobileGroup group : data.groups().all()) {
            // The requests on the way are not saved: a group of a saved world asks for its route again.
            if (group.state() == GroupState.WAITING && !group.isRouteReady()) {
                CaravanRoutes.requestDetail(server, group);
            }
        }
        GroupSimulation.Report report = GroupSimulation.tick(data.groups(), tick.number(),
                WorldConfig.GROUP_BLOCKS_PER_TICK.get(), GroupMaterializer::isMaterialized,
                new GroupSimulation.Listener() {
                    @Override
                    public boolean onEndOfRoute(MobileGroup group, long tickNumber) {
                        return MobileGroupSystem.onEndOfRoute(group, tickNumber);
                    }

                    @Override
                    public void onArrival(MobileGroup group) {
                        Escorts.onGroupFinished(server, group);
                        GroupMaterializer.release(server.overworld(), group);
                        deliver(server, group);
                        if (group.kind() == GroupKind.CARAVAN) {
                            VillageTrade.onArrival(server, group);
                        }
                    }

                    @Override
                    public void onLost(MobileGroup group) {
                        Escorts.onGroupFinished(server, group);
                        GroupMaterializer.dematerialize(server.overworld(), group, false);
                        FolkholdWorld.LOGGER.debug("Mobile group lost: {}", group);
                    }
                });
        if (report.moved() > 0 || report.arrived() > 0 || report.lost() > 0) {
            data.setDirty();
        }
    }

    /**
     * A traveling group is at the end of its route. A traveling merchant stays near the village (see
     * {@link TravelingMerchants#stay(MobileGroup, long)}). The other groups arrive.
     *
     * @param group      the group
     * @param tickNumber the number of the abstract tick
     * @return true if the group arrives
     */
    public static boolean onEndOfRoute(MobileGroup group, long tickNumber) {
        if (group.kind() == GroupKind.TRAVELING_MERCHANT) {
            TravelingMerchants.stay(group, tickNumber);
            return false;
        }
        return true;
    }

    /**
     * Puts the cargo of a group into the stock of its destination.
     *
     * @param server the server
     * @param group  the group
     * @return the quantity of items that the stock accepted
     */
    public static int deliver(MinecraftServer server, MobileGroup group) {
        SettlementData settlements = SettlementData.get(server);
        Optional<Settlement> destination = settlements.registry().byId(group.destinationId())
                .filter(Settlement::isActive);
        if (destination.isEmpty()) {
            FolkholdWorld.LOGGER.debug("Mobile group arrived at an inactive settlement: {}", group);
            return 0;
        }
        int capacity = LevelTables.get().traits(destination.get().level()).stockCapacity();
        int accepted = 0;
        for (Map.Entry<String, Integer> entry : group.cargo().asMap().entrySet()) {
            accepted += destination.get().economy().stock().add(entry.getKey(), entry.getValue(), capacity);
        }
        settlements.setDirty();
        return accepted;
    }
}
