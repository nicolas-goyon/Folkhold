package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.village.StreetBlocks;
import io.github.nicolasgoyon.folkhold.world.village.VillageReference;
import io.github.nicolasgoyon.folkhold.world.village.VillageRepairs;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the repairs of the streets of a village (see {@link StreetBlocks}). See {@link TestSupport} for the
 * templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class StreetRepairGameTests {
    private static final int SIZE = 16;
    private static final BlockPos HEART = new BlockPos(2, 1, 2);
    private static final BlockPos PATH = new BlockPos(6, 1, 6);
    private static final BlockPos GRAVEL = new BlockPos(7, 1, 6);
    private static final BlockPos LIGHT = new BlockPos(8, 1, 6);
    private static final ResourceLocation PIECE = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID,
            "village/house_cottage");

    private StreetRepairGameTests() {
    }

    /**
     * The heart records the surface of the streets: a path block stays, gravel becomes the planks of the palisade, a
     * street light is not a surface. A hole in a street is a damaged block, and a paved street is not.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "street_repairs")
    public static void holesOfTheStreetsAreDamaged(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        TestSupport.buildFloor(helper, SIZE);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        helper.setBlock(PATH, Blocks.DIRT_PATH);
        helper.setBlock(GRAVEL, Blocks.GRAVEL);
        helper.setBlock(LIGHT, Blocks.OAK_FENCE);
        BlockPos path = helper.absolutePos(PATH);
        BlockPos gravel = helper.absolutePos(GRAVEL);
        BlockPos light = helper.absolutePos(LIGHT);
        BlockPos far = helper.absolutePos(new BlockPos(SIZE - 1, 0, SIZE - 1));
        VillageReference.Palisade palisade = new VillageReference.Palisade(far.above(), 1, 0, List.of(far.getY()),
                Blocks.COBBLESTONE.defaultBlockState(), Blocks.OAK_LOG.defaultBlockState());
        VillageReference reference = new VillageReference(List.of(new VillageReference.Piece(PIECE,
                far.above(40), Rotation.NONE)), List.of(palisade), Optional.empty(), List.of(), Optional.empty(),
                List.of(), List.of(path.atY(0), gravel.atY(0), light.atY(0)), Map.of());

        Map<BlockPos, BlockState> streets = StreetBlocks.record(level, reference);
        helper.assertTrue(streets.get(path) != null && streets.get(path).is(Blocks.DIRT_PATH),
                "The path block must be a surface block: " + streets);
        helper.assertTrue(streets.get(gravel) != null && streets.get(gravel).is(Blocks.OAK_PLANKS),
                "The gravel must become the planks of the palisade: " + streets);
        helper.assertFalse(streets.containsKey(light), "A street light is not a surface block");

        VillageReference withStreets = reference.withStreets(streets);
        heart.setRepairReference(withStreets);
        helper.assertTrue(VillageRepairs.damaged(level, heart, withStreets).stream().noneMatch(streets::containsKey),
                "An intact street has no damaged block");
        helper.setBlock(PATH, Blocks.AIR);
        helper.setBlock(GRAVEL, Blocks.WATER);
        helper.setBlock(GRAVEL.below(), Blocks.AIR);
        Set<BlockPos> holes = StreetBlocks.holes(level, withStreets);
        helper.assertTrue(holes.equals(Set.of(path, gravel)), "The 2 holes must be found: " + holes);
        helper.assertTrue(VillageRepairs.damaged(level, heart, withStreets).containsAll(holes),
                "The holes must be damaged blocks");
        helper.setBlock(PATH, Blocks.STONE_BRICKS);
        helper.assertFalse(StreetBlocks.holes(level, withStreets).contains(path), "A paved street is not a hole");
        helper.setBlock(HEART, Blocks.AIR);
        helper.succeed();
    }
}
