package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.village.RaidMarch;
import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import io.github.nicolasgoyon.folkhold.world.village.VillageRaids;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.LevelEvent;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.EventHooks;

/**
 * The march of a raider on a faction village (see {@link VillageRaids}). Near a faction village, it replaces the
 * vanilla march of the raid, which goes to random positions toward the center of the raid and stops at a river.
 *
 * <ul>
 *   <li>The route comes from {@link RaidMarch}: the raider walks to the approach of the nearest gate, then through
 *       the gate to the heart.</li>
 *   <li>A raider in front of a closed gate forces it: it hits the gate, and after
 *       {@link WorldConfig#RAID_GATE_FORCE_SECONDS} seconds all the fence gates of the gate open. A gate that closes
 *       again is forced again. A knockback does not lose the time when the raider comes back in
 *       {@link WorldConfig#RAID_GATE_FORCE_MEMORY_SECONDS} seconds.</li>
 *   <li>A raider that forced a gate open and is still in front of the same closed gate
 *       {@link WorldConfig#RAID_GATE_BREAK_AFTER_SECONDS} seconds after its first hit breaks the gate column by
 *       column, as a zombie breaks a door, until the opening is as wide as the raider (see {@link #breakGate}). The
 *       break follows the game rule {@code mobGriefing}.</li>
 *   <li>A gate is open for a raider only when its open columns side by side are as wide as the raider, or as its
 *       vehicle (see {@link VillageExits.Approach#isOpen(net.minecraft.world.level.Level, double)}).</li>
 *   <li>At {@link #ARRIVAL} blocks from the heart, the raider arrives and the goal stops: the vanilla goals of the
 *       raid move the raider in the village. After the arrival, the goal starts again only when the raider is
 *       outside the palisade.</li>
 *   <li>The navigation crosses the water: the raider swims across a river.</li>
 *   <li>When the raider has a target, the goal stops and the raider attacks. When it loses the target, the goal
 *       starts again. A raider outside the palisade with a target inside the palisade marches: the path to the
 *       target goes through a gate, and the raider forces a closed gate.</li>
 *   <li>As the vanilla march, the goal recruits the raiders near the raider into the raid.</li>
 * </ul>
 */
public class MarchOnVillageGoal extends Goal {
    /** The raider arrives at this distance from the heart, in blocks. */
    public static final double ARRIVAL = 12.0;
    /** The raider recruits the raiders near it at this interval, in ticks. */
    private static final int RECRUIT_INTERVAL = 20;
    /** The radius of the recruitment, in blocks, as in the vanilla march. */
    private static final double RECRUIT_RADIUS = 16.0;
    /** The raider calculates a new path at this interval, in ticks. */
    private static final int PATH_INTERVAL = 40;
    private static final double SPEED = 1.0;
    /** A raider forces a gate at this horizontal distance from the front of the gate, in blocks. */
    private static final double FORCE_REACH = 2.0;
    /** The raider hits the gate at this interval, in ticks. */
    private static final int HIT_INTERVAL = 20;

    private final Raider raider;
    private BlockPos heart = BlockPos.ZERO;
    /** The approach of the open gate that the raider goes through, or null (see {@link RaidMarch.Leg}). */
    @Nullable
    private BlockPos entering;
    /** The closed gate that the raider forces, or null (see {@link RaidMarch.Leg}). */
    @Nullable
    private VillageExits.Approach forcing;
    /** The time that the raider forced its gate, in ticks. */
    private int forceTicks;
    /** The approach of the gate of {@link #forceTicks}, or null. */
    @Nullable
    private BlockPos forceGate;
    /** The game time of the last tick that the raider forced its gate. */
    private long lastForce;
    /** The approach of the gate where the raider is blocked, or null. */
    @Nullable
    private BlockPos blockedGate;
    /** The game time of the first hit of the raider on {@link #blockedGate}. */
    private long blockedSince;
    /** True when {@link #blockedGate} was open while the raider was in front of it. */
    private boolean blockedOpened;
    /** The fence gates that the raider breaks, or null. */
    @Nullable
    private List<BlockPos> breaking;
    /** The time that the raider broke {@link #breaking}, in ticks. */
    private int breakTicks;
    /** The last break progress sent to the clients (0 to 9), or -1. */
    private int breakProgress = -1;
    /** The time in front of the closed gate, in ticks: the raider hits the gate at {@link #HIT_INTERVAL}. */
    private int hitTicks;
    /** True after the arrival of the raider at the heart. */
    private boolean arrived;
    private int nextRecruit;
    private int nextPath;

    public MarchOnVillageGoal(Raider raider) {
        this.raider = raider;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Optional<BlockPos> found = heart();
        if (raider.hasControllingPassenger() || found.isEmpty() || !(raider.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!found.get().equals(heart)) {
            heart = found.get();
            arrived = false;
        }
        if (!marchesWithTarget(level)) {
            return false;
        }
        if (arrived && !RaidMarch.isOutside(heart, VillageRaids.approaches(level, heart), raider.blockPosition())) {
            return false;
        }
        arrived = isAtHeart();
        return !arrived;
    }

    @Override
    public boolean canContinueToUse() {
        if (raider.hasControllingPassenger() || heart().filter(heart::equals).isEmpty()
                || !(raider.level() instanceof ServerLevel level) || !marchesWithTarget(level)) {
            return false;
        }
        arrived = isAtHeart();
        return !arrived;
    }

    @Override
    public void start() {
        nextPath = 0;
        entering = null;
        forcing = null;
    }

    @Override
    public void stop() {
        raider.getNavigation().stop();
        if (raider.level() instanceof ServerLevel level) {
            clearBreak(level);
        }
        entering = null;
        forcing = null;
    }

    /**
     * True if the target of the raider lets it march: the raider has no target, or the raider is outside the palisade
     * and its target is inside. The raider then marches to a gate, and forces it when it is closed. In the other
     * cases, the vanilla goals attack the target.
     */
    private boolean marchesWithTarget(ServerLevel level) {
        LivingEntity target = raider.getTarget();
        if (target == null) {
            return true;
        }
        List<VillageExits.Approach> approaches = VillageRaids.approaches(level, heart);
        return RaidMarch.isInside(heart, approaches, target.blockPosition())
                && !RaidMarch.isInside(heart, approaches, raider.blockPosition());
    }

    @Override
    public void tick() {
        Raid raid = raider.getCurrentRaid();
        if (raid == null || !(raider.level() instanceof ServerLevel level)) {
            return;
        }
        if (raider.tickCount >= nextRecruit) {
            nextRecruit = raider.tickCount + RECRUIT_INTERVAL;
            recruitNearby(raid);
        }
        if (force(level)) {
            raider.getNavigation().stop();
            return;
        }
        if (raider.tickCount < nextPath && !raider.getNavigation().isDone()) {
            return;
        }
        nextPath = raider.tickCount + PATH_INTERVAL;
        RaidMarch.Leg leg = RaidMarch.next(heart, VillageRaids.approaches(level, heart),
                approach -> approach.isOpen(level, width()), raider.blockPosition(), entering);
        entering = leg.entering();
        forcing = leg.forcing();
        BlockPos pos = leg.target();
        if (!raider.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED)) {
            // No path at all: a step toward the target, as the vanilla march does.
            Vec3 step = DefaultRandomPos.getPosTowards(raider, 15, 4, Vec3.atBottomCenterOf(pos), Math.PI / 2.0);
            if (step != null) {
                raider.getNavigation().moveTo(step.x, step.y, step.z, SPEED);
            }
        }
    }

    /**
     * Forces the closed gate in front of the raider: the raider hits the gate, and after
     * {@link WorldConfig#RAID_GATE_FORCE_SECONDS} seconds in front of it, all the fence gates of the gate open, also
     * the stacked fence gates. A raider that comes back to the same gate in
     * {@link WorldConfig#RAID_GATE_FORCE_MEMORY_SECONDS} seconds continues from its time. Else it starts again from
     * zero. A raider forces a gate also when the game rule {@code mobGriefing} is false: the gate opens, and no block
     * breaks.
     *
     * <p>The raider is blocked at a gate from its first hit. When the gate was open while the raider was in front of
     * it, and the raider is in front of the same closed gate {@link WorldConfig#RAID_GATE_BREAK_AFTER_SECONDS} seconds
     * after its first hit, it breaks the gate (see {@link #breakGate}). A raider that is away from the gate for this
     * time is not blocked any more.
     *
     * @return true while the raider stays in front of the closed gate and forces or breaks it
     */
    private boolean force(ServerLevel level) {
        long now = level.getGameTime();
        long breakAfter = WorldConfig.RAID_GATE_BREAK_AFTER_SECONDS.get() * 20L;
        boolean inReach = forcing != null
                && horizontalDistanceSqr(RaidMarch.front(forcing)) <= FORCE_REACH * FORCE_REACH;
        if (!inReach || forcing.isOpen(level, width())) {
            if (inReach && forcing.point().equals(blockedGate)) {
                blockedOpened = true;
            }
            if (now - lastForce > WorldConfig.RAID_GATE_FORCE_MEMORY_SECONDS.get() * 20L) {
                forceTicks = 0;
                clearBreak(level);
            }
            return false;
        }
        if (!forcing.point().equals(forceGate)) {
            forceGate = forcing.point();
            forceTicks = 0;
        }
        if (!forcing.point().equals(blockedGate) || now - lastForce > breakAfter) {
            blockedGate = forcing.point();
            blockedSince = now;
            blockedOpened = false;
            clearBreak(level);
        }
        lastForce = now;
        BlockPos middle = forcing.gates().get(forcing.gates().size() / 2);
        raider.getLookControl().setLookAt(Vec3.atCenterOf(middle));
        if (hitTicks++ % HIT_INTERVAL == 0) {
            raider.swing(InteractionHand.MAIN_HAND);
            level.playSound(null, middle, SoundEvents.ZOMBIE_ATTACK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
        }
        if (blockedOpened && now - blockedSince >= breakAfter && EventHooks.canEntityGrief(level, raider)) {
            return breakGate(level);
        }
        if (++forceTicks < WorldConfig.RAID_GATE_FORCE_SECONDS.get() * 20) {
            return true;
        }
        for (BlockPos gate : forcing.blocks()) {
            BlockState state = level.getBlockState(gate);
            if (state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN)) {
                level.setBlock(gate, state.setValue(FenceGateBlock.OPEN, true), Block.UPDATE_ALL);
                level.gameEvent(raider, GameEvent.BLOCK_OPEN, gate);
            }
        }
        level.playSound(null, middle, SoundEvents.ZOMBIE_BREAK_WOODEN_DOOR, SoundSource.HOSTILE, 1.0F, 1.0F);
        forceTicks = 0;
        forceGate = null;
        blockedOpened = true;
        nextPath = 0;
        return false;
    }

    /**
     * Breaks the gate that the raider forces, one column after the other: the middle column first, then the nearest
     * columns to the middle. A column is the fence gate at the floor and the fence gates stacked on it. The blocks of a
     * column show the break progress (the cracks) during {@link WorldConfig#RAID_GATE_BREAK_SECONDS} seconds, then
     * they break with the sound of a door that a zombie breaks. As for a door that a zombie breaks, the broken fence
     * gates drop no item. The raider stays blocked after a column breaks: when the gate is not open for its width
     * (see {@link #width()}), it breaks the next column. The broken columns are open columns of the exit (see
     * {@link VillageExits#isBroken}).
     *
     * @return true while the break continues
     */
    private boolean breakGate(ServerLevel level) {
        if (breaking == null) {
            List<BlockPos> floors = forcing.gates();
            int middle = floors.size() / 2;
            breaking = IntStream.range(0, floors.size()).boxed()
                    .sorted(Comparator.comparingInt(index -> Math.abs(index - middle)))
                    .map(floors::get)
                    .filter(floor -> forcing.column(floor).stream()
                            .anyMatch(block -> level.getBlockState(block).getBlock() instanceof FenceGateBlock))
                    .findFirst()
                    .map(forcing::column)
                    .orElse(null);
            if (breaking == null) {
                return false;
            }
            breakTicks = 0;
        }
        int total = WorldConfig.RAID_GATE_BREAK_SECONDS.get() * 20;
        int progress = Math.min(9, ++breakTicks * 10 / total);
        if (progress != breakProgress) {
            breakProgress = progress;
            for (int i = 0; i < breaking.size(); i++) {
                level.destroyBlockProgress(breakerId(i), breaking.get(i), progress);
            }
        }
        if (breakTicks < total) {
            return true;
        }
        List<BlockPos> broken = breaking;
        clearBreak(level);
        for (BlockPos gate : broken) {
            if (level.getBlockState(gate).getBlock() instanceof FenceGateBlock) {
                level.destroyBlock(gate, false, raider);
            }
        }
        level.levelEvent(LevelEvent.SOUND_ZOMBIE_DOOR_CRASH, broken.get(0), 0);
        forceTicks = 0;
        forceGate = null;
        nextPath = 0;
        return false;
    }

    /**
     * The width that the raider needs to go through a gate: the width of the raider, or of its vehicle when it rides
     * one (for example a pillager on a ravager).
     */
    private double width() {
        return Math.max(raider.getBbWidth(), raider.getRootVehicle().getBbWidth());
    }

    /**
     * Stops the break: the cracks go away from the blocks, and the break time starts again from zero. A raider that
     * dies or unloads during a break does not stop it: the clients remove the cracks after some seconds with no
     * update, as for a zombie that breaks a door.
     */
    private void clearBreak(ServerLevel level) {
        if (breaking != null) {
            for (int i = 0; i < breaking.size(); i++) {
                level.destroyBlockProgress(breakerId(i), breaking.get(i), -1);
            }
        }
        breaking = null;
        breakTicks = 0;
        breakProgress = -1;
    }

    /**
     * The identifier of the break progress of one block of the column. Each block needs its own identifier: a client
     * keeps one break progress for each identifier. The identifiers are negative, so they are not the identifiers of
     * the players.
     */
    private int breakerId(int index) {
        return -1 - raider.getId() * 16 - index;
    }

    private double horizontalDistanceSqr(BlockPos pos) {
        double dx = raider.getX() - (pos.getX() + 0.5);
        double dz = raider.getZ() - (pos.getZ() + 0.5);
        return dx * dx + dz * dz;
    }

    /** True if the raider is {@link #ARRIVAL} blocks or less from the heart. */
    private boolean isAtHeart() {
        double dx = raider.blockPosition().getX() - heart.getX();
        double dz = raider.blockPosition().getZ() - heart.getZ();
        return dx * dx + dz * dz <= ARRIVAL * ARRIVAL;
    }

    private Optional<BlockPos> heart() {
        Raid raid = raider.getCurrentRaid();
        if (!raider.hasActiveRaid() || raid == null || raid.isOver()) {
            return Optional.empty();
        }
        return VillageRaids.heartOf(raid, raider.level());
    }

    /** The raiders near the raider with no raid join the raid, as in the vanilla march. */
    private void recruitNearby(Raid raid) {
        if (!raid.isActive()) {
            return;
        }
        for (Raider other : raider.level().getEntitiesOfClass(Raider.class,
                raider.getBoundingBox().inflate(RECRUIT_RADIUS),
                other -> !other.hasActiveRaid() && Raids.canJoinRaid(other, raid))) {
            raid.joinRaid(raid.getGroupsSpawned(), other, null, true);
        }
    }
}
