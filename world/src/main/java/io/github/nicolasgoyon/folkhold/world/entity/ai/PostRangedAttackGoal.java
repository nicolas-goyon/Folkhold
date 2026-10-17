package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.EnumSet;
import javax.annotation.Nullable;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.monster.RangedAttackMob;

/**
 * Ranged attack of a unit on a post. The unit does not move: it shoots the target when the target is in the attack
 * radius and in sight, and looks at it otherwise. The unit does not shoot a target that it cannot attack, for
 * example a player in creative mode. A target that stays out of the attack radius, or out of sight,
 * for {@link #GIVE_UP_TICKS} is dropped, so the unit can take a nearer target. A unit with no post uses the
 * vanilla ranged attack goal.
 */
public class PostRangedAttackGoal extends Goal {
    /** The unit drops a target that it cannot shoot for this time, in ticks. */
    static final int GIVE_UP_TICKS = 100;
    /** The unit shoots after this time in sight, in ticks. */
    private static final int AIM_TICKS = 5;

    private final FactionUnit unit;
    private final RangedAttackMob shooter;
    private final int attackInterval;
    private final float attackRadius;
    private final float attackRadiusSqr;
    @Nullable
    private LivingEntity target;
    private int seeTime;
    private int attackTime = -1;
    private int outOfReachTime;

    /**
     * @param unit           the unit, a ranged attack mob
     * @param attackInterval the time between two shots at the edge of the attack radius, in ticks
     * @param attackRadius   the attack radius, in blocks
     */
    public <T extends FactionUnit & RangedAttackMob> PostRangedAttackGoal(T unit, int attackInterval,
            float attackRadius) {
        this.unit = unit;
        this.shooter = unit;
        this.attackInterval = attackInterval;
        this.attackRadius = attackRadius;
        this.attackRadiusSqr = attackRadius * attackRadius;
        setFlags(EnumSet.of(Flag.LOOK));
    }

    @Override
    public boolean canUse() {
        LivingEntity found = unit.getTarget();
        if (unit.getPostPos() == null || found == null || !found.isAlive() || !unit.canAttack(found)) {
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
        attackTime = -1;
        outOfReachTime = 0;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        double distanceSqr = unit.distanceToSqr(target.getX(), target.getY(), target.getZ());
        boolean seen = unit.getSensing().hasLineOfSight(target);
        seeTime = seen ? seeTime + 1 : 0;
        boolean inReach = seen && distanceSqr <= attackRadiusSqr;
        outOfReachTime = inReach ? 0 : outOfReachTime + 1;
        if (outOfReachTime > GIVE_UP_TICKS) {
            unit.setTarget(null);
            return;
        }
        unit.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--attackTime == 0) {
            if (!inReach || seeTime < AIM_TICKS) {
                attackTime = 1;
                return;
            }
            float distanceFactor = Mth.clamp((float) Math.sqrt(distanceSqr) / attackRadius, 0.1F, 1.0F);
            shooter.performRangedAttack(target, distanceFactor);
            attackTime = attackInterval;
        } else if (attackTime < 0) {
            attackTime = attackInterval;
        }
    }
}
