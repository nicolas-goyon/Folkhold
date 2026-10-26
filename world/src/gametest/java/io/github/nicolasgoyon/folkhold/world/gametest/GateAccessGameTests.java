package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.core.sim.village.PathRamp;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.structure.TerrainPiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.VillagePathPiece;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the access to the gates of a village on a slope: the flat terrain of a gate clears its space
 * ({@link TerrainPiece}), and the path from a gate to the streets is a ramp ({@link VillagePathPiece}). The tests build
 * a terrain of dirt on the floor of the arena.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GateAccessGameTests {
    /** The Y of the top block of the flat terrain. */
    private static final int GROUND = 1;
    /** The X of the middle of the path. */
    private static final int PATH_X = 7;
    /** The terrain goes up by this number of blocks at the row {@link #CLIFF_ROW} of the path. */
    private static final int CLIFF = 4;
    private static final int CLIFF_ROW = 4;
    private static final int LENGTH = 10;

    private GateAccessGameTests() {
    }

    /**
     * The flat terrain of a gate removes the dirt and the plants above its ground, and fills a hole at its ground. The
     * blocks of the other pieces stay.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_gate_access")
    public static void gateTerrainClearsItsSpace(GameTestHelper helper) {
        for (int x = 1; x <= 4; x++) {
            for (int z = 1; z <= 4; z++) {
                helper.setBlock(new BlockPos(x, GROUND, z), Blocks.DIRT);
            }
        }
        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.setBlock(new BlockPos(x, GROUND + 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, GROUND + 2, z), Blocks.GRASS_BLOCK);
            }
        }
        helper.setBlock(new BlockPos(4, GROUND + 1, 4), Blocks.POPPY);
        helper.setBlock(new BlockPos(3, GROUND + 1, 1), Blocks.SPRUCE_PLANKS);
        helper.setBlock(new BlockPos(4, GROUND, 1), Blocks.AIR);
        BlockPos min = helper.absolutePos(new BlockPos(1, GROUND, 1));
        BlockPos max = helper.absolutePos(new BlockPos(4, GROUND, 4));

        place(helper, new TerrainPiece(min.getX(), min.getZ(), max.getX(), max.getZ(), min.getY(), true));

        for (int x = 1; x <= 2; x++) {
            for (int z = 1; z <= 2; z++) {
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, GROUND + 1, z));
                helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, GROUND + 2, z));
            }
        }
        helper.assertBlockPresent(Blocks.AIR, new BlockPos(4, GROUND + 1, 4));
        helper.assertBlockPresent(Blocks.SPRUCE_PLANKS, new BlockPos(3, GROUND + 1, 1));
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(4, GROUND, 1));
        helper.succeed();
    }

    /**
     * A path from a gate at the level of the flat terrain to a terrain 4 blocks higher: the path goes up by 1 block at
     * most from a row to the next one. It cuts through the high terrain, and a hole under a row gets dirt. Each row has
     * a dirt path with free space above it. The blocks of the other pieces stay.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_gate_access")
    public static void gatePathIsARamp(GameTestHelper helper) {
        int[] heights = new int[LENGTH];
        for (int row = 0; row < LENGTH; row++) {
            heights[row] = row < CLIFF_ROW ? GROUND : GROUND + CLIFF;
            for (int x = PATH_X - 1; x <= PATH_X + 1; x++) {
                for (int y = GROUND; y <= heights[row]; y++) {
                    helper.setBlock(new BlockPos(x, y, 1 + row), Blocks.DIRT);
                }
            }
        }
        BlockPos hole = new BlockPos(PATH_X, GROUND, 2);
        helper.setBlock(hole, Blocks.AIR);
        BlockPos start = helper.absolutePos(new BlockPos(PATH_X, GROUND, 1));
        int[] levels = PathRamp.levels(start.getY(), absolute(helper, heights));
        BlockPos plank = new BlockPos(PATH_X - 1, GROUND + CLIFF, 1 + CLIFF_ROW + 1);
        helper.setBlock(plank, Blocks.SPRUCE_PLANKS);

        place(helper, new VillagePathPiece(start.getX(), start.getZ(), 0, 1, levels));

        for (int row = 0; row < LENGTH; row++) {
            int level = levels[row] - helper.absolutePos(BlockPos.ZERO).getY();
            if (row > 0) {
                helper.assertTrue(Math.abs(levels[row] - levels[row - 1]) <= PathRamp.MAX_STEP,
                        "Rows " + (row - 1) + " and " + row + " must have a step of 1 block at most");
            }
            for (int x = PATH_X - 1; x <= PATH_X + 1; x++) {
                BlockPos top = new BlockPos(x, level, 1 + row);
                if (top.above().equals(plank) || top.above(2).equals(plank)) {
                    continue;
                }
                helper.assertBlockPresent(Blocks.DIRT_PATH, top);
                helper.assertBlockPresent(Blocks.AIR, top.above());
                helper.assertBlockPresent(Blocks.AIR, top.above(2));
                helper.assertBlockNotPresent(Blocks.AIR, top.below());
            }
        }
        helper.assertTrue(levels[LENGTH - 1] == helper.absolutePos(BlockPos.ZERO).getY() + GROUND + CLIFF,
                "The last row must meet the high terrain");
        helper.assertBlockPresent(Blocks.SPRUCE_PLANKS, plank);
        helper.succeed();
    }

    /** A path on a flat terrain with a pool of shallow water: the path fills the pool with dirt and goes over it. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_gate_access")
    public static void gatePathFillsShallowWater(GameTestHelper helper) {
        int[] heights = new int[LENGTH];
        for (int row = 0; row < LENGTH; row++) {
            heights[row] = GROUND;
            for (int x = PATH_X - 1; x <= PATH_X + 1; x++) {
                helper.setBlock(new BlockPos(x, GROUND, 1 + row), Blocks.DIRT);
            }
        }
        BlockPos pool = new BlockPos(PATH_X, GROUND, 4);
        helper.setBlock(pool, Blocks.WATER);
        BlockPos start = helper.absolutePos(new BlockPos(PATH_X, GROUND, 1));
        int[] levels = PathRamp.levels(start.getY(), absolute(helper, heights));

        place(helper, new VillagePathPiece(start.getX(), start.getZ(), 0, 1, levels));

        helper.assertBlockPresent(Blocks.DIRT_PATH, pool);
        helper.assertBlockPresent(Blocks.AIR, pool.above());
        helper.assertBlockPresent(Blocks.DIRT_PATH, pool.north());
        helper.assertBlockPresent(Blocks.DIRT_PATH, pool.south());
        helper.succeed();
    }

    /** The heights of the terrain as absolute Y values. */
    private static int[] absolute(GameTestHelper helper, int[] heights) {
        int base = helper.absolutePos(BlockPos.ZERO).getY();
        int[] result = new int[heights.length];
        for (int i = 0; i < heights.length; i++) {
            result[i] = base + heights[i];
        }
        return result;
    }

    /** Places the blocks of a piece in the level, in all its box. */
    private static void place(GameTestHelper helper, StructurePiece piece) {
        ServerLevel level = helper.getLevel();
        BlockPos pivot = piece.getBoundingBox().getCenter();
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                piece.getBoundingBox(), new ChunkPos(pivot), pivot);
    }
}
