package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.structure.PierBlocks;
import io.github.nicolasgoyon.folkhold.world.village.structure.PierPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the pier of a water gate ({@link PierPiece}). The tests build a pool on a floor of dirt, and place
 * a pier over it.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class PierGameTests {
    private static final int LENGTH = 8;
    private static final int DECK_Y = 3;
    private static final PierBlocks BLOCKS = new PierBlocks(Blocks.SPRUCE_PLANKS.defaultBlockState(),
            Blocks.SPRUCE_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y),
            Blocks.SPRUCE_FENCE.defaultBlockState());

    private PierGameTests() {
    }

    /**
     * The pier has a deck of 3 blocks over the water. On the shore, the deck goes through the sand. The posts go under
     * the two sides of the deck every 3 rows and under the last row, down to the floor of the pool. The rails are on
     * the two sides, except on the first row.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_pier")
    public static void pierHasADeckPostsAndRails(GameTestHelper helper) {
        for (int x = 0; x < 5; x++) {
            for (int z = 0; z < LENGTH + 2; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, 1, z), Blocks.WATER);
                helper.setBlock(new BlockPos(x, 2, z), Blocks.WATER);
            }
        }
        // A shore of sand on the first 2 rows, at the level of the deck and above it.
        for (int x = 1; x <= 3; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, DECK_Y, z), Blocks.SAND);
                helper.setBlock(new BlockPos(x, DECK_Y + 1, z), Blocks.SAND);
            }
        }
        ServerLevel level = helper.getLevel();
        BlockPos start = helper.absolutePos(new BlockPos(2, DECK_Y, 1));
        PierPiece pier = new PierPiece(start.getX(), start.getZ(), 0, 1, LENGTH, start.getY(), BLOCKS);

        pier.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                pier.getBoundingBox(), new ChunkPos(start), start);

        for (int row = 0; row < LENGTH; row++) {
            boolean postRow = row % PierPiece.POST_SPACING == 0 || row == LENGTH - 1;
            for (int side = -1; side <= 1; side++) {
                BlockPos deck = new BlockPos(2 + side, DECK_Y, 1 + row);
                helper.assertBlockPresent(Blocks.SPRUCE_PLANKS, deck);
                if (side == 0) {
                    continue;
                }
                if (row > 0) {
                    helper.assertBlockPresent(Blocks.SPRUCE_FENCE, deck.above());
                } else {
                    helper.assertBlockNotPresent(Blocks.SPRUCE_FENCE, deck.above());
                }
                for (int y = 1; y < DECK_Y; y++) {
                    BlockPos post = new BlockPos(2 + side, y, 1 + row);
                    if (postRow) {
                        helper.assertBlockPresent(Blocks.SPRUCE_LOG, post);
                    } else {
                        helper.assertBlockPresent(Blocks.WATER, post);
                    }
                }
            }
        }
        helper.assertBlockPresent(Blocks.WATER, new BlockPos(2, 2, 1));
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(1, 0, 1));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, DECK_Y + 1, 1));
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(2, DECK_Y + 1, 2));
        helper.succeed();
    }
}
