package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.config.WorldConfig;
import io.github.nicolasgoyon.folkhold.world.entity.GuardEntity;
import io.github.nicolasgoyon.folkhold.world.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the guard. The template {@code folkhold_world:empty} is a structure of 1 air block.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class GuardGameTests {

    private GuardGameTests() {
    }

    /** A new guard has its equipment and the configured health. */
    @GameTest(template = "empty")
    public static void guardSpawnsWithEquipmentAndConfiguredHealth(GameTestHelper helper) {
        GuardEntity guard = helper.spawn(ModEntities.GUARD.get(), new BlockPos(0, 1, 0));
        guard.finalizeSpawn(helper.getLevel(), helper.getLevel().getCurrentDifficultyAt(guard.blockPosition()),
                MobSpawnType.COMMAND, null);

        helper.assertTrue(guard.getItemBySlot(EquipmentSlot.MAINHAND).is(Items.IRON_SWORD),
                "The guard must hold an iron sword");
        helper.assertTrue(guard.getItemBySlot(EquipmentSlot.OFFHAND).is(Items.SHIELD),
                "The guard must hold a shield");

        double expectedHealth = WorldConfig.GUARD.health();
        helper.assertTrue(Math.abs(guard.getMaxHealth() - expectedHealth) < 0.001,
                "Max health must be " + expectedHealth + ", not " + guard.getMaxHealth());
        helper.assertTrue(Math.abs(guard.getHealth() - expectedHealth) < 0.001,
                "The guard must spawn with full health");
        helper.succeed();
    }
}
