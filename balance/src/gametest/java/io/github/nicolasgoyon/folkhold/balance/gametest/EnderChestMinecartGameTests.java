package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.BENCH_Z;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.EnderChestMinecart;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartChest;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the ender chest minecart, on the template {@code folkhold_balance:bench}. */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class EnderChestMinecartGameTests {
    private EnderChestMinecartGameTests() {
    }

    /**
     * Two players open the same minecart: each player sees the items of his own ender chest. The menu closes when the
     * minecart moves away or when it is removed.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void twoPlayersSeeTheirOwnEnderChests(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        EnderChestMinecart cart = cartOnRail(helper, 4);
        ServerPlayer first = TrainGameTests.survivalPlayer(helper, new Vec3(4.5, 1.0, BENCH_Z + 1.5));
        ServerPlayer second = TrainGameTests.survivalPlayer(helper, new Vec3(5.5, 1.0, BENCH_Z + 1.5));
        first.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        second.getEnderChestInventory().setItem(0, new ItemStack(Items.EMERALD, 5));
        ChestMenu firstMenu = open(first, cart);
        ChestMenu secondMenu = open(second, cart);
        helper.assertTrue(firstMenu.getContainer() == first.getEnderChestInventory()
                && firstMenu.getSlot(0).getItem().is(Items.DIAMOND), "The first player does not see his ender chest");
        helper.assertTrue(secondMenu.getContainer() == second.getEnderChestInventory()
                && secondMenu.getSlot(0).getItem().is(Items.EMERALD), "The second player does not see his ender chest");

        Vec3 start = cart.position();
        cart.setPos(start.add(12.0, 0.0, 0.0));
        helper.assertFalse(firstMenu.stillValid(first), "The menu stays open when the minecart is far");
        cart.setPos(start);
        helper.assertTrue(firstMenu.stillValid(first), "The menu closes when the minecart is near");
        cart.discard();
        helper.assertFalse(secondMenu.stillValid(second), "The menu stays open when the minecart is removed");
        first.closeContainer();
        second.closeContainer();
        helper.assertTrue(first.getEnderChestInventory().getItem(0).getCount() == 3
                && second.getEnderChestInventory().getItem(0).getCount() == 5, "The ender chests lose items");
        helper.succeed();
    }

    /**
     * A hopper above the minecart cannot put items into it, and a hopper below the rail cannot take items from it.
     * A chest minecart with the same hoppers is the reference: the test waits until its hoppers move the items.
     */
    @GameTest(template = "bench", timeoutTicks = 300, batch = "balance_rails")
    public static void hoppersHaveNoAccess(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        EnderChestMinecart cart = cartOnRail(helper, 4);
        helper.assertFalse(cart instanceof Container, "The minecart is a container");
        helper.assertTrue(cart.getCapability(Capabilities.ItemHandler.ENTITY, null) == null
                && cart.getCapability(Capabilities.ItemHandler.ENTITY_AUTOMATION, Direction.UP) == null,
                "The minecart has an item handler");
        // A player with items in the ender chest opens the minecart: the hoppers must not see these items.
        ServerPlayer player = TrainGameTests.survivalPlayer(helper, new Vec3(4.5, 1.0, BENCH_Z + 1.5));
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
        open(player, cart);
        hoppers(helper, 4);

        TrainGameTests.rail(helper, 8, BENCH_Z, RailShape.EAST_WEST);
        TrainGameTests.fixRails(helper);
        MinecartChest reference = helper.spawn(EntityType.CHEST_MINECART, new Vec3(8.5, CART_Y, BENCH_Z + 0.5));
        reference.setItem(5, new ItemStack(Items.COBBLESTONE));
        hoppers(helper, 8);
        helper.succeedWhen(() -> {
            helper.assertTrue(count(helper, 8, Items.COBBLESTONE) == 1 && apples(helper, 8) == 0,
                    "The hoppers of the chest minecart do not move the items yet");
            helper.assertTrue(apples(helper, 4) == 1, "The hopper above puts the apple into the minecart");
            helper.assertTrue(count(helper, 4, Items.DIAMOND) == 0
                    && player.getEnderChestInventory().getItem(0).getCount() == 3,
                    "The hopper below takes items from the ender chest");
            player.closeContainer();
            reference.clearContent();
            reference.discard();
            cart.discard();
        });
    }

    /** Damage destroys the minecart: it drops only its item, and the ender chest of the player keeps its items. */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void destroyedMinecartDropsOnlyItsItem(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        DamageSources sources = helper.getLevel().damageSources();
        Player player = helper.makeMockPlayer(GameType.SURVIVAL);
        player.getEnderChestInventory().setItem(0, new ItemStack(Items.DIAMOND, 64));
        EnderChestMinecart hit = helper.spawn(ModEntities.ENDER_CHEST_MINECART.get(), new Vec3(2.5, 1.0, 2.5));
        EnderChestMinecart exploded = helper.spawn(ModEntities.ENDER_CHEST_MINECART.get(), new Vec3(6.5, 1.0, 2.5));
        for (int i = 0; i < 2; i++) {
            // A second hit after the destruction must not drop more items.
            hit.hurt(sources.playerAttack(player), 100.0F);
            exploded.hurt(sources.explosion(null, null), 100.0F);
        }
        helper.succeedWhen(() -> {
            List<ItemEntity> drops = helper.getLevel().getEntitiesOfClass(ItemEntity.class,
                    helper.getBounds().inflate(2.0));
            helper.assertTrue(hit.isRemoved() && exploded.isRemoved(), "The minecarts are not removed");
            helper.assertTrue(drops.size() == 2 && drops.stream().allMatch(drop -> drop.getItem()
                    .is(ModItems.ENDER_CHEST_MINECART.get()) && drop.getItem().getCount() == 1),
                    "The minecarts drop " + drops.stream().map(ItemEntity::getItem).toList());
            helper.assertTrue(player.getEnderChestInventory().getItem(0).getCount() == 64,
                    "The ender chest of the player loses items");
            drops.forEach(ItemEntity::discard);
        });
    }

    /** A furnace locomotive pulls an ender chest minecart as a linked wagon. The wagon has no cargo. */
    @GameTest(template = "bench", timeoutTicks = 400, batch = "balance_rails")
    public static void locomotivePullsTheMinecart(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
        Locomotive locomotive = helper.spawn(ModEntities.FURNACE_LOCOMOTIVE.get(), new Vec3(4.5, CART_Y, BENCH_Z + 0.5));
        locomotive.setFuel(2000 * locomotive.stats().fuelPerBlock());
        EnderChestMinecart wagon = helper.spawn(ModEntities.ENDER_CHEST_MINECART.get(),
                new Vec3(3.0, CART_Y, BENCH_Z + 0.5));
        List<AbstractMinecart> train = List.of(locomotive, wagon);
        TrainGameTests.assertLinked(helper, locomotive, wagon);
        LocomotiveGameTests.start(helper, locomotive);
        double finish = helper.absoluteVec(new Vec3(12.0, 0, 0)).x;
        helper.succeedWhen(() -> {
            helper.assertTrue(wagon.getX() > finish, "The wagon is at x " + wagon.getX());
            helper.assertTrue(CartLinks.get(locomotive).back().equals(Optional.of(wagon.getUUID())),
                    "The link breaks");
            LocomotiveGameTests.clear(helper, train);
        });
    }

    /** An ender chest minecart on a rail at x. */
    private static EnderChestMinecart cartOnRail(GameTestHelper helper, int x) {
        TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        TrainGameTests.fixRails(helper);
        return helper.spawn(ModEntities.ENDER_CHEST_MINECART.get(), new Vec3(x + 0.5, CART_Y, BENCH_Z + 0.5));
    }

    /** The player opens the minecart with an empty hand. Returns the open menu. */
    private static ChestMenu open(ServerPlayer player, EnderChestMinecart cart) {
        player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        player.interactOn(cart, InteractionHand.MAIN_HAND);
        if (!(player.containerMenu instanceof ChestMenu menu)) {
            throw new GameTestAssertException("The minecart does not open a chest menu: " + player.containerMenu);
        }
        return menu;
    }

    /**
     * A hopper with an apple above the rail at x, and a hopper below the rail that moves the items to a chest.
     */
    private static void hoppers(GameTestHelper helper, int x) {
        BlockPos above = new BlockPos(x, 2, BENCH_Z);
        helper.setBlock(above, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        if (helper.getBlockEntity(above) instanceof HopperBlockEntity hopper) {
            hopper.setItem(0, new ItemStack(Items.APPLE));
        }
        BlockPos below = new BlockPos(x, 0, BENCH_Z);
        helper.setBlock(below, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.NORTH));
        helper.setBlock(below.north(), Blocks.CHEST);
    }

    /** The count of apples in the hopper above the rail at x. */
    private static int apples(GameTestHelper helper, int x) {
        return helper.getBlockEntity(new BlockPos(x, 2, BENCH_Z)) instanceof HopperBlockEntity hopper
                ? hopper.countItem(Items.APPLE) : -1;
    }

    /** The count of an item in the chest after the hopper below the rail at x. */
    private static int count(GameTestHelper helper, int x, Item item) {
        return helper.getBlockEntity(new BlockPos(x, 0, BENCH_Z - 1)) instanceof Container chest
                ? chest.countItem(item) : -1;
    }
}
