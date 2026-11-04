package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for Cartofold, a required dependency of the world mod: its survey maps move and update at the
 * cartography table. See {@link TestSupport} for the templates and the helpers.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CartofoldGameTests {
    private CartofoldGameTests() {
    }

    /** Cartofold is loaded, with its survey map. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "cartofold")
    public static void cartofoldIsLoaded(GameTestHelper helper) {
        helper.assertTrue(ModList.get().isLoaded("cartofold"), "Cartofold is loaded");
        ResourceLocation surveyMap = ResourceLocation.fromNamespaceAndPath("cartofold", "survey_map");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(surveyMap), "The survey map is registered");
        helper.succeed();
    }
}
