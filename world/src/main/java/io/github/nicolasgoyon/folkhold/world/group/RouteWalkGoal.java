package io.github.nicolasgoyon.folkhold.world.group;

import io.github.nicolasgoyon.folkhold.core.sim.group.GroupState;
import io.github.nicolasgoyon.folkhold.core.sim.group.MobileGroup;
import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import java.util.EnumSet;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Moves the merchant of a mobile group on the route of the group, with the vanilla pathfinding over short distances.
 *
 * <ul>
 *   <li>The goal runs while the group travels. A waiting, stopped or finished group does not move.</li>
 *   <li>The target is a point of the route a little in front of the merchant.</li>
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

    private final PathfinderMob mob;
    private final double speed;
    private int repathTicks;
    private int stuckTicks;
    private double bestProgress;

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
        bestProgress = -1.0;
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
        if (group == null) {
            return;
        }
        Route route = group.route();
        double progress = route.progressNear(mob.getX(), mob.getZ());
        if (progress > bestProgress + MIN_PROGRESS) {
            bestProgress = progress;
            stuckTicks = 0;
        } else {
            stuckTicks++;
        }
        Route.Position next = route.positionAt(progress + LOOKAHEAD);
        int x = (int) Math.floor(next.x());
        int z = (int) Math.floor(next.z());
        if (!mob.level().isLoaded(new BlockPos(x, mob.getBlockY(), z))) {
            mob.getNavigation().stop();
            return;
        }
        int y = mob.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
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
}
