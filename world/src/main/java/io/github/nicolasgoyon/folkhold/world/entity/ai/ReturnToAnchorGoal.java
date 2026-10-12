package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Moves the unit back to its anchor (post, night watch position or home) when it is outside the anchor
 * radius. See {@link FactionUnit#getAnchorPos()}.
 */
public class ReturnToAnchorGoal extends Goal {
    /** The unit stops when it is at this distance from the anchor, in blocks. */
    private static final double ARRIVED_DISTANCE = 1.0;

    private final FactionUnit unit;
    private final double speed;

    public ReturnToAnchorGoal(FactionUnit unit, double speed) {
        this.unit = unit;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    private double distanceToAnchor() {
        return Math.sqrt(unit.distanceToSqr(Vec3.atBottomCenterOf(unit.getAnchorPos())));
    }

    @Override
    public boolean canUse() {
        return unit.getTarget() == null && distanceToAnchor() > unit.getAnchorRadius();
    }

    @Override
    public boolean canContinueToUse() {
        return unit.getTarget() == null && distanceToAnchor() > ARRIVED_DISTANCE && !unit.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos anchor = unit.getAnchorPos();
        unit.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, speed);
    }

    @Override
    public void stop() {
        unit.getNavigation().stop();
    }
}
