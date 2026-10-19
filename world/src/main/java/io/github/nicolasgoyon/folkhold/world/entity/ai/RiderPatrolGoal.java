package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.RiderEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * A rider on its horse rides from one waypoint of its patrol to the next (see {@link RiderEntity#patrolWaypoint()}).
 *
 * <ul>
 *   <li>The goal runs when the rider is on its horse, has no target and is not routed.</li>
 *   <li>When the path of the rider ends (at the waypoint, or before it for a waypoint that the horse cannot reach), or
 *       after {@link #MAX_TICKS}, the patrol goes on to the next waypoint. Thus a waypoint that the horse cannot
 *       reach does not stop the patrol.</li>
 *   <li>A charge stops the goal. After the charge, the rider rides to the same waypoint again.</li>
 *   <li>After a failed path search, the goal waits {@link #RETRY_TICKS}.</li>
 * </ul>
 */
public class RiderPatrolGoal extends Goal {
    /** The rider rides to one waypoint for this time at most, in ticks. */
    private static final int MAX_TICKS = 1200;
    /** The wait after a failed path search, in ticks. */
    private static final int RETRY_TICKS = 40;
    /** The speed modifier of the horse during the patrol. */
    private static final double PATROL_SPEED = 1.0;

    private final RiderEntity rider;
    @Nullable
    private BlockPos waypoint;
    private int ticks;
    private long retryAt;

    public RiderPatrolGoal(RiderEntity rider) {
        this.rider = rider;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private boolean canPatrol() {
        return rider.isPassenger() && rider.getTarget() == null && !rider.isRouted();
    }

    @Override
    public boolean canUse() {
        if (!canPatrol() || rider.level().getGameTime() < retryAt) {
            return false;
        }
        waypoint = rider.patrolWaypoint();
        return waypoint != null;
    }

    @Override
    public boolean canContinueToUse() {
        return canPatrol() && !rider.getNavigation().isDone() && ticks < MAX_TICKS;
    }

    @Override
    public void start() {
        ticks = 0;
        rider.setPatrolling(true);
        if (waypoint == null || !rider.getNavigation().moveTo(waypoint.getX() + 0.5, waypoint.getY(),
                waypoint.getZ() + 0.5, PATROL_SPEED)) {
            retryAt = rider.level().getGameTime() + RETRY_TICKS;
        }
    }

    @Override
    public void tick() {
        ticks++;
    }

    /**
     * The rider arrived, the path ended, the path search failed or the time is over: the patrol goes on to the next
     * waypoint. A charge keeps the waypoint.
     */
    @Override
    public void stop() {
        if (canPatrol()) {
            rider.nextPatrolWaypoint();
        }
        rider.setPatrolling(false);
        waypoint = null;
    }
}
