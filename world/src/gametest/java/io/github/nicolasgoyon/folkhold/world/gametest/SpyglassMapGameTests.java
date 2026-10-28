package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.journal.SpyglassMaps;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the exploration of the vanilla maps with a spyglass (see {@link SpyglassMaps}). See
 * {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SpyglassMapGameTests {
    private static final int SLOT = 20;

    private SpyglassMapGameTests() {
    }

    /** A player with a spyglass in the inventory updates a map in a larger radius. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "spyglass_maps")
    public static void spyglassMakesTheRadiusLarger(GameTestHelper helper) {
        ServerPlayer player = FakePlayerFactory.getMinecraft(helper.getLevel());
        ItemStack before = player.getInventory().getItem(SLOT);
        player.getInventory().setItem(SLOT, ItemStack.EMPTY);
        int without = SpyglassMaps.radius(128, player);
        player.getInventory().setItem(SLOT, new ItemStack(Items.SPYGLASS));
        int with = SpyglassMaps.radius(128, player);
        player.getInventory().setItem(SLOT, before);

        helper.assertTrue(without == 128, "With no spyglass, the radius of the game: " + without);
        helper.assertTrue(with == 128 * SpyglassMaps.FACTOR, "With a spyglass, a larger radius: " + with);
        helper.succeed();
    }
}
