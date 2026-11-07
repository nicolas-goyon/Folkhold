package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.structure.GroundFillPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the fill of the gaps under the surface near a tower or a gate of a village
 * ({@link GroundFillPiece}). The tests build columns of terrain with gaps of air on the floor of the arena.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GroundFillGameTests {
    /** The Y of the floor of stone under the gaps. */
    private static final int FLOOR = 1;
    /** The number of air blocks of a gap: as the gap next to a tower of a generated village. */
    private static final int GAP = 5;
    /** The Y of the surface above a gap of {@link #GAP} blocks. */
    private static final int SURFACE = FLOOR + GAP + 1;
    /** The X of the 4 columns of grass over a gap. */
    private static final int[] GRASS_X = {2, 3, 4, 5};
    private static final int GRASS_Z = 2;
    /** The column of sand over a gap. */
    private static final BlockPos SAND = new BlockPos(2, 0, 4);
    /** The column of grass over a cave deeper than the largest gap. */
    private static final BlockPos CAVE = new BlockPos(4, 0, 4);
    /** The column of planks of another piece over air. */
    private static final BlockPos PLANKS = new BlockPos(6, 0, 4);
    /** The column of grass over water. */
    private static final BlockPos WATER = new BlockPos(8, 0, 4);

    private GroundFillGameTests() {
    }

    /**
     * Near a tower, 4 columns of grass over 5 blocks of air and a column of sand over air get the ground block under
     * the surface: no air stays under the surface. A cave deeper than the largest gap, the air under the planks of
     * another piece and the water stay.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_ground_fill")
    public static void groundFillLeavesNoGapUnderTheSurface(GameTestHelper helper) {
        // The columns go above the arena: its ceiling of barriers goes.
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
                for (int y = FLOOR + 1; y <= SURFACE + 1; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        for (int x : GRASS_X) {
            helper.setBlock(new BlockPos(x, SURFACE, GRASS_Z), Blocks.GRASS_BLOCK);
        }
        helper.setBlock(SAND.atY(SURFACE), Blocks.SAND);
        int caveTop = FLOOR + GroundFillPiece.MAX_GAP + 2;
        helper.setBlock(CAVE.atY(caveTop), Blocks.GRASS_BLOCK);
        helper.setBlock(PLANKS.atY(SURFACE), Blocks.SPRUCE_PLANKS);
        helper.setBlock(WATER.atY(SURFACE - 1), Blocks.DIRT);
        helper.setBlock(WATER.atY(SURFACE), Blocks.WATER);
        // The tower: a box above the surface, with the columns of the test in its band.
        BlockPos tower = helper.absolutePos(new BlockPos(6, SURFACE, 6));
        BoundingBox box = new BoundingBox(tower.getX(), tower.getY(), tower.getZ(), tower.getX() + 1,
                tower.getY() + GroundFillPiece.MAX_GAP + 4, tower.getZ() + 1);

        place(helper, new GroundFillPiece(box));

        for (int x : GRASS_X) {
            helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, SURFACE, GRASS_Z));
            for (int y = FLOOR + 1; y < SURFACE; y++) {
                helper.assertBlockPresent(Blocks.DIRT, new BlockPos(x, y, GRASS_Z));
            }
        }
        for (int y = FLOOR + 1; y < SURFACE; y++) {
            helper.assertBlockPresent(Blocks.SAND, SAND.atY(y));
            helper.assertBlockPresent(Blocks.AIR, PLANKS.atY(y));
        }
        for (int y = FLOOR + 1; y < caveTop; y++) {
            helper.assertBlockPresent(Blocks.AIR, CAVE.atY(y));
        }
        helper.assertBlockPresent(Blocks.WATER, WATER.atY(SURFACE));
        helper.assertBlockPresent(Blocks.AIR, WATER.atY(SURFACE - 2));
        helper.succeed();
    }

    /** Places the blocks of a piece in the level, in all its box. */
    private static void place(GameTestHelper helper, GroundFillPiece piece) {
        ServerLevel level = helper.getLevel();
        BlockPos pivot = piece.getBoundingBox().getCenter();
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                piece.getBoundingBox(), new ChunkPos(pivot), pivot);
    }
}
