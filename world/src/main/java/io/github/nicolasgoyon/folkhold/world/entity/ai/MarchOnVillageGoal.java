package io.github.nicolasgoyon.folkhold.world.entity.ai;

import io.github.nicolasgoyon.folkhold.world.village.RaidMarch;
import io.github.nicolasgoyon.folkhold.world.village.VillageRaids;
import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
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
 *   <li>The route comes from {@link RaidMarch}: the raider walks to the approach of the nearest gate, then through
 *       an open gate to the heart. At a closed gate, it waits.</li>
 *   <li>At {@link #ARRIVAL} blocks from the heart, the raider arrives and the goal stops: the vanilla goals of the
 *       raid move the raider in the village. A raider that stops at this distance for another cause, for example a
 *       target, also arrives. After the arrival, the goal starts again only when the raider is outside the
 *       palisade.</li>
 *   <li>The navigation crosses the water: the raider swims across a river.</li>
 *   <li>When the raider has a target, the goal stops and the raider attacks. When it loses the target, the goal
 *       starts again.</li>
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

    private final Raider raider;
    private BlockPos heart = BlockPos.ZERO;
    /** The approach of the open gate that the raider goes through, or null (see {@link RaidMarch.Leg}). */
    @Nullable
    private BlockPos entering;
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
        if (raider.getTarget() != null || raider.hasControllingPassenger() || found.isEmpty()
                || !(raider.level() instanceof ServerLevel level)) {
            return false;
        }
        if (!found.get().equals(heart)) {
            heart = found.get();
            arrived = false;
        }
        if (arrived && !RaidMarch.isOutside(heart, VillageRaids.approaches(level, heart), raider.blockPosition())) {
            return false;
        }
        arrived = isAtHeart();
        return !arrived;
    }

    @Override
    public boolean canContinueToUse() {
        if (raider.getTarget() != null || raider.hasControllingPassenger()
                || heart().filter(heart::equals).isEmpty()) {
            return false;
        }
        arrived = isAtHeart();
        return !arrived;
    }

    @Override
    public void start() {
        nextPath = 0;
        entering = null;
    }

    @Override
    public void stop() {
        raider.getNavigation().stop();
        entering = null;
        if (isAtHeart()) {
            arrived = true;
        }
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
        RaidMarch.Leg leg = RaidMarch.next(heart, VillageRaids.approaches(level, heart),
                approach -> approach.isOpen(level), raider.blockPosition(), entering);
        entering = leg.entering();
        if (leg.target().isEmpty()) {
            raider.getNavigation().stop();
            return;
        }
        BlockPos pos = leg.target().get();
        if (!raider.getNavigation().moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, SPEED)) {
            // No path at all: a step toward the target, as the vanilla march does.
            Vec3 step = DefaultRandomPos.getPosTowards(raider, 15, 4, Vec3.atBottomCenterOf(pos), Math.PI / 2.0);
            if (step != null) {
                raider.getNavigation().moveTo(step.x, step.y, step.z, SPEED);
            }
        }
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
