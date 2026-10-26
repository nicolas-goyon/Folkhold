package io.github.nicolasgoyon.folkhold.world.construction;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * The navigation of a builder on a construction site: the path finder of a villager, with the walk of the plan (see
 * {@code WalkSearch} in the core module).
 *
 * <ul>
 *   <li>A builder does not walk through the scaffolding. The path finder of a villager walks through it, but a
 *       villager in scaffolding climbs it when it touches a block. Thus a scaffolding block stops the path finder of a
 *       builder.</li>
 *   <li>The blocks of the column that the builder climbs do not stop it: the builder walks into the bottom of the
 *       column (see {@link #climb}).</li>
 *   <li>A villager gets this navigation when it works on a site, and gets its own navigation back when it stops (see
 *       {@link #equip} and {@link #restore}).</li>
 * </ul>
 */
public final class BuilderNavigation extends GroundPathNavigation {
    private final PathNavigation original;

    private BuilderNavigation(Villager villager, PathNavigation original) {
        super(villager, villager.level());
        this.original = original;
        setCanOpenDoors(true);
        setCanFloat(original.canFloat());
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes) {
        this.nodeEvaluator = new Evaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /** Gives the navigation of a builder to a villager, if it does not have it. */
    public static void equip(Villager villager) {
        PathNavigation current = villager.navigation;
        if (!(current instanceof BuilderNavigation)) {
            current.stop();
            villager.navigation = new BuilderNavigation(villager, current);
        }
    }

    /** Gives its own navigation back to a villager. */
    public static void restore(Villager villager) {
        if (villager.navigation instanceof BuilderNavigation navigation) {
            navigation.stop();
            villager.navigation = navigation.original;
        }
    }

    /**
     * Sets the column that the builder climbs. The blocks of this column do not stop the builder.
     *
     * @param villager the villager of the builder
     * @param base     the bottom block of the column, or null when the builder does not climb
     */
    public static void climb(Villager villager, @Nullable BlockPos base) {
        if (villager.navigation instanceof BuilderNavigation navigation
                && navigation.nodeEvaluator instanceof Evaluator evaluator) {
            evaluator.column = base;
        }
    }

    /** The path types of a builder: the scaffolding is blocked, except the column that the builder climbs. */
    private static final class Evaluator extends WalkNodeEvaluator {
        @Nullable
        private BlockPos column;

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (!isInColumn(x, y, z) && context.getBlockState(new BlockPos(x, y, z)).is(Blocks.SCAFFOLDING)) {
                return PathType.BLOCKED;
            }
            return super.getPathType(context, x, y, z);
        }

        private boolean isInColumn(int x, int y, int z) {
            BlockPos base = column;
            return base != null && x == base.getX() && z == base.getZ() && y >= base.getY();
        }
    }
}
