package io.github.nicolasgoyon.folkhold.world.construction;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.pathfinder.Node;
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
 *   <li>A builder does not walk on the top of a new wall of its site (see {@link BuilderSite#isWallTop}). The path
 *       finder of a villager jumps 1 block up on the top of a wall, walks on it and goes down on the other side. For
 *       the path finder of a builder, a position above the top of a new wall costs {@link #WALL_TOP_COST} more blocks
 *       of walk (see {@link #site}). Thus the builder goes around the wall, through the door or the open side of the
 *       site.</li>
 *   <li>A villager gets this navigation when it works on a site, and gets its own navigation back when it stops (see
 *       {@link #equip} and {@link #restore}).</li>
 * </ul>
 */
public final class BuilderNavigation extends GroundPathNavigation {
    /**
     * The cost of a position above the top of a new wall, in blocks of walk. It is more than the walk around a
     * cottage of the village. A way over a wall stays possible when no other way is near.
     */
    static final float WALL_TOP_COST = 64.0F;

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

    /**
     * Sets the site of the builder: the builder does not walk on the top of a new wall of this site.
     *
     * @param villager the villager of the builder
     * @param site     the site, or null for no site
     */
    static void site(Villager villager, @Nullable BuilderSite site) {
        if (villager.navigation instanceof BuilderNavigation navigation
                && navigation.nodeEvaluator instanceof Evaluator evaluator) {
            evaluator.site = site;
        }
    }

    /**
     * The path types of a builder: the scaffolding is blocked, except the column that the builder climbs. A position
     * above the top of a new wall of the site has a high cost.
     */
    private static final class Evaluator extends WalkNodeEvaluator {
        @Nullable
        private BlockPos column;
        @Nullable
        private BuilderSite site;

        @Override
        public PathType getPathType(PathfindingContext context, int x, int y, int z) {
            if (!isInColumn(x, y, z) && context.getBlockState(new BlockPos(x, y, z)).is(Blocks.SCAFFOLDING)) {
                return PathType.BLOCKED;
            }
            return super.getPathType(context, x, y, z);
        }

        @Override
        protected Node findAcceptedNode(int x, int y, int z, int verticalDeltaLimit, double nodeFloorLevel,
                Direction direction, PathType pathType) {
            Node node = super.findAcceptedNode(x, y, z, verticalDeltaLimit, nodeFloorLevel, direction, pathType);
            BuilderSite current = site;
            if (node != null && node.costMalus >= 0.0F && current != null
                    && current.isWallTop(currentContext.level(), new BlockPos(node.x, node.y - 1, node.z))) {
                // The path finder keeps one node for each position: the cost does not add up.
                node.costMalus = Math.max(node.costMalus, WALL_TOP_COST);
            }
            return node;
        }

        private boolean isInColumn(int x, int y, int z) {
            BlockPos base = column;
            return base != null && x == base.getX() && z == base.getZ() && y >= base.getY();
        }
    }
}
