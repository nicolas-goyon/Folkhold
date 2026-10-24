package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.SiteSchedule;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * A builder of a construction site: a villager of the village with the builder profession (see {@link BuilderSite}).
 * The site controls the villager with two memories of its brain: the walk target and the look target. The other
 * behaviors of the villager stay (doors, swim, panic, sleep).
 *
 * <ul>
 *   <li>The builder claims a step of the site (see {@link SiteSchedule}). It walks to a position where the block of
 *       the step is in its reach zone: the position to stand of the plan, or a free position near the block.</li>
 *   <li>For a position on a scaffolding column, the builder walks to the bottom of the column and climbs it. To go to
 *       another position, it goes down the column first.</li>
 *   <li>At the position, the builder looks at the block. When the stock of the village has the materials of the
 *       step, the builder plays the animation of a placement, then the step applies.</li>
 *   <li>A builder takes the next step of the site when no builder claimed it, or when the builder is in position
 *       for it and its owner is not (see {@link SiteSchedule#takeNext}).</li>
 *   <li>When the step of the builder can apply and the builder cannot go to its position in some time, it goes there
 *       directly. With no free position, it works from where it is.</li>
 * </ul>
 */
final class BuilderWorker {
    /** The speed of a builder that walks to its position, as a multiple of its speed. */
    static final float WALK_SPEED = 0.6F;
    /** The speed of a builder that climbs a scaffolding column, in blocks for each tick. */
    static final double CLIMB_SPEED = 0.2;
    /**
     * A builder whose step can apply, and that does not get to its position in this number of ticks, goes there
     * directly.
     */
    static final int MOVE_TIMEOUT = 300;
    /** A builder whose step can apply, with no path to its position for this number of ticks, goes there directly. */
    static final int NO_PATH_TIMEOUT = 100;
    /** A builder that does not get to the bottom of a column in this number of ticks goes there directly. */
    static final int DESCEND_TIMEOUT = 200;
    /**
     * A builder whose step can apply, and that is not in position after this number of ticks, works from where it
     * is.
     */
    static final int STEP_TIMEOUT = 2 * MOVE_TIMEOUT;
    /** A builder that walks selects its position again every this number of ticks. */
    static final int RECHECK_TICKS = 20;
    /** A builder with no path to its position tries again every {@link #RECHECK_TICKS} ticks, after this time. */
    private static final int NO_PATH_PAUSE = 20;
    /** A builder at this distance below the top of its column, or nearer, is on the top. */
    private static final double TOP_MARGIN = 0.1;
    /** A builder that went to its position directly has this number of ticks to land there. */
    private static final int LANDING_TICKS = 20;
    /** The animation of a placement starts this number of ticks before the block changes. */
    static final int SWING_LEAD = 8;
    /** A climb takes this number of ticks at most for each block of the column, plus {@link #CLIMB_EXTRA}. */
    private static final int CLIMB_TICKS_PER_BLOCK = 10;
    private static final int CLIMB_EXTRA = 40;

    /** The state of a builder. */
    enum State {
        /** No step, or a new step: the builder does not move yet. */
        IDLE,
        /** The builder walks to its position. */
        MOVE,
        /** The builder climbs a scaffolding column to its position. */
        CLIMB,
        /** The builder is in position: it looks at the block, then the step applies. */
        WORK,
        /** The builder goes down a scaffolding column. */
        DESCEND
    }

    private final UUID id;
    private State state = State.IDLE;
    private int stateTicks;
    /** The step of the builder, or -1. */
    private int step = -1;
    /**
     * The time during which the step of the builder can apply and the builder is not in position, in ticks. During this
     * time, the other steps wait for this builder.
     */
    private int blockingTicks;
    private int workTicks;
    private int blockedTicks;
    /** The position where the builder stands for its step, or null before the choice. */
    @Nullable
    private BlockPos stand;
    /** The bottom of the scaffolding column below the position of the builder, or null. */
    @Nullable
    private BlockPos climbBase;
    /** True after the builder went to its position directly. */
    private boolean teleported;
    /** True when the builder works from where it is: it cannot go to a position. */
    private boolean direct;

    BuilderWorker(UUID id) {
        this.id = id;
    }

    UUID id() {
        return id;
    }

    /** True if the builder climbs or goes down a column: it must not stop on the way. */
    boolean isOnTheWay() {
        return state == State.CLIMB || state == State.DESCEND;
    }

    /**
     * True if a villager can work on a site now: an adult builder that is awake. It does not panic, hide or fight a
     * raid.
     */
    static boolean isAvailable(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        return villager.isAlive() && !villager.isBaby() && !villager.isSleeping() && !villager.isPassenger()
                && !villager.isLeashed()
                && villager.getVillagerData().getProfession() == ModProfessions.BUILDER.get()
                && !brain.isActive(Activity.PANIC) && !brain.isActive(Activity.HIDE)
                && !brain.isActive(Activity.RAID) && !brain.isActive(Activity.PRE_RAID);
    }

    /**
     * One tick of work.
     *
     * @param level    the level
     * @param site     the site
     * @param villager the villager of this builder
     * @param watched  an entity that the builder looks at instead of its work, for example a player in the work zone,
     *                 or null. With an entity, the builder does not move and does not work.
     */
    void tick(ServerLevel level, BuilderSite site, Villager villager, @Nullable Entity watched) {
        stateTicks++;
        if (state == State.CLIMB) {
            climb(villager);
            return;
        }
        if (state == State.DESCEND) {
            descend(level, villager);
            return;
        }
        if (watched != null) {
            stay(villager);
            look(villager, new EntityTracker(watched, true));
            return;
        }
        SiteSchedule<UUID> schedule = site.schedule();
        if (step >= 0 && schedule.claimOf(id).orElse(-1) != step) {
            // Another builder took the step.
            step = -1;
        }
        if (takeNext(level, site, villager)) {
            return;
        }
        OptionalInt claim = schedule.claim(id, index -> site.claimCost(index, villager));
        if (claim.isEmpty()) {
            idle(level, villager);
            return;
        }
        int index = claim.getAsInt();
        if (index != step) {
            startStep(index);
        }
        if (direct || site.canWorkFrom(level, villager, index)) {
            work(level, site, villager, index);
            return;
        }
        if (state == State.WORK) {
            // The builder is not in position any more, for example after a push.
            setState(State.MOVE);
            stand = null;
        }
        move(level, site, villager, index);
    }

    /**
     * Takes the next step of the site (see {@link SiteSchedule#takeNext}): when no builder claimed it, or when this
     * builder is in position for it and its owner is not. The other steps wait for the next step.
     *
     * @return true if the builder took the step and works on it in this tick
     */
    private boolean takeNext(ServerLevel level, BuilderSite site, Villager villager) {
        SiteSchedule<UUID> schedule = site.schedule();
        int next = schedule.next();
        if (!schedule.ordered() || next >= schedule.total() || next == step) {
            return false;
        }
        Optional<BuilderWorker> owner = schedule.owner(next).map(site::worker);
        if (owner.isEmpty()) {
            schedule.takeNext(id);
            startStep(next);
            return false;
        }
        if (owner.get().state == State.WORK || !site.canWorkFrom(level, villager, next)) {
            return false;
        }
        schedule.takeNext(id);
        startStep(next);
        work(level, site, villager, next);
        return true;
    }

    private void startStep(int index) {
        step = index;
        blockingTicks = 0;
        workTicks = 0;
        blockedTicks = 0;
        stand = null;
        climbBase = null;
        teleported = false;
        direct = false;
        setState(State.IDLE);
    }

    private void setState(State next) {
        state = next;
        stateTicks = 0;
    }

    /** No step for the builder: it leaves a column, then the vanilla behaviors of the villager run. */
    private void idle(ServerLevel level, Villager villager) {
        step = -1;
        if (BuilderSite.isOnScaffolding(level, villager)) {
            startDescent(villager);
            return;
        }
        setState(State.IDLE);
    }

    /**
     * The builder is in position: it looks at the block and prepares the step, then plays the animation of a placement
     * and applies the step. A step that cannot apply yet waits: the steps before it apply first.
     */
    private void work(ServerLevel level, BuilderSite site, Villager villager, int index) {
        if (state != State.WORK) {
            setState(State.WORK);
            workTicks = 0;
        }
        stay(villager);
        look(villager, new BlockPosTracker(site.lookPos(index)));
        int needed = site.ticksFor(index);
        int swingAt = Math.max(0, needed - SWING_LEAD);
        if (workTicks < swingAt) {
            workTicks++;
            return;
        }
        if (workTicks == swingAt) {
            if (!site.schedule().canApply(index) || !site.hasMaterials(level, index)) {
                return;
            }
            if (site.isBlocked(level, index)) {
                blocked(level, site, index);
                return;
            }
            blockedTicks = 0;
            AnimatedVillagers.place(villager);
        }
        workTicks++;
        if (workTicks < needed) {
            return;
        }
        switch (site.apply(level, index)) {
            case APPLIED -> {
                step = -1;
                setState(State.IDLE);
            }
            case BLOCKED -> {
                workTicks = swingAt;
                blocked(level, site, index);
            }
            case WAITING -> workTicks = swingAt;
        }
    }

    /** An entity is in the block of the step: after some time, the entity moves out. */
    private void blocked(ServerLevel level, BuilderSite site, int index) {
        blockedTicks++;
        if (blockedTicks >= BuilderSite.BLOCKED_TIMEOUT) {
            site.moveBlockersAside(level, index);
            blockedTicks = 0;
        }
    }

    /**
     * The builder walks to its position, or to the bottom of the column below its position. The builder selects its
     * position again every {@link #RECHECK_TICKS} ticks. Thus it uses the position of the plan when this position
     * becomes free, for example when the steps before it put its scaffolding column.
     */
    private void move(ServerLevel level, BuilderSite site, Villager villager, int index) {
        if (state != State.MOVE) {
            setState(State.MOVE);
        }
        boolean blocking = site.schedule().canApply(index);
        if (blocking) {
            blockingTicks++;
        }
        if (blockingTicks > STEP_TIMEOUT) {
            FolkholdWorld.LOGGER.debug("Builder {}: step {} takes too long, the builder works from {}", id, index,
                    villager.blockPosition());
            direct = true;
            return;
        }
        if (stand == null || stateTicks % RECHECK_TICKS == 0) {
            chooseStand(level, site, villager, index);
        }
        boolean onThisColumn = climbBase != null && isInColumn(villager, climbBase);
        if (BuilderSite.isOnScaffolding(level, villager) && !onThisColumn) {
            startDescent(villager);
            return;
        }
        if (climbBase != null && stand != null) {
            if (onThisColumn) {
                if (villager.getY() >= stand.getY() - TOP_MARGIN || site.isColumnTaken(level, climbBase, this)) {
                    // On the top of the column, or another builder is up the column: the builder waits.
                    stay(villager);
                } else {
                    FolkholdWorld.LOGGER.debug("Builder {}: climbs the column at {} to {}", id, climbBase, stand);
                    setState(State.CLIMB);
                }
                return;
            }
            walk(level, villager, climbBase, 0);
        } else if (stand != null) {
            if (villager.blockPosition().equals(stand)) {
                // The builder is at its position, but not in its middle: it steps to the middle.
                villager.getMoveControl().setWantedPosition(stand.getX() + 0.5, stand.getY(), stand.getZ() + 0.5,
                        WALK_SPEED);
            } else {
                walk(level, villager, stand, 0);
            }
        } else {
            walk(level, villager, site.reachTarget(level, index), 1);
        }
        if (blocking && (blockingTicks > MOVE_TIMEOUT || noPathFor(level, villager) > NO_PATH_TIMEOUT)) {
            goDirectly(level, site, villager, index);
        }
    }

    /** Selects the position of the builder for its step (see {@link BuilderSite#standFor}). */
    private void chooseStand(ServerLevel level, BuilderSite site, Villager villager, int index) {
        BlockPos chosen = site.standFor(level, index, villager);
        if (Objects.equals(chosen, stand)) {
            return;
        }
        stand = chosen;
        climbBase = chosen != null && BuilderSite.isScaffolding(level, chosen.below())
                ? BuilderSite.columnBase(level, chosen.below()) : null;
        teleported = false;
    }

    /** The time since the villager has no path to its walk target, in ticks, or 0. */
    private static long noPathFor(ServerLevel level, Villager villager) {
        Optional<Long> since = villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        return since.map(time -> level.getGameTime() - time).orElse(0L);
    }

    /**
     * The builder cannot get to its position: it goes there directly, once. After that, or with no free position, it
     * works from where it is.
     */
    private void goDirectly(ServerLevel level, BuilderSite site, Villager villager, int index) {
        villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
        if (!teleported && stand != null && site.isStandFor(level, stand, index)) {
            FolkholdWorld.LOGGER.debug("Builder {}: no way to {} for step {}, the builder goes there directly", id,
                    stand, index);
            teleported = true;
            BuilderSite.teleport(villager, stand);
            // The builder gets some ticks to land at its position before it works from where it is.
            blockingTicks = MOVE_TIMEOUT - LANDING_TICKS;
            setState(State.MOVE);
            return;
        }
        FolkholdWorld.LOGGER.debug("Builder {}: no way to a position for step {}, the builder works from {}", id,
                index, villager.blockPosition());
        direct = true;
    }

    /** True if the villager is in the column of a scaffolding block: on the same block X and Z, above its bottom. */
    private static boolean isInColumn(Villager villager, BlockPos base) {
        BlockPos feet = villager.blockPosition();
        return feet.getX() == base.getX() && feet.getZ() == base.getZ() && villager.getY() > base.getY() - 0.5;
    }

    /** The builder climbs the column to its position: it goes up in the middle of the column. */
    private void climb(Villager villager) {
        BlockPos top = stand;
        BlockPos base = climbBase;
        if (top == null || base == null) {
            setState(State.MOVE);
            return;
        }
        stay(villager);
        if (villager.getY() >= top.getY()) {
            // The builder is above the column: it lands on the top.
            if (villager.onGround() || stateTicks > CLIMB_EXTRA + climbTicks(base, top)) {
                setState(State.MOVE);
            }
            return;
        }
        if (stateTicks > climbTicks(base, top)) {
            FolkholdWorld.LOGGER.debug("Builder {}: the climb to {} takes too long, the builder goes there directly",
                    id, top);
            BuilderSite.teleport(villager, top);
            setState(State.MOVE);
            return;
        }
        villager.setPos(base.getX() + 0.5, villager.getY(), base.getZ() + 0.5);
        villager.setDeltaMovement(0.0, CLIMB_SPEED, 0.0);
        villager.resetFallDistance();
    }

    private static int climbTicks(BlockPos base, BlockPos top) {
        return (top.getY() - base.getY()) * CLIMB_TICKS_PER_BLOCK + CLIMB_EXTRA;
    }

    /** The builder goes down the column below it: it does not stand on the scaffolding, and slides down. */
    private void startDescent(Villager villager) {
        setState(State.DESCEND);
        climbBase = villager.blockPosition();
        villager.setShiftKeyDown(true);
        stay(villager);
    }

    private void descend(ServerLevel level, Villager villager) {
        BlockPos column = climbBase;
        if (column == null) {
            villager.setShiftKeyDown(false);
            setState(State.MOVE);
            return;
        }
        stay(villager);
        villager.setShiftKeyDown(true);
        villager.setPos(column.getX() + 0.5, villager.getY(), column.getZ() + 0.5);
        villager.setDeltaMovement(0.0, Math.min(0.0, villager.getDeltaMovement().y), 0.0);
        boolean down = villager.onGround() && !BuilderSite.isOnScaffolding(level, villager);
        if (!down && stateTicks > DESCEND_TIMEOUT) {
            FolkholdWorld.LOGGER.debug("Builder {}: the descent takes too long, the builder goes down directly", id);
            BuilderSite.teleport(villager, BuilderSite.groundBelow(level, villager.blockPosition()));
            down = true;
        }
        if (down) {
            villager.setShiftKeyDown(false);
            climbBase = null;
            stand = null;
            setState(State.MOVE);
        }
    }

    /**
     * The builder leaves the site: it goes down a column first.
     *
     * @return true when the builder is on the ground and can go
     */
    boolean leave(ServerLevel level, Villager villager) {
        if (state == State.DESCEND) {
            stateTicks++;
            descend(level, villager);
            return state != State.DESCEND;
        }
        if (state == State.CLIMB || BuilderSite.isOnScaffolding(level, villager)) {
            startDescent(villager);
            return false;
        }
        return true;
    }

    /** Gives the villager back to its vanilla behaviors. */
    void release(Villager villager) {
        villager.setShiftKeyDown(false);
        Brain<Villager> brain = villager.getBrain();
        brain.eraseMemory(MemoryModuleType.WALK_TARGET);
        brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        step = -1;
        setState(State.IDLE);
    }

    /** Forgets the position of the step: the builder selects a position again. */
    void forgetPosition() {
        if (!isOnTheWay()) {
            stand = null;
            climbBase = null;
            if (state == State.WORK) {
                setState(State.MOVE);
            }
        }
    }

    /** The villager stops where it is. */
    private static void stay(Villager villager) {
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    /**
     * The villager walks to a position. With no path for some time, it tries again only every {@link #RECHECK_TICKS}
     * ticks: a search of a path that fails costs time on the server.
     */
    private void walk(ServerLevel level, Villager villager, BlockPos pos, int closeEnough) {
        if (noPathFor(level, villager) > NO_PATH_PAUSE && stateTicks % RECHECK_TICKS != 0) {
            stay(villager);
            return;
        }
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(pos, WALK_SPEED, closeEnough));
    }

    private static void look(Villager villager, PositionTracker target) {
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, target);
    }
}
