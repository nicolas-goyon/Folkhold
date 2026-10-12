package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the paths of the faction units. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PathGameTests {
    /** Height of the test walls: a unit cannot jump over them. The arena is 4 blocks high. */
    private static final int WALL_TOP = 3;

    private PathGameTests() {
    }

    /** A pig that does not move and does not die. */
    private static Pig target(GameTestHelper helper, BlockPos pos) {
        Pig pig = helper.spawn(EntityType.PIG, pos);
        pig.setNoAi(true);
        AttributeInstance health = pig.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(1000.0);
        }
        pig.setHealth(1000.0F);
        return pig;
    }

    private static void wall(GameTestHelper helper, int fromX, int toX, int z) {
        for (int x = fromX; x <= toX; x++) {
            for (int y = 1; y <= WALL_TOP; y++) {
                helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
            }
        }
    }

    /** A guard goes around a wall to reach a target behind it. */
    @GameTest(template = "arena", timeoutTicks = 300, batch = "path_around_wall")
    public static void guardGoesAroundAWall(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        // Wall from the west side to x = 12. The opening is at x = 13 to 15.
        wall(helper, 0, 12, 8);
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(2, 1, 3));
        Pig pig = target(helper, new BlockPos(2, 1, 13));
        guard.setTarget(pig);

        TestSupport.succeedWhenAndRemove(helper, () -> helper.assertTrue(guard.distanceToSqr(pig) < 6.25,
                "The guard must reach the target behind the wall"), guard, pig);
    }

    /** A guard stops the chase of a target that it cannot reach and cannot see. */
    @GameTest(template = "arena", timeoutTicks = 400, batch = "path_unreachable")
    public static void guardStopsTheChaseOfAnUnreachableTarget(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        // A closed box around the target: walls on the four sides, a roof under the top of the arena.
        for (int x = 9; x <= 13; x++) {
            for (int z = 9; z <= 13; z++) {
                boolean side = x == 9 || x == 13 || z == 9 || z == 13;
                for (int y = 1; y <= WALL_TOP; y++) {
                    if (side || y == WALL_TOP) {
                        helper.setBlock(new BlockPos(x, y, z), Blocks.STONE_BRICKS);
                    }
                }
            }
        }
        GuardEntity guard = TestSupport.spawnGuard(helper, new BlockPos(2, 1, 2));
        Pig pig = target(helper, new BlockPos(11, 1, 11));
        guard.setTarget(pig);

        TestSupport.succeedWhenAndRemove(helper, () -> helper.assertTrue(guard.getTarget() == null,
                "The guard must stop the chase"), guard, pig);
    }
}
