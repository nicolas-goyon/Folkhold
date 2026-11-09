package io.github.nicolasgoyon.folkhold.world.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;

/**
 * The melee attack of a faction golem.
 *
 * <p>The path to the target ends near the target, not at the target. A golem is wide, so the end of its path can be
 * out of its attack range: the golem stops at less than 2 blocks from the target and does not hit it. While the target
 * does not move, the attack goal does not make a new path. When the path is done and the target is out of the attack
 * range, the golem walks directly to the target, if it can see the target.
 */
public class GolemMeleeAttackGoal extends MeleeAttackGoal {
    private final double speed;

    /**
     * Makes the goal.
     *
     * @param mob   the golem
     * @param speed the speed modifier of the chase
     */
    public GolemMeleeAttackGoal(PathfinderMob mob, double speed) {
        super(mob, speed, true);
        this.speed = speed;
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = mob.getTarget();
        if (target != null && mob.getNavigation().isDone() && !mob.isWithinMeleeAttackRange(target)
                && mob.getSensing().hasLineOfSight(target)) {
            mob.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
        }
    }
}
