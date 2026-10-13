package io.github.nicolasgoyon.folkhold.world.entity.ai;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * Moves a mob near a leader entity (for example the mules of a caravan near the merchant).
 *
 * <ul>
 *   <li>The mob starts to follow when it is farther than the start distance. It stops at the stop distance.</li>
 *   <li>When the mob is farther than the teleport distance, it moves directly near the leader.</li>
 *   <li>A mob in a vehicle (for example a boat) does not follow.</li>
 * </ul>
 */
public class FollowEntityGoal extends Goal {
    private static final double START_DISTANCE = 6.0;
    private static final double STOP_DISTANCE = 3.0;
    private static final double TELEPORT_DISTANCE = 24.0;
    private static final int REPATH_TICKS = 20;

    private final Mob mob;
    private final UUID leaderId;
    private final double speed;
    private int repathTicks;

    /**
     * Makes the goal.
     *
     * @param mob      the mob that follows
     * @param leaderId the UUID of the leader
     * @param speed    the speed factor
     */
    public FollowEntityGoal(Mob mob, UUID leaderId, double speed) {
        this.mob = mob;
        this.leaderId = leaderId;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Nullable
    private Entity leader() {
        if (!(mob.level() instanceof ServerLevel level)) {
            return null;
        }
        Entity leader = level.getEntity(leaderId);
        return leader != null && leader.isAlive() ? leader : null;
    }

    @Override
    public boolean canUse() {
        Entity leader = leader();
        return leader != null && !mob.isPassenger() && mob.distanceTo(leader) > START_DISTANCE;
    }

    @Override
    public boolean canContinueToUse() {
        Entity leader = leader();
        return leader != null && !mob.isPassenger() && mob.distanceTo(leader) > STOP_DISTANCE;
    }

    @Override
    public void start() {
        repathTicks = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        Entity leader = leader();
        if (leader == null) {
            return;
        }
        mob.getLookControl().setLookAt(leader, 10.0F, mob.getMaxHeadXRot());
        if (mob.distanceTo(leader) > TELEPORT_DISTANCE) {
            mob.getNavigation().stop();
            mob.moveTo(leader.getX(), leader.getY(), leader.getZ(), mob.getYRot(), mob.getXRot());
            return;
        }
        if (--repathTicks <= 0) {
            repathTicks = REPATH_TICKS;
            mob.getNavigation().moveTo(leader, speed);
        }
    }
}
