package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Moves the merchant of a mobile group on the route of the group, with the vanilla pathfinding over short distances.
 *
 * <ul>
 *   <li>The goal runs while the group travels. A waiting, stopped or finished group does not move.</li>
 *   <li>The target is a point of the route a little in front of the merchant.</li>
 *   <li>Near the end of the route, the merchant goes to the heart of the destination, if the heart is known. The
 *       group arrives near the heart (see {@link GroupMaterializer#isAtDestination}).</li>
 *   <li>The merchant waits when a member of its group is farther than {@link #WAIT_DISTANCE}, for
 *       {@link #MAX_WAIT_TICKS} or less. Then it continues until all members are near again.</li>
 *   <li>If the merchant does not go forward for {@link #STUCK_TICKS}, it moves directly to the target, on the
 *       ground.</li>
 * </ul>
 */
public class RouteWalkGoal extends Goal {
    /** Distance in blocks from the merchant to its target on the route. */
    private static final double LOOKAHEAD = 12.0;
    /** Game ticks between two new paths. */
    private static final int REPATH_TICKS = 40;
    /** After this time with no progress, the merchant moves directly to its target. */
    static final int STUCK_TICKS = 200;
    /** A progress below this distance in blocks is no progress. */
    private static final double MIN_PROGRESS = 1.0;
    /** The merchant goes to the destination heart at this distance or less from the end of the route, in blocks. */
    static final double FINAL_APPROACH = 48.0;
    /** The merchant waits when a member of its group is farther than this distance, in blocks. */
    static final double WAIT_DISTANCE = 10.0;
    /** The longest wait for the members, in game ticks. */
    static final int MAX_WAIT_TICKS = 400;

    private final PathfinderMob mob;
    private final double speed;
    private int repathTicks;
    private int stuckTicks;
    private int waitTicks;
    private double bestProgress;
    private boolean approach;

    public RouteWalkGoal(PathfinderMob mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    @Nullable
    private MobileGroup group() {
        MinecraftServer server = mob.level().getServer();
        String groupId = GroupMaterializer.groupId(mob);
        if (server == null || groupId == null) {
            return null;
        }
        Optional<MobileGroup> group = MobileGroupData.get(server).groups().byId(groupId);
        return group.filter(found -> found.state() == GroupState.TRAVELING).orElse(null);
    }

    @Override
    public boolean canUse() {
        return group() != null;
    }

    @Override
    public boolean canContinueToUse() {
        return group() != null;
    }

    @Override
    public void start() {
        repathTicks = 0;
        stuckTicks = 0;
        waitTicks = 0;
        bestProgress = -Double.MAX_VALUE;
        approach = false;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick() {
        return true;
    }

    @Override
    public void tick() {
        MobileGroup group = group();
        if (group == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        if (waitForMembers(level, group)) {
            return;
        }
        Route route = group.route();
        double progress = route.progressNear(mob.getX(), mob.getZ());
        Optional<BlockPos> heart = GroupMaterializer.destinationHeart(level.getServer(), group);
        double toHeart = heart.map(pos -> horizontalDistance(pos)).orElse(Double.MAX_VALUE);
        boolean nearEnd = route.length() - progress <= FINAL_APPROACH || toHeart <= FINAL_APPROACH;
        if (heart.isPresent() && nearEnd != approach) {
            approach = nearEnd;
            bestProgress = -Double.MAX_VALUE;
            stuckTicks = 0;
        }
        double targetX;
        double targetZ;
        double measure;
        if (heart.isPresent() && approach) {
            // A step to the heart. The heart can be far from the end of a route of an earlier position.
            double share = toHeart <= LOOKAHEAD ? 1.0 : LOOKAHEAD / toHeart;
            targetX = mob.getX() + (heart.get().getX() + 0.5 - mob.getX()) * share;
            targetZ = mob.getZ() + (heart.get().getZ() + 0.5 - mob.getZ()) * share;
            measure = -toHeart;
        } else {
            Route.Position next = route.positionAt(progress + LOOKAHEAD);
            targetX = next.x();
            targetZ = next.z();
            measure = progress;
        }
        if (measure > bestProgress + MIN_PROGRESS) {
            bestProgress = measure;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
        int x = (int) Math.floor(targetX);
        int z = (int) Math.floor(targetZ);
        if (!level.isLoaded(new BlockPos(x, mob.getBlockY(), z))) {
            mob.getNavigation().stop();
            return;
        }
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
        if (stuckTicks > STUCK_TICKS) {
            mob.getNavigation().stop();
            mob.moveTo(x + 0.5, y, z + 0.5, mob.getYRot(), mob.getXRot());
            stuckTicks = 0;
            return;
        }
        if (--repathTicks <= 0 || mob.getNavigation().isDone()) {
            repathTicks = REPATH_TICKS;
            mob.getNavigation().moveTo(x + 0.5, y, z + 0.5, speed);
        }
    }

    /**
     * Stops the merchant when a member of its group is far behind.
     *
     * @return true if the merchant waits
     */
    private boolean waitForMembers(ServerLevel level, MobileGroup group) {
        boolean behind = false;
        for (LivingEntity member : GroupMaterializer.memberEntities(level, group)) {
            if (member != mob && mob.distanceTo(member) > WAIT_DISTANCE) {
                behind = true;
                break;
            }
        }
        if (!behind) {
            waitTicks = 0;
            return false;
        }
        if (waitTicks >= MAX_WAIT_TICKS) {
            return false;
        }
        waitTicks++;
        mob.getNavigation().stop();
        return true;
    }

    private double horizontalDistance(BlockPos pos) {
        double dx = pos.getX() + 0.5 - mob.getX();
        double dz = pos.getZ() + 0.5 - mob.getZ();
        return Math.sqrt(dx * dx + dz * dz);
    }
}
