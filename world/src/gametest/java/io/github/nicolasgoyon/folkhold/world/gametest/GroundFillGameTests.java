package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.village.structure.GroundFillPiece;
import io.github.nicolasgoyon.folkhold.world.village.structure.TerrainPiece;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.TerrainAdjustment;
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
    /** The column of sand over a gap with water under it. */
    private static final BlockPos SAND_ON_WATER = new BlockPos(8, 0, 2);

    private GroundFillGameTests() {
    }

    /**
     * Near a tower, 4 columns of grass over 5 blocks of air and a column of sand over air get the ground block under
     * the surface: no air stays under the surface. Sand over a gap with water under it gets dirt. A cave deeper than
     * the largest gap, the air under the planks of another piece and the water stay. The piece does not change the
     * shape of the terrain: it has no terrain adaptation.
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
        helper.setBlock(SAND_ON_WATER.atY(FLOOR + 1), Blocks.WATER);
        helper.setBlock(SAND_ON_WATER.atY(SURFACE), Blocks.SAND);
        // The tower: a box above the surface, with the columns of the test in its band.
        BlockPos tower = helper.absolutePos(new BlockPos(6, SURFACE, 6));
        BoundingBox box = new BoundingBox(tower.getX(), tower.getY(), tower.getZ(), tower.getX() + 1,
                tower.getY() + GroundFillPiece.MAX_GAP + 4, tower.getZ() + 1);

        GroundFillPiece piece = new GroundFillPiece(box, List.of());
        helper.assertTrue(piece.getTerrainAdjustment() == TerrainAdjustment.NONE,
                "The piece must not change the shape of the terrain");
        place(helper, piece);

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
        helper.assertBlockPresent(Blocks.WATER, SAND_ON_WATER.atY(FLOOR + 1));
        for (int y = FLOOR + 2; y < SURFACE; y++) {
            helper.assertBlockPresent(Blocks.DIRT, SAND_ON_WATER.atY(y));
        }
        helper.succeed();
    }

    /**
     * A gate approach on a slope: the flat terrain of the gate clears the 3 blocks above its ground, and the terrain
     * at 4 blocks above the ground stays. The fill does not close the cleared space: the terrain above it stays over
     * air. A gap under the ground of the approach gets dirt.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "village_ground_fill")
    public static void groundFillKeepsTheGateApproachClear(GameTestHelper helper) {
        int ground = FLOOR + 2;
        int high = ground + TerrainPiece.CLEARANCE + 1;
        for (int x = 0; x <= 10; x++) {
            for (int z = 0; z <= 8; z++) {
                helper.setBlock(new BlockPos(x, FLOOR, z), Blocks.STONE);
                helper.setBlock(new BlockPos(x, FLOOR + 1, z), Blocks.DIRT);
                helper.setBlock(new BlockPos(x, ground, z), Blocks.GRASS_BLOCK);
                for (int y = ground + 1; y <= high + 1; y++) {
                    helper.setBlock(new BlockPos(x, y, z), Blocks.AIR);
                }
            }
        }
        // The slope: the terrain goes up to 4 blocks above the ground on the rows z = 4 to 8.
        for (int x = 2; x <= 8; x++) {
            for (int z = 4; z <= 8; z++) {
                for (int y = ground + 1; y <= high; y++) {
                    helper.setBlock(new BlockPos(x, y, z), y == high ? Blocks.GRASS_BLOCK : Blocks.DIRT);
                }
            }
        }
        // A gap under the ground of the approach.
        helper.setBlock(new BlockPos(3, FLOOR + 1, 4), Blocks.AIR);
        BlockPos min = helper.absolutePos(new BlockPos(2, ground, 2));
        BlockPos max = helper.absolutePos(new BlockPos(8, ground, 6));
        TerrainPiece approach = new TerrainPiece(Math.min(min.getX(), max.getX()), Math.min(min.getZ(), max.getZ()),
                Math.max(min.getX(), max.getX()), Math.max(min.getZ(), max.getZ()), min.getY(), true);
        BlockPos gate = helper.absolutePos(new BlockPos(5, ground, 7));
        BoundingBox gateBox = new BoundingBox(gate.getX(), gate.getY(), gate.getZ(), gate.getX(), gate.getY() + 6,
                gate.getZ());

        place(helper, approach);
        place(helper, new GroundFillPiece(gateBox, List.of(approach.getBoundingBox())));

        for (int x = 2; x <= 8; x++) {
            for (int z = 4; z <= 6; z++) {
                for (int y = ground + 1; y < high; y++) {
                    helper.assertBlockPresent(Blocks.AIR, new BlockPos(x, y, z));
                }
                helper.assertBlockPresent(Blocks.GRASS_BLOCK, new BlockPos(x, high, z));
            }
        }
        helper.assertBlockPresent(Blocks.DIRT, new BlockPos(3, FLOOR + 1, 4));
        helper.succeed();
    }

    /** Places the blocks of a piece in the level, in all its box. */
    private static void place(GameTestHelper helper, StructurePiece piece) {
        ServerLevel level = helper.getLevel();
        BlockPos pivot = piece.getBoundingBox().getCenter();
        piece.postProcess(level, level.structureManager(), level.getChunkSource().getGenerator(), level.getRandom(),
                piece.getBoundingBox(), new ChunkPos(pivot), pivot);
    }
}
