package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.journal.MapMoves;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.inventory.CartographyTableMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.MapItem;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the move of a map in the cartography table (see {@link MapMoves}). See {@link TestSupport} for the
 * templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapMoveGameTests {
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);

    private MapMoveGameTests() {
    }

    /**
     * A map and a paper with the east button give a map with a center half a map to the east, with the same scale and
     * the explored pixels in their new positions. The same button again gives the zoom of the game.
     */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "map_moves")
    public static void eastButtonMovesTheMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
        BlockPos table = helper.absolutePos(TABLE);
        ItemStack map = MapItem.create(level, table.getX(), table.getZ(), (byte) 2, true, false);
        MapItemSavedData data = MapItem.getSavedData(map, level);
        data.colors[100 + 10 * MapMoves.SIZE] = 7;
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        CartographyTableMenu menu = new CartographyTableMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, table));
        menu.getSlot(0).set(map.copyWithCount(2));
        menu.getSlot(1).set(new ItemStack(Items.PAPER, 2));

        boolean zoom = menu.getSlot(2).getItem().has(DataComponents.MAP_POST_PROCESSING);
        menu.clickMenuButton(player, MapMoves.EAST);
        ItemStack result = menu.getSlot(2).getItem().copy();
        menu.getSlot(2).onTake(player, result);
        MapItemSavedData moved = MapItem.getSavedData(result, level);

        menu.clickMenuButton(player, MapMoves.EAST);
        boolean zoomAgain = menu.getSlot(2).getItem().has(DataComponents.MAP_POST_PROCESSING);
        menu.removed(player);
        player.getInventory().clearContent();

        helper.assertTrue(zoom, "With no direction, the result is the zoom of the game");
        helper.assertTrue(moved != null && moved != data, "The moved map must have new data");
        int step = MapMoves.STEP << data.scale;
        helper.assertTrue(moved.centerX == data.centerX + step && moved.centerZ == data.centerZ,
                "The center must move half a map to the east: " + moved.centerX + " " + moved.centerZ);
        helper.assertTrue(moved.scale == data.scale, "The scale stays");
        helper.assertTrue(moved.colors[36 + 10 * MapMoves.SIZE] == 7, "The explored pixel keeps its place");
        helper.assertTrue(!result.has(DataComponents.CUSTOM_DATA), "The result has no mark of the move");
        helper.assertTrue(zoomAgain, "The same button again gives the zoom of the game");
        helper.succeed();
    }
}
