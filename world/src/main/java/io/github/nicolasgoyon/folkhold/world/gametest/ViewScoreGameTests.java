package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.VillageLayout;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the view score of the archer posts. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ViewScoreGameTests {
    private static final BlockPos STAND = new BlockPos(8, 1, 8);

    private ViewScoreGameTests() {
    }

    /** A post in a closed ring of walls (a tower in the rock) has no view. A post in a block is not valid. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "view_score")
    public static void closedPostHasNoView(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) == 2 || Math.abs(dz) == 2) {
                    for (int y = 1; y <= 3; y++) {
                        helper.setBlock(STAND.offset(dx, y - 1, dz), Blocks.STONE);
                    }
                }
            }
        }
        int closed = VillageLayout.viewScore(helper.getLevel(), helper.absolutePos(STAND));
        helper.setBlock(STAND, Blocks.STONE);
        int inBlock = VillageLayout.viewScore(helper.getLevel(), helper.absolutePos(STAND));

        helper.assertTrue(closed < VillageLayout.MIN_VIEW, "A closed post must not be valid, score " + closed);
        helper.assertTrue(inBlock == 0, "A post in a block must have a score of 0, not " + inBlock);
        helper.succeed();
    }
}
