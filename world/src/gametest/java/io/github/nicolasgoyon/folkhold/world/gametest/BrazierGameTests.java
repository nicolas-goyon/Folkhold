package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the braziers of the villages. See {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrazierGameTests {
    private static final BlockPos BRAZIER = new BlockPos(8, 1, 8);
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos CAMPFIRE = new BlockPos(4, 1, 4);

    private BrazierGameTests() {
    }

    /**
     * A lit brazier does not burn the entities. An armor stand and a pig fall on the brazier: the armor stand stays on
     * it, 1.5 blocks high. During 5 seconds, they do not burn and keep their health. On a lit campfire, the armor
     * stand burns.
     */
    @GameTest(template = "arena", timeoutTicks = 200, batch = "brazier_burn")
    public static void litBrazierDoesNotBurnTheEntities(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, ModBlocks.BRAZIER.get().defaultBlockState().setValue(BrazierBlock.LIT, true));
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, BRAZIER.above(2));
        Pig pig = helper.spawn(EntityType.PIG, BRAZIER.above(2));
        boolean[] burned = {false};
        helper.onEachTick(() -> burned[0] |= stand.isOnFire() || pig.isOnFire() || pig.getHealth() < pig.getMaxHealth());
        helper.startSequence()
                .thenIdle(100)
                .thenExecute(() -> {
                    double standY = stand.getY() - helper.absolutePos(BRAZIER).getY();
                    stand.discard();
                    pig.discard();
                    helper.assertFalse(burned[0], "A lit brazier must not burn the entities");
                    helper.assertTrue(Math.abs(standY - 1.5) < 0.01,
                            "The armor stand must stand on the brazier, 1.5 blocks high, not at " + standY);
                })
                .thenSucceed();
    }

    /** A campfire of the village layout becomes a brazier: lit at night and out by day. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_campfire")
    public static void campfireOfTheLayoutBecomesABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(HEART, ModBlocks.VILLAGE_HEART.get());
        helper.setBlock(CAMPFIRE, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false));
        VillageHeartBlockEntity heart = helper.getBlockEntity(HEART);
        heart.getLayout().scan(helper.getLevel(), helper.absolutePos(HEART));

        heart.applyDayNight(true);
        BlockState night = helper.getBlockState(CAMPFIRE);
        heart.applyDayNight(false);
        BlockState day = helper.getBlockState(CAMPFIRE);

        helper.assertTrue(heart.getLayout().braziers().contains(helper.absolutePos(CAMPFIRE)),
                "The layout must contain the campfire as a brazier");
        helper.assertTrue(night.is(ModBlocks.BRAZIER.get()) && night.getValue(BrazierBlock.LIT),
                "At night, the campfire must be a lit brazier, not " + night);
        helper.assertTrue(day.is(ModBlocks.BRAZIER.get()) && !day.getValue(BrazierBlock.LIT),
                "By day, the brazier must not burn, not " + day);
        helper.succeed();
    }
}
