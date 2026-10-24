package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.group.Route;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.group.RouteWalker;
import io.github.nicolasgoyon.folkhold.world.group.RouteWalker.Step;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FenceGateBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the walk on the blocks along a route ({@link RouteWalker}): the line of the route in the journal
 * follows it. The floor of the arena is at y = 0, so the feet are at y = 1 on the floor.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class RouteWalkerGameTests {
    private RouteWalkerGameTests() {
    }

    /** A route from a relative position to another one, in absolute coordinates. */
    private static Route route(GameTestHelper helper, int fromX, int fromZ, int toX, int toZ) {
        BlockPos from = helper.absolutePos(new BlockPos(fromX, 1, fromZ));
        BlockPos to = helper.absolutePos(new BlockPos(toX, 1, toZ));
        return new Route(List.of(new Route.Point(from.getX(), from.getZ()), new Route.Point(to.getX(), to.getZ())));
    }

    /** The walk along a route from its first point, with the feet at a relative height. */
    private static List<Step> walk(GameTestHelper helper, Route route, int feetY) {
        RouteWalker walker = new RouteWalker(helper.getLevel());
        Route.Point first = route.points().get(0);
        int y = helper.absolutePos(new BlockPos(0, feetY, 0)).getY();
        Step start = new Step(first.x(), y, first.z(), walker.floorAt(first.x(), y, first.z()));
        helper.assertFalse(Double.isNaN(start.floor()), "The start of the walk must be standable");
        return walker.walk(start, route);
    }

    /** Checks that each position is standable and each move goes to a block around, with a valid height. */
    private static void assertValid(GameTestHelper helper, List<Step> steps, Route route) {
        RouteWalker walker = new RouteWalker(helper.getLevel());
        helper.assertFalse(steps.isEmpty(), "The walk must find a path");
        Route.Point end = route.points().get(route.points().size() - 1);
        Step last = steps.get(steps.size() - 1);
        helper.assertTrue(Math.hypot(last.x() + 0.5 - end.x(), last.z() + 0.5 - end.z()) <= 1.5,
                "The walk must end near the end of the route: " + last);
        for (int i = 0; i < steps.size(); i++) {
            Step step = steps.get(i);
            helper.assertTrue(walker.floorAt(step.x(), step.y(), step.z()) == step.floor(),
                    "Each position must be standable: " + step);
            if (i > 0) {
                Step before = steps.get(i - 1);
                int dy = step.y() - before.y();
                helper.assertTrue(Math.abs(step.x() - before.x()) <= 1 && Math.abs(step.z() - before.z()) <= 1
                        && dy <= 1 && dy >= -RouteWalker.MAX_DROP, "Each move must be a valid step: " + before + " -> "
                        + step);
            }
        }
    }

    /** A wall with a gate: the walk goes through the gate, not through the wall. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "route_walker_gate", skyAccess = true)
    public static void walkGoesThroughTheGate(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int z = 0; z < 16; z++) {
            for (int y = 1; y <= 3; y++) {
                helper.setBlock(new BlockPos(8, y, z), Blocks.STONE_BRICKS);
            }
        }
        helper.setBlock(new BlockPos(8, 1, 10), Blocks.SPRUCE_FENCE_GATE.defaultBlockState()
                .setValue(FenceGateBlock.FACING, Direction.EAST));
        helper.setBlock(new BlockPos(8, 2, 10), Blocks.AIR);
        Route route = route(helper, 2, 4, 14, 4);

        List<Step> steps = walk(helper, route, 1);

        assertValid(helper, steps, route);
        BlockPos gate = helper.absolutePos(new BlockPos(8, 1, 10));
        for (Step step : steps) {
            if (step.x() == gate.getX()) {
                helper.assertTrue(step.z() == gate.getZ() && step.y() == gate.getY(),
                        "The walk must cross the wall at the gate: " + step);
            }
        }
        helper.succeed();
    }

    /** A block mass too high to climb, with a tunnel: the walk goes through the tunnel, under the ceiling. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "route_walker_tunnel", skyAccess = true)
    public static void walkGoesUnderTheCeiling(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int x = 6; x <= 10; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y <= 4; y++) {
                    boolean tunnel = z == 4 && y <= 2;
                    helper.setBlock(new BlockPos(x, y, z), tunnel ? Blocks.AIR : Blocks.STONE);
                }
            }
        }
        Route route = route(helper, 2, 4, 14, 4);

        List<Step> steps = walk(helper, route, 1);

        assertValid(helper, steps, route);
        int feet = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        for (Step step : steps) {
            helper.assertTrue(step.y() == feet, "The walk must stay in the tunnel, not on the mass: " + step);
        }
        helper.succeed();
    }

    /** Steps of one block up, then down: the walk climbs them and stays on the blocks. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "route_walker_steps", skyAccess = true)
    public static void walkClimbsTheSteps(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        int[] heights = {0, 0, 0, 0, 0, 1, 2, 3, 3, 2, 1, 0, 0, 0, 0, 0};
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                for (int y = 1; y <= heights[x]; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.STONE);
                }
            }
        }
        Route route = route(helper, 2, 8, 14, 8);

        List<Step> steps = walk(helper, route, 1);

        assertValid(helper, steps, route);
        int feet = helper.absolutePos(new BlockPos(0, 1, 0)).getY();
        for (Step step : steps) {
            int x = step.x() - helper.absolutePos(BlockPos.ZERO).getX();
            helper.assertTrue(step.y() == feet + heights[x], "The walk must stand on the steps: " + step);
        }
        helper.succeed();
    }

    /** Deep water: the walk swims on the surface, not on the bottom. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "route_walker_water", skyAccess = true)
    public static void walkSwimsOnTheSurface(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                boolean pool = x >= 5 && x <= 10;
                for (int y = 1; y <= 2; y++) {
                    helper.setBlock(new BlockPos(x, y, z), pool ? Blocks.WATER : Blocks.STONE);
                }
            }
        }
        Route route = route(helper, 2, 8, 14, 8);

        List<Step> steps = walk(helper, route, 3);

        assertValid(helper, steps, route);
        int surface = helper.absolutePos(new BlockPos(0, 3, 0)).getY();
        for (Step step : steps) {
            helper.assertTrue(step.y() == surface && step.floor() > surface - 1.0,
                    "The walk must stay on the surface of the water: " + step);
        }
        helper.succeed();
    }
}
