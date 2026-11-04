package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.BENCH_Z;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.ShulkerMinecart;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.LockCode;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ShulkerBoxSlot;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.BundleContents;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.SeededContainerLoot;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the shulker box minecart, on the template {@code folkhold_balance:bench}. */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class ShulkerMinecartGameTests {
    private static final int SIZE = ShulkerMinecart.SIZE;

    private ShulkerMinecartGameTests() {
    }

    /**
     * Each removal drops exactly one item with the 27 full stacks and the color of the minecart, and no stack on the
     * ground: a player, a creative player, an explosion, lava, a cactus and {@code /kill}. An empty minecart removed
     * with {@code /kill} drops nothing.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void removedMinecartDropsOneItem(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        DamageSources sources = helper.getLevel().damageSources();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        Player creative = helper.makeMockPlayer(GameType.CREATIVE);
        creative.getAbilities().instabuild = true;
        List<BiConsumer<ShulkerMinecart, Float>> removals = List.of(
                (cart, amount) -> cart.hurt(sources.playerAttack(player), amount),
                (cart, amount) -> cart.hurt(sources.playerAttack(creative), 1.0F),
                (cart, amount) -> cart.hurt(sources.explosion(null, null), amount),
                (cart, amount) -> cart.hurt(sources.lava(), amount),
                (cart, amount) -> cart.hurt(sources.cactus(), amount),
                (cart, amount) -> {
                    if (!cart.isRemoved()) {
                        cart.kill();
                    }
                });
        Item[] contents = {Items.COBBLESTONE, Items.DIRT, Items.SAND, Items.GRAVEL, Items.OAK_LOG, Items.STONE};
        List<ShulkerMinecart> carts = new ArrayList<>();
        for (int i = 0; i < removals.size(); i++) {
            ShulkerMinecart cart = helper.spawn(ModEntities.SHULKER_BOX_MINECART.get(),
                    new Vec3(2.5 + 3 * i, 1.0, 2.5));
            cart.setColor(i == 0 ? null : DyeColor.byId(i));
            fill(cart, contents[i], 64);
            carts.add(cart);
        }
        ShulkerMinecart empty = helper.spawn(ModEntities.SHULKER_BOX_MINECART.get(), new Vec3(4.0, 1.0, 5.5));
        for (int i = 0; i < removals.size(); i++) {
            // A second hit after the destruction must not drop more items.
            removals.get(i).accept(carts.get(i), 100.0F);
            removals.get(i).accept(carts.get(i), 100.0F);
        }
        empty.kill();
        helper.runAtTickTime(5, () -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    helper.getBounds().inflate(2.0));
            helper.assertTrue(drops.size() == removals.size(), "The minecarts drop " + drops.size() + " items");
            for (int i = 0; i < removals.size(); i++) {
                helper.assertTrue(carts.get(i).isRemoved(), "The minecart " + i + " is not removed");
                DyeColor color = i == 0 ? null : DyeColor.byId(i);
                Item content = contents[i];
                List<ItemStack> found = drops.stream().map(ItemEntity::getItem)
                        .filter(stack -> stack.is(ModItems.SHULKER_BOX_MINECART.get())
                                && stack.get(DataComponents.BASE_COLOR) == color)
                        .toList();
                helper.assertTrue(found.size() == 1, "The removal " + i + " drops " + found.size() + " items");
                helper.assertTrue(count(found.get(0), content) == SIZE * 64,
                        "The item of the removal " + i + " has " + count(found.get(0), content) + " items");
            }
            drops.forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    /**
     * The recipe keeps the items and the color of the shulker box. The placed item gives them back to the minecart.
     * A shulker box with a loot table does not match.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void recipeAndPlacementKeepTheItems(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        ItemStack box = new ItemStack(Items.RED_SHULKER_BOX);
        List<ItemStack> items = List.of(new ItemStack(Items.DIAMOND, 7), ItemStack.EMPTY, new ItemStack(Items.APPLE));
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
        ItemStack result = craft(helper, box);
        helper.assertTrue(result.is(ModItems.SHULKER_BOX_MINECART.get()), "The recipe gives " + result);
        helper.assertTrue(result.get(DataComponents.BASE_COLOR) == DyeColor.RED, "The recipe loses the color");
        helper.assertTrue(box.get(DataComponents.CONTAINER).equals(result.get(DataComponents.CONTAINER)),
                "The recipe loses the items");
        ItemStack lootBox = new ItemStack(Items.SHULKER_BOX);
        lootBox.set(DataComponents.CONTAINER_LOOT, new SeededContainerLoot(BuiltInLootTables.SIMPLE_DUNGEON, 1L));
        helper.assertTrue(craft(helper, lootBox).isEmpty(), "A shulker box with a loot table matches");
        ItemStack lockedBox = new ItemStack(Items.SHULKER_BOX);
        lockedBox.set(DataComponents.LOCK, new LockCode("key"));
        helper.assertTrue(craft(helper, lockedBox).isEmpty(), "A shulker box with a lock matches");
        // The tooltip shows the 2 stacks, as the shulker box item.
        List<Component> tooltip = new ArrayList<>();
        result.getItem().appendHoverText(result, Item.TooltipContext.EMPTY, tooltip, TooltipFlag.NORMAL);
        helper.assertTrue(tooltip.size() == 2 && tooltip.get(0).getString().contains("7"),
                "The tooltip is " + tooltip);

        BlockPos rail = new BlockPos(4, 1, BENCH_Z);
        TrainGameTests.rail(helper, 4, BENCH_Z, RailShape.EAST_WEST);
        TrainGameTests.fixRails(helper);
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.setItemInHand(InteractionHand.MAIN_HAND, result);
        BlockPos absolute = helper.absolutePos(rail);
        result.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(absolute), Direction.UP, absolute, false)));
        helper.assertTrue(result.isEmpty(), "The placement does not use the item");
        List<ShulkerMinecart> carts = helper.getLevel().getEntitiesOfClass(ShulkerMinecart.class,
                helper.getBounds());
        helper.assertTrue(carts.size() == 1, "The item places " + carts.size() + " minecarts");
        ShulkerMinecart cart = carts.get(0);
        helper.assertTrue(cart.getColor() == DyeColor.RED, "The minecart has the color " + cart.getColor());
        helper.assertTrue(ItemStack.matches(cart.getItem(0), items.get(0)) && cart.getItem(1).isEmpty()
                && ItemStack.matches(cart.getItem(2), items.get(2)), "The minecart does not get the items");
        cart.clearContent();
        cart.discard();
        helper.succeed();
    }

    /** A shulker box minecart item cannot go into a shulker box, a bundle or a shulker box minecart. */
    @GameTest(template = "bench", batch = "balance_rails")
    public static void itemCannotGoIntoAShulkerBox(GameTestHelper helper) {
        ItemStack item = new ItemStack(ModItems.SHULKER_BOX_MINECART.get());
        ShulkerBoxBlockEntity box = new ShulkerBoxBlockEntity(BlockPos.ZERO, Blocks.SHULKER_BOX.defaultBlockState());
        helper.assertFalse(box.canPlaceItemThroughFace(0, item, Direction.UP), "A hopper puts it into a box");
        helper.assertFalse(new ShulkerBoxSlot(box, 0, 0, 0).mayPlace(item), "A player puts it into a box");
        helper.assertFalse(canFitInsideContainerItems(item.getItem()), "The method with no argument gives true");
        helper.assertTrue(new BundleContents.Mutable(BundleContents.EMPTY).tryInsert(item) == 0,
                "It goes into a bundle");
        ShulkerMinecart cart = helper.spawn(ModEntities.SHULKER_BOX_MINECART.get(), new Vec3(4.5, 2.0, 2.5));
        helper.assertFalse(cart.canPlaceItem(0, item), "It goes into a shulker box minecart");
        helper.assertFalse(cart.canPlaceItem(0, new ItemStack(Items.SHULKER_BOX)), "A shulker box goes into it");
        helper.assertTrue(cart.canPlaceItem(0, new ItemStack(Items.CHEST)), "A chest does not go into it");
        cart.discard();
        helper.succeed();
    }

    /** A hopper above the minecart fills its 27 slots and keeps a shulker box. */
    @GameTest(template = "bench", timeoutTicks = 500, batch = "balance_rails")
    public static void hopperFillsTheMinecart(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        ShulkerMinecart cart = cartOnRail(helper);
        BlockPos hopper = new BlockPos(4, 2, BENCH_Z);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        if (helper.getBlockEntity(hopper) instanceof HopperBlockEntity block) {
            block.setItem(1, new ItemStack(Items.SHULKER_BOX));
        }
        AtomicInteger given = new AtomicInteger();
        helper.onEachTick(() -> {
            if (helper.getBlockEntity(hopper) instanceof HopperBlockEntity block && block.getItem(0).isEmpty()
                    && given.get() < SIZE + 1) {
                block.setItem(0, new ItemStack(Items.MINECART));
                given.incrementAndGet();
            }
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(cart.countItem(Items.MINECART) == SIZE, "The minecart has "
                    + cart.countItem(Items.MINECART) + " minecarts");
            helper.assertTrue(given.get() == SIZE + 1, "The hopper gives " + given.get() + " minecarts");
            helper.assertTrue(helper.getBlockEntity(hopper) instanceof HopperBlockEntity block
                    && block.getItem(1).is(Items.SHULKER_BOX), "The shulker box leaves the hopper");
            cart.clearContent();
            cart.discard();
        });
    }

    /** A hopper below the rail takes all the items of the minecart and moves them to a chest. */
    @GameTest(template = "bench", timeoutTicks = 500, batch = "balance_rails")
    public static void hopperEmptiesTheMinecart(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        BlockPos hopper = new BlockPos(4, 0, BENCH_Z);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
        helper.setBlock(hopper.north(), Blocks.CHEST);
        ShulkerMinecart cart = cartOnRail(helper);
        fill(cart, Items.COBBLESTONE, 1);
        helper.succeedWhen(() -> {
            int inChest = helper.getBlockEntity(hopper.north()) instanceof Container chest
                    ? chest.countItem(Items.COBBLESTONE) : -1;
            helper.assertTrue(cart.isAlive(), "The minecart is removed");
            helper.assertTrue(inChest == SIZE && cart.isEmpty(),
                    "The chest has " + inChest + " cobblestone, the minecart " + cart.countItem(Items.COBBLESTONE));
            cart.discard();
        });
    }

    @SuppressWarnings("deprecation")
    private static boolean canFitInsideContainerItems(Item item) {
        return item.canFitInsideContainerItems();
    }

    /** The result of the crafting recipe for a minecart and a shulker box, or an empty stack. */
    private static ItemStack craft(GameTestHelper helper, ItemStack box) {
        CraftingInput input = CraftingInput.of(2, 1, List.of(new ItemStack(Items.MINECART), box));
        return helper.getLevel().getRecipeManager().getRecipeFor(RecipeType.CRAFTING, input, helper.getLevel())
                .map(recipe -> recipe.value().assemble(input, helper.getLevel().registryAccess()))
                .orElse(ItemStack.EMPTY);
    }

    /** A shulker box minecart on a rail at x = 4. */
    private static ShulkerMinecart cartOnRail(GameTestHelper helper) {
        TrainGameTests.rail(helper, 4, BENCH_Z, RailShape.EAST_WEST);
        TrainGameTests.fixRails(helper);
        return helper.spawn(ModEntities.SHULKER_BOX_MINECART.get(), new Vec3(4.5, CART_Y, BENCH_Z + 0.5));
    }

    private static void fill(ShulkerMinecart cart, Item item, int count) {
        for (int slot = 0; slot < SIZE; slot++) {
            cart.setItem(slot, new ItemStack(item, count));
        }
    }

    /** The count of an item in the items of a shulker box minecart item. */
    private static int count(ItemStack stack, Item item) {
        return stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY).nonEmptyStream()
                .filter(content -> content.is(item)).mapToInt(ItemStack::getCount).sum();
    }
}
