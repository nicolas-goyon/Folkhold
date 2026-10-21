package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.ArcherEntity;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;

/**
 * The attack of an archer with a crossbow. The archer charges the crossbow, aims while the crossbow holds a bolt, and
 * shoots when the target is in range and in sight. Then it charges again.
 *
 * <ul>
 *   <li>The charge takes the time of the crossbow (25 ticks, less with Quick Charge). During the charge, the archer
 *       uses the crossbow: the clients show the pull of the string.</li>
 *   <li>An archer on a post does not move: it shoots from the post. It drops a target that stays out of range or
 *       out of sight for {@link #GIVE_UP_TICKS}, so it can take a nearer target.</li>
 *   <li>An archer with no post walks to its target until the target is in range and in sight.</li>
 * </ul>
 */
public class CrossbowAttackGoal extends Goal {
    /** An archer on a post drops a target that it cannot shoot for this time, in ticks. */
    static final int GIVE_UP_TICKS = 100;
    /** The archer shoots after this time in sight, in ticks. */
    private static final int AIM_TICKS = 5;
    /** An archer with no post calculates a new path to its target at this interval, in ticks. */
    private static final int PATH_INTERVAL = 10;

    private final ArcherEntity archer;
    private final boolean onPost;
    private final float radiusSqr;
    private final double speed;
    @Nullable
    private LivingEntity target;
    private int seeTime;
    private int outOfReachTime;
    private int nextPath;

    /**
     * @param archer the archer
     * @param onPost true for the goal of an archer on a post, false for an archer with no post
     * @param radius the attack radius, in blocks
     * @param speed  the speed of an archer with no post on the way to its target
     */
    public CrossbowAttackGoal(ArcherEntity archer, boolean onPost, float radius, double speed) {
        this.archer = archer;
        this.onPost = onPost;
        this.radiusSqr = radius * radius;
        this.speed = speed;
        setFlags(onPost ? EnumSet.of(Flag.LOOK) : EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity found = archer.getTarget();
        if ((archer.getPostPos() != null) != onPost || !archer.holdsCrossbow() || found == null || !found.isAlive()
                || !archer.canAttack(found)) {
            return false;
        }
        target = found;
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse();
    }

    @Override
    public void stop() {
        target = null;
        seeTime = 0;
        outOfReachTime = 0;
        archer.stopChargingCrossbow();
        if (!onPost) {
            archer.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        if (target == null) {
            return;
        }
        boolean seen = archer.getSensing().hasLineOfSight(target);
        seeTime = seen ? seeTime + 1 : 0;
        boolean inReach = seen && archer.distanceToSqr(target) <= radiusSqr;
        outOfReachTime = inReach ? 0 : outOfReachTime + 1;
        if (onPost && outOfReachTime > GIVE_UP_TICKS) {
            archer.setTarget(null);
            return;
        }
        if (!onPost) {
            if (inReach) {
                archer.getNavigation().stop();
            } else if (archer.tickCount >= nextPath) {
                nextPath = archer.tickCount + PATH_INTERVAL;
                archer.getNavigation().moveTo(target, speed);
            }
        }
        archer.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (!archer.isCrossbowCharged()) {
            archer.chargeCrossbow();
        } else if (inReach && seeTime >= AIM_TICKS) {
            archer.shootCrossbow(target);
        }
    }
}
