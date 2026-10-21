package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.village.VillageExits;
import io.github.nicolasgoyon.folkhold.world.village.VillageRaids;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.util.DefaultRandomPos;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.world.phys.Vec3;

/**
 * The march of a raider on a faction village (see {@link VillageRaids}). Near a faction village, it replaces the
 * vanilla march of the raid, which goes to random positions toward the center of the raid and stops at a river.
 *
 * <ul>
 *   <li>Outside the palisade, the raider walks to the approach of the nearest gate.</li>
 *   <li>At the approach, the raider goes through the gate when the gate is open. At a closed gate, it waits.</li>
 *   <li>Inside the palisade, the raider walks to the heart. At {@link #ARRIVAL} blocks from the heart, the goal
 *       stops: the vanilla goals of the raid move the raider in the village.</li>
 *   <li>The navigation crosses the water: the raider swims across a river.</li>
 *   <li>When the raider has a target, the goal stops and the raider attacks. When it loses the target, the goal
 *       starts again.</li>
 *   <li>As the vanilla march, the goal recruits the raiders near the raider into the raid.</li>
 * </ul>
 */
public class MarchOnVillageGoal extends Goal {
    /** The goal stops at this distance from the heart, in blocks. */
    public static final double ARRIVAL = 12.0;
    /** The raider is at an approach at this distance from it, in blocks. */
    private static final double AT_APPROACH = 3.0;
    /** The raider recruits the raiders near it at this interval, in ticks. */
    private static final int RECRUIT_INTERVAL = 20;
    /** The radius of the recruitment, in blocks, as in the vanilla march. */
    private static final double RECRUIT_RADIUS = 16.0;
    /** The raider calculates a new path at this interval, in ticks. */
    private static final int PATH_INTERVAL = 40;
    private static final double SPEED = 1.0;

    private final Raider raider;
    private BlockPos heart = BlockPos.ZERO;
    private int nextRecruit;
    private int nextPath;

    public MarchOnVillageGoal(Raider raider) {
        this.raider = raider;
        setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse() {
        Optional<BlockPos> found = heart();
        if (raider.getTarget() != null || raider.hasControllingPassenger() || found.isEmpty()
                || isArrived(found.get())) {
            return false;
        }
        heart = found.get();
        return true;
    }

    @Override
    public boolean canContinueToUse() {
        return raider.getTarget() == null && !raider.hasControllingPassenger()
                && heart().filter(heart::equals).isPresent() && !isArrived(heart);
    }

    @Override
    public void start() {
        nextPath = 0;
    }

    @Override
    public void stop() {
        raider.getNavigation().stop();
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
        if (raider.tickCount < nextPath && !raider.getNavigation().isDone()) {
            return;
        }
        nextPath = raider.tickCount + PATH_INTERVAL;
        Optional<BlockPos> target = target(level);
        if (target.isEmpty()) {
            raider.getNavigation().stop();
            return;
        }
        BlockPos pos = target.get();
        if (!raider.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED)) {
            // No path at all: a step toward the target, as the vanilla march does.
            Vec3 step = DefaultRandomPos.getPosTowards(raider, 15, 4, Vec3.atBottomCenterOf(pos), Math.PI / 2.0);
            if (step != null) {
                raider.getNavigation().moveTo(step.x, step.y, step.z, SPEED);
            }
        }
    }

    /**
     * The next point of the march: the approach of the nearest gate while the raider is outside the palisade, the
     * heart when the raider is inside or at an open gate. Empty at a closed gate: the raider waits.
     */
    private Optional<BlockPos> target(ServerLevel level) {
        List<VillageExits.Approach> approaches = VillageRaids.approaches(level, heart);
        BlockPos pos = raider.blockPosition();
        Optional<VillageExits.Approach> nearest = approaches.stream()
                .min(Comparator.comparingDouble(approach -> approach.point().distSqr(pos)));
        if (nearest.isEmpty() || isInside(approaches, pos)) {
            return Optional.of(heart);
        }
        VillageExits.Approach approach = nearest.get();
        if (!approach.point().closerThan(pos, AT_APPROACH)) {
            return Optional.of(approach.point());
        }
        return approach.isOpen(level) ? Optional.of(heart) : Optional.empty();
    }

    /** True if a position is nearer to the heart than the gates of the palisade: inside the palisade. */
    private boolean isInside(List<VillageExits.Approach> approaches, BlockPos pos) {
        double wall = Double.MAX_VALUE;
        for (VillageExits.Approach approach : approaches) {
            for (BlockPos gate : approach.gates()) {
                wall = Math.min(wall, horizontalDistance(gate, heart));
            }
        }
        return horizontalDistance(pos, heart) < wall - 1.0;
    }

    private boolean isArrived(BlockPos target) {
        return horizontalDistance(raider.blockPosition(), target) <= ARRIVAL;
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

    private static double horizontalDistance(BlockPos a, BlockPos b) {
        double dx = a.getX() - b.getX();
        double dz = a.getZ() - b.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
