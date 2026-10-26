package io.github.nicolasgoyon.folkhold.world.gametest;

import io.github.nicolasgoyon.folkhold.blocks.block.BrazierBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModItems;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartBlockEntity;
import io.github.nicolasgoyon.folkhold.world.village.VillageBraziers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.projectile.ThrownPotion;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.alchemy.PotionContents;
import net.minecraft.world.item.alchemy.Potions;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * In-game tests for the braziers: the actions of a vanilla campfire, and the braziers of the villages. See
 * {@link TestSupport} for the templates.
 */
@GameTestHolder(FolkholdWorld.MOD_ID)
@PrefixGameTestTemplate(false)
public final class BrazierGameTests {
    private static final BlockPos BRAZIER = new BlockPos(8, 1, 8);
    private static final BlockPos HEART = new BlockPos(8, 1, 8);
    private static final BlockPos CAMPFIRE = new BlockPos(4, 1, 4);
    /** A projectile starts at this position: in the arena, below the ceiling of barriers of the test structure. */
    private static final BlockPos ABOVE_BRAZIER = BRAZIER.above(2);

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

    /** A campfire in water becomes a brazier in water. The brazier does not burn at night. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_campfire")
    public static void campfireInWaterBecomesABrazierThatDoesNotBurn(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(CAMPFIRE, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, false)
                .setValue(CampfireBlock.WATERLOGGED, true));

        VillageBraziers.set(helper.getLevel(), helper.absolutePos(CAMPFIRE), true);

        helper.assertBlockPresent(ModBlocks.BRAZIER.get(), CAMPFIRE);
        helper.assertBlockProperty(CAMPFIRE, BrazierBlock.WATERLOGGED, true);
        helper.assertBlockProperty(CAMPFIRE, BrazierBlock.LIT, false);
        helper.setBlock(CAMPFIRE, Blocks.AIR);
        helper.succeed();
    }

    /** Flint and steel lights a brazier, as a campfire. The flint and steel takes 1 point of damage. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_actions")
    public static void flintAndSteelLightsABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, brazier(false));
        ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);

        useOnTop(helper, BRAZIER, flint);

        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, true);
        helper.assertTrue(flint.getDamageValue() == 1,
                "The flint and steel must take 1 point of damage, not " + flint.getDamageValue());
        helper.succeed();
    }

    /** A fire charge lights a brazier, as a campfire. The player uses 1 fire charge. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_actions")
    public static void fireChargeLightsABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, brazier(false));
        ItemStack charges = new ItemStack(Items.FIRE_CHARGE, 2);

        useOnTop(helper, BRAZIER, charges);

        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, true);
        helper.assertTrue(charges.getCount() == 1,
                "The player must use 1 fire charge, not " + (2 - charges.getCount()));
        helper.succeed();
    }

    /** A burning arrow that hits a brazier lights it, as a campfire. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "brazier_actions")
    public static void burningArrowLightsABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, brazier(false));
        Arrow arrow = helper.spawn(EntityType.ARROW, ABOVE_BRAZIER);
        arrow.igniteForSeconds(5.0F);
        arrow.setDeltaMovement(0.0, -1.5, 0.0);

        helper.succeedWhen(() -> {
            helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, true);
            arrow.discard();
        });
    }

    /** A shovel puts out a lit brazier, as a campfire. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_actions")
    public static void shovelPutsOutABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, brazier(true));

        useOnTop(helper, BRAZIER, new ItemStack(Items.IRON_SHOVEL));

        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, false);
        helper.succeed();
    }

    /** A splash water potion puts out a lit brazier, as a campfire. */
    @GameTest(template = "arena", timeoutTicks = 100, batch = "brazier_actions")
    public static void waterPotionPutsOutABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, brazier(true));
        ThrownPotion potion = helper.spawn(EntityType.POTION, ABOVE_BRAZIER);
        potion.setItem(PotionContents.createItemStack(Items.SPLASH_POTION, Potions.WATER));
        potion.setDeltaMovement(0.0, -1.0, 0.0);

        helper.succeedWhen(() -> helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, false));
    }

    /**
     * Water puts out a lit brazier, and the brazier becomes waterlogged. A brazier in water does not burn: flint and
     * steel and the village do not light it.
     */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_actions")
    public static void waterPutsOutABrazier(GameTestHelper helper) {
        TestSupport.buildFloor(helper);
        helper.setBlock(BRAZIER, brazier(true));
        BlockPos pos = helper.absolutePos(BRAZIER);
        BlockState state = helper.getBlockState(BRAZIER);

        ((BrazierBlock) state.getBlock()).placeLiquid(helper.getLevel(), pos, state, Fluids.WATER.getSource(false));

        helper.assertBlockProperty(BRAZIER, BrazierBlock.WATERLOGGED, true);
        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, false);
        useOnTop(helper, BRAZIER, new ItemStack(Items.FLINT_AND_STEEL));
        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, false);
        VillageBraziers.set(helper.getLevel(), pos, true);
        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, false);
        helper.setBlock(BRAZIER, Blocks.AIR);
        helper.succeed();
    }

    /** A brazier that a player places is lit, as a campfire. */
    @GameTest(template = "arena", timeoutTicks = 20, batch = "brazier_actions")
    public static void placedBrazierIsLit(GameTestHelper helper) {
        TestSupport.buildFloor(helper);

        useOnTop(helper, BRAZIER.below(), new ItemStack(ModItems.BRAZIER.get()));

        helper.assertBlockPresent(ModBlocks.BRAZIER.get(), BRAZIER);
        helper.assertBlockProperty(BRAZIER, BrazierBlock.LIT, true);
        helper.succeed();
    }

    private static BlockState brazier(boolean lit) {
        return ModBlocks.BRAZIER.get().defaultBlockState().setValue(BrazierBlock.LIT, lit);
    }

    /** A player in survival mode uses an item on the top face of a block. */
    private static void useOnTop(GameTestHelper helper, BlockPos pos, ItemStack stack) {
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        BlockPos absolute = helper.absolutePos(pos);
        helper.useBlock(pos, player, new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false));
    }
}
