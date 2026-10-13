package io.github.nicolasgoyon.folkhold.world.entity.ai;

import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation of the faction units.
 *
 * <ul>
 *   <li>A closed fence gate is a closed wooden door for the path finder: the path can go through it, and
 *       {@link UsePassageGoal} opens it. The same goal opens and closes the wooden doors.</li>
 *   <li>The path finder searches in a radius of at least {@link #MIN_PATH_RANGE} blocks, with
 *       {@link #NODES_MULTIPLIER} times more nodes than a vanilla mob. A unit can go around a wall or through a
 *       gate of the village to reach a target behind the wall.</li>
 * </ul>
 */
public class UnitNavigation extends GroundPathNavigation {
    /** Minimum search radius of the path finder, in blocks. The village is 39 blocks wide. */
    public static final float MIN_PATH_RANGE = 64.0F;
    /** Multiplier of the maximum number of nodes that the path finder visits. */
    public static final float NODES_MULTIPLIER = 4.0F;

    public UnitNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
        setMaxVisitedNodesMultiplier(NODES_MULTIPLIER);
    }

    @Override
    @Nullable
    protected Path createPath(Set<BlockPos> targets, int regionOffset, boolean offsetUpward, int accuracy,
            float followRange) {
        return super.createPath(targets, regionOffset, offsetUpward, accuracy, Math.max(followRange, MIN_PATH_RANGE));
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        nodeEvaluator = new GateNodeEvaluator();
        nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(nodeEvaluator, maxVisitedNodes);
    }

    /** True if the block at the position is a closed fence gate. */
    public static boolean isClosedGate(BlockState state) {
        return state.getBlock() instanceof FenceGateBlock && !state.getValue(FenceGateBlock.OPEN);
    }

    /** Node evaluator that gives the path type of a closed wooden door to a closed fence gate. */
    private static final class GateNodeEvaluator extends WalkNodeEvaluator {
        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (isClosedGate(context.getBlockState(new BlockPos(x, y, z)))) {
                return PathType.DOOR_WOOD_CLOSED;
            }
            return super.getPathType(context, x, y, z);
        }
    }
}
