package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.core.sim.construction.Reach;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.construction.BlockKinds;
import io.github.nicolasgoyon.folkhold.world.construction.BuilderSite;
import io.github.nicolasgoyon.folkhold.world.entity.AnimatedVillagers;
import io.github.nicolasgoyon.folkhold.world.level.ReadyChunks;
import io.github.nicolasgoyon.folkhold.world.registry.ModProfessions;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.schedule.Activity;

/**
 * A handyman at work on the braziers of its village (see {@link Handymen}). The work controls the villager with two
 * memories of its brain: the walk target and the look target. The other behaviors of the villager stay (doors, swim,
 * panic, sleep).
 *
 * <ul>
 *   <li>The handyman takes a brazier and walks to the free position next to the brazier that is nearest to it.</li>
 *   <li>When the brazier is in its reach zone ({@link #REACH}), the handyman stops and looks at the brazier. It plays
 *       the animation of a use, and the brazier changes {@link #USE_LEAD} ticks later.</li>
 *   <li>With no free position next to the brazier, with no path to the position for {@link #NO_PATH_TIMEOUT} ticks,
 *       or after {@link #MOVE_TIMEOUT} ticks, the handyman stops: the brazier is unreachable for the work.</li>
 * </ul>
 */
final class HandymanWorker {
    /** The speed of a handyman that walks to a brazier, as a multiple of its speed. */
    static final float WALK_SPEED = 0.6F;
    /** A handyman that does not get to its brazier in this number of ticks stops. */
    static final int MOVE_TIMEOUT = 600;
    /** A handyman with no path to its brazier for this number of ticks stops. */
    static final int NO_PATH_TIMEOUT = 100;
    /** The brazier changes this number of ticks after the start of the animation of a use. */
    static final int USE_LEAD = 8;
    /**
     * The zone where a handyman uses a brazier: the blocks next to its feet, from the layer of its floor to one block
     * above its head.
     */
    static final Reach REACH = new Reach(1, 1, 2);

    /** The state of a handyman. */
    enum State {
        /** No brazier: the handyman does the other activities of the villagers. */
        IDLE,
        /** The handyman walks to its brazier. */
        MOVE,
        /** The handyman uses its brazier. */
        WORK
    }

    private final UUID id;
    private State state = State.IDLE;
    private int stateTicks;
    /** The brazier of the handyman, or null. */
    @Nullable
    private BlockPos brazier;
    /** The position next to the brazier where the handyman goes, or null. */
    @Nullable
    private BlockPos stand;
    /** True while the work controls the memories of the villager. */
    private boolean controls;

    HandymanWorker(UUID id) {
        this.id = id;
    }

    UUID id() {
        return id;
    }

    /** True while the handyman uses a brazier: it must not stop before the change. */
    boolean isWorking() {
        return state == State.WORK;
    }

    /**
     * True if a villager can work on the braziers now: an adult handyman that is awake. It is not in the rest time of
     * the villagers, and it does not panic, hide or fight a raid.
     */
    static boolean isAvailable(Villager villager) {
        Brain<Villager> brain = villager.getBrain();
        return villager.isAlive() && !villager.isBaby() && !villager.isSleeping() && !villager.isPassenger()
                && !villager.isLeashed()
                && villager.getVillagerData().getProfession() == ModProfessions.HANDYMAN.get()
                && !brain.isActive(Activity.REST) && !brain.isActive(Activity.PANIC)
                && !brain.isActive(Activity.HIDE) && !brain.isActive(Activity.RAID)
                && !brain.isActive(Activity.PRE_RAID);
    }

    /**
     * One tick of work.
     *
     * @param level    the level
     * @param handymen the handymen of the village
     * @param villager the villager of this handyman
     */
    void tick(ServerLevel level, Handymen handymen, Villager villager) {
        stateTicks++;
        if (brazier != null && !handymen.holds(this, brazier)) {
            // Another cause changed the brazier, or the work stopped.
            brazier = null;
        }
        if (brazier == null) {
            brazier = handymen.claim(this, villager);
            if (brazier == null) {
                release(villager);
                return;
            }
            stand = null;
            setState(State.MOVE);
        }
        BlockPos target = brazier;
        if (state == State.WORK) {
            work(level, handymen, villager, target);
        } else {
            move(level, handymen, villager, target);
        }
    }

    private void move(ServerLevel level, Handymen handymen, Villager villager, BlockPos target) {
        if (villager.onGround() && REACH.contains(BlockKinds.toBuildPos(villager.blockPosition()),
                BlockKinds.toBuildPos(target))) {
            stay(villager);
            look(villager, target);
            AnimatedVillagers.use(villager);
            setState(State.WORK);
            return;
        }
        if (stand == null) {
            stand = standNear(level, target, villager);
        }
        if (stand == null || stateTicks > MOVE_TIMEOUT || noPathFor(level, villager) > NO_PATH_TIMEOUT) {
            FolkholdWorld.LOGGER.debug("Handyman {}: no way to the brazier {}", id, target);
            villager.getBrain().eraseMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE);
            stay(villager);
            handymen.markUnreachable(target);
            brazier = null;
            setState(State.IDLE);
            return;
        }
        controls = true;
        villager.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stand, WALK_SPEED, 0));
        look(villager, target);
    }

    private void work(ServerLevel level, Handymen handymen, Villager villager, BlockPos target) {
        stay(villager);
        look(villager, target);
        if (stateTicks >= USE_LEAD) {
            handymen.apply(level, target);
            brazier = null;
            setState(State.IDLE);
        }
    }

    /**
     * The free position next to a brazier that is nearest to the handyman: on the 8 columns around the brazier, from
     * one layer below the brazier to one layer above it.
     */
    @Nullable
    private static BlockPos standNear(ServerLevel level, BlockPos brazier, Villager villager) {
        BlockPos best = null;
        double bestDistance = Double.MAX_VALUE;
        for (int dy = -1; dy <= 1; dy++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    BlockPos feet = brazier.offset(dx, dy, dz);
                    double distance = feet.distToCenterSqr(villager.position());
                    if ((dx != 0 || dz != 0) && distance < bestDistance && ReadyChunks.isReady(level, feet)
                            && BuilderSite.isStandFree(level, feet)) {
                        best = feet;
                        bestDistance = distance;
                    }
                }
            }
        }
        return best;
    }

    /** The time since the villager has no path to its walk target, in ticks, or 0. */
    private static long noPathFor(ServerLevel level, Villager villager) {
        return villager.getBrain().getMemory(MemoryModuleType.CANT_REACH_WALK_TARGET_SINCE)
                .map(time -> level.getGameTime() - time).orElse(0L);
    }

    /** The handyman stops its work: the villager does the other activities of the villagers again. */
    void release(Villager villager) {
        if (controls) {
            Brain<Villager> brain = villager.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            controls = false;
        }
        brazier = null;
        stand = null;
        setState(State.IDLE);
    }

    private void setState(State next) {
        state = next;
        stateTicks = 0;
    }

    /** The villager stops where it is. */
    private void stay(Villager villager) {
        controls = true;
        villager.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        villager.getNavigation().stop();
    }

    private void look(Villager villager, BlockPos target) {
        controls = true;
        villager.getBrain().setMemory(MemoryModuleType.LOOK_TARGET, new BlockPosTracker(target));
    }
}
