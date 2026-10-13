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
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.level.pathfinder.Node;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;

/**
 * Opens a closed passage on the path of the unit, and closes it again after the unit. A passage is a fence gate or
 * a wooden door. See {@link UnitNavigation}.
 *
 * <ul>
 *   <li>The goal starts only for a closed passage. A passage that is open stays open.</li>
 *   <li>When the unit is on the other side, the goal waits until no other entity is in the passage or next to it.
 *       Then it closes the passage. Thus a unit does not close the passage on the unit behind it.</li>
 *   <li>After {@link #MAX_TICKS}, the goal stops. If an entity is still near, the passage stays open.</li>
 * </ul>
 *
 * <p>The goal has no flags: it does not stop the movement goals of the unit.
 */
public class UsePassageGoal extends Goal {
    /** The unit opens a passage at this horizontal distance (squared), in blocks. */
    private static final double REACH_SQR = 4.0;
    /** The goal stops after this time, in ticks, also if the unit did not pass. */
    private static final int MAX_TICKS = 160;
    /** An entity at this horizontal distance from the passage, in blocks, keeps it open. */
    private static final double CLEAR_DISTANCE = 1.5;

    private final Mob mob;
    @Nullable
    private BlockPos passagePos;
    private float startDirX;
    private float startDirZ;
    private boolean passed;
    private boolean closed;
    private int ticks;

    public UsePassageGoal(Mob mob) {
        this.mob = mob;
    }

    /** True if the block is a closed fence gate or the lower half of a closed wooden door. */
    public static boolean isClosedPassage(BlockState state) {
        if (UnitNavigation.isClosedGate(state)) {
            return true;
        }
        return DoorBlock.isWoodenDoor(state) && !state.getValue(DoorBlock.OPEN);
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
            if (isClosedPassage(mob.level().getBlockState(pos))
                    && mob.distanceToSqr(pos.getX() + 0.5, mob.getY(), pos.getZ() + 0.5) <= REACH_SQR) {
                passagePos = pos;
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canContinueToUse() {
        return !closed && ticks < MAX_TICKS;
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void start() {
        passed = false;
        closed = false;
        ticks = 0;
        startDirX = (float) (passagePos.getX() + 0.5 - mob.getX());
        startDirZ = (float) (passagePos.getZ() + 0.5 - mob.getZ());
        setOpen(true);
    }

    @Override
    public void tick() {
        ticks++;
        if (!passed) {
            float dirX = (float) (passagePos.getX() + 0.5 - mob.getX());
            float dirZ = (float) (passagePos.getZ() + 0.5 - mob.getZ());
            // The direction to the passage turns around when the unit is on the other side.
            boolean otherSide = startDirX * dirX + startDirZ * dirZ < 0.0F;
            boolean outOfPassage = !mob.getBoundingBox().intersects(passageBox(0.0));
            passed = otherSide && outOfPassage;
        }
        if (passed && isClear()) {
            setOpen(false);
            closed = true;
        }
    }

    @Override
    public void stop() {
        if (!closed && isClear()) {
            setOpen(false);
        }
        passagePos = null;
    }

    /** True if no entity is in the passage or next to it. The unit counts only when it is in the passage. */
    private boolean isClear() {
        AABB passage = passageBox(0.0);
        return mob.level().getEntitiesOfClass(LivingEntity.class, passageBox(CLEAR_DISTANCE),
                entity -> entity != mob || entity.getBoundingBox().intersects(passage)).isEmpty();
    }

    /** The box of the passage, two blocks high, with a horizontal margin. */
    private AABB passageBox(double margin) {
        return new AABB(passagePos).expandTowards(0.0, 1.0, 0.0).inflate(margin, 0.0, margin);
    }

    private void setOpen(boolean open) {
        Level level = mob.level();
        BlockState state = level.getBlockState(passagePos);
        if (state.getBlock() instanceof DoorBlock door) {
            if (DoorBlock.isWoodenDoor(state) && door.isOpen(state) != open) {
                door.setOpen(mob, level, state, passagePos, open);
            }
            return;
        }
        if (!(state.getBlock() instanceof FenceGateBlock) || state.getValue(FenceGateBlock.OPEN) == open) {
            return;
        }
        level.setBlock(passagePos, state.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL);
        level.playSound(null, passagePos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE,
                SoundSource.BLOCKS, 1.0F, 1.0F);
        level.gameEvent(mob, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, passagePos);
    }
}
