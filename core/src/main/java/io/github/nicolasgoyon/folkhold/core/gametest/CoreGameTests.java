package io.github.nicolasgoyon.folkhold.core.gametest;

import io.github.nicolasgoyon.folkhold.core.FolkholdCore;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the core mod.
 *
 * <p>The game loads these tests only when GameTest is enabled (dev runs and CI).
 * The template {@code folkhold_core:empty} is a structure of 1 air block.
 */
@GameTestHolder(FolkholdCore.MOD_ID)
@PrefixGameTestTemplate(false)
public final class CoreGameTests {

    private CoreGameTests() {
    }

    /** The server starts with the core mod loaded. */
    @GameTest(template = "empty")
    public static void coreModIsLoaded(GameTestHelper helper) {
        if (!ModList.get().isLoaded(FolkholdCore.MOD_ID)) {
            helper.fail("Mod " + FolkholdCore.MOD_ID + " is not loaded");
        }
        helper.succeed();
    }
}
