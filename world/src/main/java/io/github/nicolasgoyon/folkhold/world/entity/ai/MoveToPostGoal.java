package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Moves the unit to its post (a tower or a wall) when its village is not calm.
 *
 * <p>The goal stops when the unit is at the post. Then {@link FactionUnit} keeps the unit near the post
 * while the village is not calm.
 */
public class MoveToPostGoal extends Goal {
    private static final double ARRIVED_DISTANCE_SQR = 2.0;

    private final FactionUnit unit;
    private final double speed;

    public MoveToPostGoal(FactionUnit unit, double speed) {
        this.unit = unit;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        BlockPos post = unit.getPostPos();
        return post != null && unit.isVillageAlerted() && unit.distanceToSqr(Vec3.atBottomCenterOf(post)) > ARRIVED_DISTANCE_SQR;
    }

    @Override
    public boolean canContinueToUse() {
        return canUse() && !unit.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos post = unit.getPostPos();
        if (post != null) {
            unit.getNavigation().moveTo(post.getX() + 0.5, post.getY(), post.getZ() + 0.5, speed);
        }
    }

    @Override
    public void stop() {
        unit.getNavigation().stop();
    }
}
