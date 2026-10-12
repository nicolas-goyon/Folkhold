package io.github.nicolasgoyon.folkhold.world.entity.ai;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * Ground navigation of the faction units. A closed fence gate is a closed wooden door for the path finder: the
 * path can go through it, and {@link UseFenceGateGoal} opens it. The units also open wooden doors.
 */
public class UnitNavigation extends GroundPathNavigation {

    public UnitNavigation(Mob mob, Level level) {
        super(mob, level);
        setCanOpenDoors(true);
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
