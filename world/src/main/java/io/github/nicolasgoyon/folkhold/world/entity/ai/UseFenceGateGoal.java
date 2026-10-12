package io.github.nicolasgoyon.folkhold.world.entity.ai;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * Opens a closed fence gate on the path of the unit, and closes it again when the unit is on the other side.
 * A gate that was open stays open. See {@link UnitNavigation}.
 */
public class UseFenceGateGoal extends Goal {
    /** The unit opens a gate at this horizontal distance (squared), in blocks. */
    private static final double REACH_SQR = 2.25;
    /** The goal stops after this time, in ticks, also if the unit did not pass the gate. */
    private static final int MAX_TICKS = 100;

    private final Mob mob;
    @Nullable
    private BlockPos gatePos;
    private float startDirX;
    private float startDirZ;
    private boolean passed;
    private int ticks;

    public UseFenceGateGoal(Mob mob) {
        this.mob = mob;
    }

    @Override
    public boolean canUse() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int last = Math.min(path.getNextNodeIndex() + 2, path.getNodeCount());
        for (int i = Math.max(0, path.getNextNodeIndex() - 1); i < last; i++) {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (UnitNavigation.isClosedGate(mob.level().getBlockState(pos))
                    && mob.distanceToSqr(pos.getX() + 0.5, mob.getY(), pos.getZ() + 0.5) <= REACH_SQR) {
                gatePos = pos;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !passed && ticks < MAX_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        passed = false;
        ticks = 0;
        startDirX = (float) (gatePos.getX() + 0.5 - mob.getX());
        startDirZ = (float) (gatePos.getZ() + 0.5 - mob.getZ());
        setOpen(true);
    }

    @Override
    public void tick() {
        ticks++;
        float dirX = (float) (gatePos.getX() + 0.5 - mob.getX());
        float dirZ = (float) (gatePos.getZ() + 0.5 - mob.getZ());
        // The direction to the gate turns around when the unit is on the other side.
        boolean otherSide = startDirX * dirX + startDirZ * dirZ < 0.0F;
        boolean outOfGate = !mob.getBoundingBox().intersects(new AABB(gatePos));
        if (otherSide && outOfGate) {
            passed = true;
        }
    }

    @Override
    public void stop() {
        if (passed && !isOccupied()) {
            setOpen(false);
        }
        gatePos = null;
    }

    /** True if an entity stands in the gate: the gate stays open. */
    private boolean isOccupied() {
        return !mob.level().getEntitiesOfClass(LivingEntity.class, new AABB(gatePos)).isEmpty();
    }

    private void setOpen(boolean open) {
        Level level = mob.level();
        BlockState state = level.getBlockState(gatePos);
        if (!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        level.setBlock(gatePos, state.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, gatePos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(mob, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, gatePos);
    }
}
