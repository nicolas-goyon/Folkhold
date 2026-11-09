package io.github.nicolasgoyon.folkhold.world.entity.ai;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.level.pathfinder.Path;

/**
 * The melee attack of a wide mob, for example a faction golem.
 *
 * <p>The path to the target ends near the target, not at the target. For a wide mob, the end of the path can be out
 * of the attack range: the mob stops at less than 2 blocks from the target and does not hit it. While the target does
 * not move, the attack goal makes a new path only by a small chance, and the new path ends at once.
 *
 * <p>In this case only, the mob walks directly to the target. The move has no check of the blocks on the way, so the
 * goal does the move only when all these conditions are true:
 * <ul>
 *   <li>the path is done and it reached the target (not a partial path);</li>
 *   <li>the target is alive and the mob can see it;</li>
 *   <li>the target is nearer than {@link #MAX_DISTANCE} horizontally and nearer than {@link #MAX_HEIGHT}
 *       vertically;</li>
 *   <li>the target is out of the attack range.</li>
 * </ul>
 * The mob stays at its height: a target that a hit throws up does not make the mob jump.
 */
public class WideMobMeleeAttackGoal extends MeleeAttackGoal {
    /** The largest horizontal distance to the target for the direct move, in blocks. */
    static final double MAX_DISTANCE = 3.0;
    /** The largest vertical distance to the target for the direct move, in blocks. */
    static final double MAX_HEIGHT = 1.0;

    private final double speed;

    /**
     * Makes the goal.
     *
     * @param mob   the wide mob
     * @param speed the speed modifier of the chase
     */
    public WideMobMeleeAttackGoal(PathfinderMob mob, double speed) {
        super(mob, speed, true);
        this.speed = speed;
    }

    @Override
    public void tick() {
        super.tick();
        LivingEntity target = mob.getTarget();
        if (target != null && isOutOfReachAtPathEnd(target)) {
            mob.getMoveControl().setWantedPosition(target.getX(), mob.getY(), target.getZ(), speed);
        }
    }

    /** True if the mob is at the end of a complete path to the target, near the target and out of attack range. */
    private boolean isOutOfReachAtPathEnd(LivingEntity target) {
        Path path = mob.getNavigation().getPath();
        if (path == null || !path.isDone() || !path.canReach() || !target.isAlive()) {
            return false;
        }
        double dx = target.getX() - mob.getX();
        double dz = target.getZ() - mob.getZ();
        return dx * dx + dz * dz < MAX_DISTANCE * MAX_DISTANCE
                && Math.abs(target.getY() - mob.getY()) < MAX_HEIGHT
                && !mob.isWithinMeleeAttackRange(target)
                && mob.getSensing().hasLineOfSight(target);
    }
}
