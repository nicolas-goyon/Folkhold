package io.github.nicolasgoyon.folkhold.world.entity.ai;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
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
 * Opens a closed passage on the path of the unit, and closes the passage again after the unit. A passage is a
 * fence gate or a wooden door. See {@link UnitNavigation}.
 *
 * <ul>
 *   <li>The goal starts for a passage in front of the unit on its path, open or closed. The unit opens a closed
 *       passage. A unit that goes through an open passage closes it after itself.</li>
 *   <li>When the unit is on the other side, the goal waits until no entity is in the passage or next to it, and no
 *       mob near the passage has a path through it. Then it closes the passage. Thus a unit does not close the
 *       passage on the unit behind it, and does not close it in front of a unit that follows.</li>
 *   <li>A gate of the village has several fence gates side by side. The unit opens and closes all the fence gates of
 *       the gate together, so a wide mob (for example a mule) can go through.</li>
 *   <li>A unit can have followers, for example the mules of a caravan merchant. The passage also stays open while a
 *       follower near the passage is still on the side where the unit started.</li>
 *   <li>After {@link #MAX_TICKS}, the goal stops. If an entity is still near, the passage stays open.</li>
 *   <li>The goal does not start again for the passage that the unit just left behind.</li>
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
    /** A mob at this horizontal distance from the passage, in blocks, with a path through it, keeps it open. */
    private static final double FOLLOW_DISTANCE = 8.0;
    /** A follower at this distance from the passage or nearer, in blocks, that did not pass, keeps it open. */
    private static final double FOLLOWER_DISTANCE = 16.0;
    /** The largest number of fence gates on each side of a fence gate in one gate. */
    private static final int MAX_GATE_SIDE = 2;

    private final Mob mob;
    private final Supplier<List<? extends Entity>> followers;
    @Nullable
    private BlockPos passagePos;
    private float startDirX;
    private float startDirZ;
    private boolean passed;
    private boolean closed;
    private int ticks;

    public UsePassageGoal(Mob mob) {
        this(mob, List::of);
    }

    /**
     * Makes the goal for a unit with followers.
     *
     * @param mob       the unit
     * @param followers the entities that follow the unit through the passages
     */
    public UsePassageGoal(Mob mob, Supplier<List<? extends Entity>> followers) {
        this.mob = mob;
        this.followers = followers;
    }

    /** True if the block is a fence gate or the lower half of a wooden door. */
    public static boolean isPassage(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock || DoorBlock.isWoodenDoor(state);
    }

    /** True if the block is a closed fence gate or the lower half of a closed wooden door. */
    public static boolean isClosedPassage(BlockState state) {
        if (UnitNavigation.isClosedGate(state)) {
            return true;
        }
        return DoorBlock.isWoodenDoor(state) && !state.getValue(DoorBlock.OPEN);
    }

    /**
     * True if a path goes through a position: the position is one of the nodes that the mob did not reach yet.
     *
     * @param path the path, or null
     * @param pos  the position
     * @return true if the path goes through the position
     */
    public static boolean pathGoesThrough(@Nullable Path path, BlockPos pos) {
        if (path == null || path.isDone()) {
            return false;
        }
        for (int i = path.getNextNodeIndex(); i < path.getNodeCount(); i++) {
            Node node = path.getNode(i);
            if (node.x == pos.getX() && node.y == pos.getY() && node.z == pos.getZ()) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canUse() {
        Path path = mob.getNavigation().getPath();
        if (path == null || path.isDone()) {
            return false;
        }
        int next = path.getNextNodeIndex();
        int last = Math.min(next + 2, path.getNodeCount());
        for (int i = Math.max(0, next - 1); i < last; i++) {
            Node node = path.getNode(i);
            BlockPos pos = new BlockPos(node.x, node.y, node.z);
            if (!isPassage(mob.level().getBlockState(pos))
                    || mob.distanceToSqr(pos.getX() + 0.5, mob.getY(), pos.getZ() + 0.5) > REACH_SQR) {
                continue;
            }
            // The node behind the unit is a passage that the unit left, except when the unit is still in it.
            if (i < next && !mob.getBoundingBox().intersects(passageBox(pos, 0.0))) {
                continue;
            }
            passagePos = pos;
            return true;
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
            boolean outOfPassage = !mob.getBoundingBox().intersects(passageBox(passagePos, 0.0));
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

    /**
     * True if no entity is in the passage or next to it, no mob near the passage has a path through it, and no
     * follower near the passage is on the side where the unit started. The unit counts only when it is in the
     * passage.
     */
    private boolean isClear() {
        double reachSqr = FOLLOWER_DISTANCE * FOLLOWER_DISTANCE;
        for (Entity follower : followers.get()) {
            float dirX = (float) (passagePos.getX() + 0.5 - follower.getX());
            float dirZ = (float) (passagePos.getZ() + 0.5 - follower.getZ());
            boolean before = startDirX * dirX + startDirZ * dirZ > 0.0F;
            if (follower.isAlive() && before && follower.distanceToSqr(passagePos.getCenter()) <= reachSqr) {
                return false;
            }
        }
        AABB passage = passageBox(passagePos, 0.0);
        AABB near = passageBox(passagePos, CLEAR_DISTANCE);
        for (LivingEntity entity : mob.level().getEntitiesOfClass(LivingEntity.class,
                passageBox(passagePos, FOLLOW_DISTANCE), entity -> entity != mob)) {
            if (entity.getBoundingBox().intersects(near)) {
                return false;
            }
            if (entity instanceof Mob other && pathGoesThrough(other.getNavigation().getPath(), passagePos)) {
                return false;
            }
        }
        return !mob.getBoundingBox().intersects(passage);
    }

    /** The box of a passage, two blocks high, with a horizontal margin. */
    private static AABB passageBox(BlockPos pos, double margin) {
        return new AABB(pos).expandTowards(0.0, 1.0, 0.0).inflate(margin, 0.0, margin);
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
        if (!(state.getBlock() instanceof FenceGateBlock)) {
            return;
        }
        boolean changed = false;
        for (BlockPos pos : gateBlocks(level, passagePos, state)) {
            BlockState gate = level.getBlockState(pos);
            if (gate.getValue(FenceGateBlock.OPEN) != open) {
                level.setBlock(pos, gate.setValue(FenceGateBlock.OPEN, open), Block.UPDATE_ALL);
                level.gameEvent(mob, open ? GameEvent.BLOCK_OPEN : GameEvent.BLOCK_CLOSE, pos);
                changed = true;
            }
        }
        if (changed) {
            level.playSound(null, passagePos, open ? SoundEvents.FENCE_GATE_OPEN : SoundEvents.FENCE_GATE_CLOSE,
                    SoundSource.BLOCKS, 1.0F, 1.0F);
        }
    }

    /**
     * The fence gates of one gate: the fence gate at the position, and the fence gates next to it in the same line
     * with the same axis, up to {@link #MAX_GATE_SIDE} on each side.
     *
     * @param level the level
     * @param pos   the position of a fence gate
     * @param state the block state of this fence gate
     * @return the positions of the fence gates
     */
    public static List<BlockPos> gateBlocks(Level level, BlockPos pos, BlockState state) {
        Direction facing = state.getValue(FenceGateBlock.FACING);
        List<BlockPos> blocks = new ArrayList<>();
        blocks.add(pos);
        for (Direction side : List.of(facing.getClockWise(), facing.getCounterClockWise())) {
            BlockPos next = pos.relative(side);
            for (int i = 0; i < MAX_GATE_SIDE; i++) {
                BlockState nextState = level.getBlockState(next);
                if (!(nextState.getBlock() instanceof FenceGateBlock)
                        || nextState.getValue(FenceGateBlock.FACING).getAxis() != facing.getAxis()) {
                    break;
                }
                blocks.add(next);
                next = next.relative(side);
            }
        }
        return blocks;
    }
}
