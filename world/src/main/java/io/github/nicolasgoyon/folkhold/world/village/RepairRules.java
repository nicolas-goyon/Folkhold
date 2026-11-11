package io.github.nicolasgoyon.folkhold.world.village;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.block.QuestBoardBlock;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.Set;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * The comparison of a block of a village with its reference block, for the repairs (see {@link VillageRepairs}). A
 * repair changes only a damaged block. A damaged block is a missing block or another block. A state that the normal
 * use, the weather or the growth changes is not damage. Thus the comparison ignores these changes:
 *
 * <ul>
 *   <li>The properties that the villagers, the players, the redstone, the weather or the growth change: open doors and
 *       gates, lit braziers, the age of a plant, the water in a block, the bites of a cake, the papers of a quest
 *       board (the offered quests of the village).</li>
 *   <li>The properties that come from the neighbors: the connections of fences, walls and panes, the shape of stairs
 *       and of rails.</li>
 *   <li>The blocks of one group of the data file {@link RepairEquivalents}: the weather, the growth, the normal use
 *       or another mod changes one block of a group into another block of the group. Examples: an empty cauldron and
 *       a water cauldron, a stem with its fruit and with no fruit, grass and dirt, water and ice, cobblestone and mossy
 *       cobblestone. The properties that the two blocks share must have the same values, except the ignored
 *       properties.</li>
 *   <li>The color of a banner: the village heart gives the color of the faction to the banners.</li>
 *   <li>A brazier in the place of a campfire.</li>
 *   <li>The blocks that the weather and the plants add on an empty position: snow, grass, flowers, trees (tag
 *       {@link #NATURAL}).</li>
 *   <li>The technical blocks of the structures: jigsaw blocks, structure blocks.</li>
 * </ul>
 *
 * <p>A player action that gives another block is damage, because the template block is not there: a candle cake that
 * becomes a cake, a chipped anvil, a flower pot with a plant.
 */
public final class RepairRules {
    /** The blocks that the weather and the plants add on an empty position. A repair does not remove them. */
    public static final TagKey<Block> NATURAL = tag("construction/repair_natural");
    /** The blocks of the soil. The group {@code soil} of {@link RepairEquivalents} contains this tag. */
    public static final TagKey<Block> SOIL = tag("construction/repair_soil");

    /** The properties that the comparison ignores. */
    private static final Set<Property<?>> IGNORED = Set.of(
            BlockStateProperties.OPEN, BlockStateProperties.LIT, BlockStateProperties.POWERED,
            BlockStateProperties.IN_WALL, BlockStateProperties.OCCUPIED, BlockStateProperties.HAS_BOOK,
            BlockStateProperties.SIGNAL_FIRE, BlockStateProperties.TRIGGERED, BlockStateProperties.ENABLED,
            BlockStateProperties.SNOWY, BlockStateProperties.WATERLOGGED, BlockStateProperties.EXTENDED,
            BlockStateProperties.POWER, BlockStateProperties.NOTE, BlockStateProperties.HAS_RECORD,
            BlockStateProperties.BERRIES, BlockStateProperties.BITES, BlockStateProperties.CANDLES,
            BlockStateProperties.STAGE, BlockStateProperties.MOISTURE, BlockStateProperties.PERSISTENT,
            BlockStateProperties.DISTANCE, BlockStateProperties.LEVEL, BlockStateProperties.LEVEL_CAULDRON,
            BlockStateProperties.LEVEL_COMPOSTER, BlockStateProperties.LEVEL_HONEY, BlockStateProperties.LAYERS,
            BlockStateProperties.AGE_1, BlockStateProperties.AGE_2, BlockStateProperties.AGE_3,
            BlockStateProperties.AGE_4, BlockStateProperties.AGE_5, BlockStateProperties.AGE_7,
            BlockStateProperties.AGE_15, BlockStateProperties.AGE_25,
            BlockStateProperties.NORTH, BlockStateProperties.EAST, BlockStateProperties.SOUTH,
            BlockStateProperties.WEST, BlockStateProperties.UP, BlockStateProperties.DOWN,
            BlockStateProperties.NORTH_WALL, BlockStateProperties.EAST_WALL, BlockStateProperties.SOUTH_WALL,
            BlockStateProperties.WEST_WALL, BlockStateProperties.NORTH_REDSTONE, BlockStateProperties.EAST_REDSTONE,
            BlockStateProperties.SOUTH_REDSTONE, BlockStateProperties.WEST_REDSTONE,
            BlockStateProperties.STAIRS_SHAPE, BlockStateProperties.RAIL_SHAPE,
            BlockStateProperties.RAIL_SHAPE_STRAIGHT, BlockStateProperties.CHEST_TYPE, QuestBoardBlock.PAPERS);

    private RepairRules() {
    }

    /**
     * True if a block of a village is damaged: a repair must give it the reference block.
     *
     * @param actual   the block of the level
     * @param expected the reference block
     * @return true if the block is damaged
     */
    public static boolean isDamaged(BlockState actual, BlockState expected) {
        if (actual == expected || isTechnical(actual)) {
            return false;
        }
        if (expected.isAir()) {
            return !actual.isAir() && !actual.is(NATURAL);
        }
        if (isSameFamily(actual, expected)) {
            return false;
        }
        if (actual.getBlock() != expected.getBlock()
                && !RepairEquivalents.get().areEquivalent(actual, expected)) {
            return true;
        }
        return hasOtherValues(actual, expected);
    }

    /**
     * True if a property of the reference block has another value in the block of the level. The check uses only the
     * properties that the two blocks have and that the comparison does not ignore.
     */
    private static boolean hasOtherValues(BlockState actual, BlockState expected) {
        for (Property<?> property : expected.getProperties()) {
            if (!IGNORED.contains(property) && actual.hasProperty(property)
                    && !expected.getValue(property).equals(actual.getValue(property))) {
                return true;
            }
        }
        return false;
    }

    /** A block that the generation of a structure removes: a repair never changes it. */
    private static boolean isTechnical(BlockState state) {
        return state.is(Blocks.JIGSAW) || state.is(Blocks.STRUCTURE_BLOCK) || state.is(Blocks.STRUCTURE_VOID);
    }

    /** Two blocks of one family: a banner of another color, or a brazier for a campfire. */
    private static boolean isSameFamily(BlockState actual, BlockState expected) {
        Block block = actual.getBlock();
        Block reference = expected.getBlock();
        if (block instanceof WallBannerBlock && reference instanceof WallBannerBlock) {
            return actual.getValue(WallBannerBlock.FACING) == expected.getValue(WallBannerBlock.FACING);
        }
        if (block instanceof BannerBlock && reference instanceof BannerBlock) {
            return actual.getValue(BannerBlock.ROTATION).equals(expected.getValue(BannerBlock.ROTATION));
        }
        return reference instanceof CampfireBlock && block instanceof BrazierBlock;
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, path));
    }
}
