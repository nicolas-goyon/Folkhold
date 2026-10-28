package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.journal.MapMoves;
import io.github.nicolasgoyon.folkhold.world.journal.MapUpdates;
import java.util.List;
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
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.maps.MapId;
import net.minecraft.world.level.saveddata.maps.MapItemSavedData;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the update of a map at a distance in the cartography table (see {@link MapUpdates}). See
 * {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class MapUpdateGameTests {
    private static final BlockPos TABLE = new BlockPos(1, 1, 1);

    private MapUpdateGameTests() {
    }

    /**
     * The update button with a map and a paper gives the same map. When the player takes it, the update paints the
     * pixel of the table from the loaded chunk, with no player near the map.
     */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "map_updates")
    public static void updateButtonPaintsTheMap(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        helper.setBlock(TABLE, Blocks.CARTOGRAPHY_TABLE);
        BlockPos table = helper.absolutePos(TABLE);
        ItemStack map = MapItem.create(level, table.getX(), table.getZ(), (byte) 0, false, false);
        MapId id = map.get(DataComponents.MAP_ID);
        MapItemSavedData data = MapItem.getSavedData(map, level);
        // A pixel of a map of scale 0 is a block.
        int px = table.getX() - (data.centerX - MapMoves.SIZE / 2);
        int pz = table.getZ() - (data.centerZ - MapMoves.SIZE / 2);
        ServerPlayer player = FakePlayerFactory.getMinecraft(level);
        CartographyTableMenu menu = new CartographyTableMenu(0, player.getInventory(),
                ContainerLevelAccess.create(level, table));
        menu.getSlot(0).set(map.copyWithCount(1));
        menu.getSlot(1).set(new ItemStack(Items.PAPER));
        menu.clickMenuButton(player, MapMoves.UPDATE);
        ItemStack result = menu.getSlot(2).getItem().copy();
        menu.getSlot(2).onTake(player, result);
        menu.removed(player);
        player.getInventory().clearContent();

        helper.assertTrue(id.equals(result.get(DataComponents.MAP_ID)), "The update keeps the map");
        helper.assertTrue(MapUpdates.running(id), "The update must start");
        List<ChunkPos> chunks = MapUpdates.chunks(data);
        helper.assertTrue(chunks.size() == 64, "A map of scale 0 has 64 chunks: " + chunks.size());
        helper.succeedWhen(() -> {
            helper.assertFalse(MapUpdates.running(id), "The update must end");
            helper.assertTrue(data.colors[px + pz * MapMoves.SIZE] != 0, "The pixel of the table must have a color");
        });
    }
}
