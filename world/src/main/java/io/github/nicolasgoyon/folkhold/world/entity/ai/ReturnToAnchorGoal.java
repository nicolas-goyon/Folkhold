package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.entity.FactionUnit;
import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.phys.Vec3;

/**
 * Brings a unit back into its anchor area (see {@link FactionUnit#getAnchorPos()}) when it has no target.
 *
 * <p>When the path finder finds no path, or the unit does not get nearer with the path it had, the goal waits
 * between {@link #MIN_RETRY_TICKS} and {@link #MAX_RETRY_TICKS} before the next search. A unit with an anchor
 * that it cannot reach does not run a path search on each tick.
 */
public class ReturnToAnchorGoal extends Goal {
    /** The unit stops when it is at this distance from the anchor, in blocks. */
    private static final double ARRIVED_DISTANCE = 1.0;
    /** The shortest wait before a new path search after a failure, in ticks. */
    static final int MIN_RETRY_TICKS = 20;
    /** The longest wait before a new path search after a failure, in ticks. */
    static final int MAX_RETRY_TICKS = 40;
    /** A path made progress when the unit got nearer to the anchor by this distance, in blocks. */
    private static final double PROGRESS = 0.5;

    private final FactionUnit unit;
    private final double speed;
    private long retryAt;
    private double startDistance;

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
        return unit.level().getGameTime() >= retryAt && unit.getTarget() == null
                && distanceToAnchor() > unit.getAnchorRadius();
    }

    @Override
    public boolean canContinueToUse() {
        return unit.getTarget() == null && distanceToAnchor() > ARRIVED_DISTANCE && !unit.getNavigation().isDone();
    }

    @Override
    public void start() {
        BlockPos anchor = unit.getAnchorPos();
        startDistance = distanceToAnchor();
        if (!unit.getNavigation().moveTo(anchor.getX() + 0.5, anchor.getY(), anchor.getZ() + 0.5, speed)) {
            waitBeforeRetry();
        }
    }

    @Override
    public void stop() {
        unit.getNavigation().stop();
        if (unit.getTarget() == null && distanceToAnchor() > startDistance - PROGRESS
                && distanceToAnchor() > unit.getAnchorRadius()) {
            waitBeforeRetry();
        }
    }

    private void waitBeforeRetry() {
        retryAt = unit.level().getGameTime() + MIN_RETRY_TICKS
                + unit.getRandom().nextInt(MAX_RETRY_TICKS - MIN_RETRY_TICKS + 1);
    }
}
