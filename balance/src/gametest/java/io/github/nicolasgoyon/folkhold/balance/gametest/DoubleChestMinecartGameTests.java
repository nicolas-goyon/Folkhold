package io.github.nicolasgoyon.folkhold.balance.gametest;

import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.BENCH_Z;
import static io.github.nicolasgoyon.folkhold.balance.gametest.TrainGameTests.CART_Y;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.CartLinks;
import io.github.nicolasgoyon.folkhold.balance.rail.DoubleChestMinecart;
import io.github.nicolasgoyon.folkhold.balance.rail.Locomotive;
import io.github.nicolasgoyon.folkhold.balance.registry.ModEntities;
import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Container;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HopperBlock;
import net.minecraft.world.level.block.entity.HopperBlockEntity;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/** In-game tests for the double chest minecart, on the template {@code folkhold_balance:bench}. */
@GameTestHolder(FolkholdBalance.MOD_ID)
@PrefixGameTestTemplate(false)
public final class DoubleChestMinecartGameTests {
    private static final int SIZE = DoubleChestMinecart.SIZE;

    private DoubleChestMinecartGameTests() {
    }

    /** A hopper above the minecart fills its 54 slots. A minecart does not stack, so 1 minecart is a full stack. */
    @GameTest(template = "bench", timeoutTicks = 700, batch = "balance_rails")
    public static void hopperFillsTheMinecart(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        DoubleChestMinecart cart = cartOnRail(helper);
        BlockPos hopper = new BlockPos(4, 2, BENCH_Z);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.DOWN));
        AtomicInteger given = new AtomicInteger();
        helper.onEachTick(() -> {
            if (helper.getBlockEntity(hopper) instanceof HopperBlockEntity block && block.isEmpty()
                    && given.get() < SIZE + 1) {
                block.setItem(0, new ItemStack(Items.MINECART));
                given.incrementAndGet();
            }
        });
        helper.succeedWhen(() -> {
            for (int slot = 0; slot < SIZE; slot++) {
                ItemStack stack = cart.getItem(slot);
                helper.assertTrue(stack.is(Items.MINECART) && stack.getCount() == stack.getMaxStackSize(),
                        "The slot " + slot + " is not full");
            }
            helper.assertTrue(given.get() == SIZE + 1, "The hopper gives " + given.get() + " minecarts");
            helper.assertTrue(helper.getBlockEntity(hopper) instanceof HopperBlockEntity block && !block.isEmpty(),
                    "The full minecart takes a 55th minecart");
            cart.clearContent();
            cart.discard();
        });
    }

    /** A hopper below the rail takes all the items of the minecart. The hopper points to stone and keeps them. */
    @GameTest(template = "bench", timeoutTicks = 700, batch = "balance_rails")
    public static void hopperEmptiesTheMinecart(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        BlockPos hopper = new BlockPos(4, 0, BENCH_Z);
        helper.setBlock(hopper, Blocks.HOPPER.defaultBlockState().setValue(HopperBlock.FACING, Direction.EAST));
        DoubleChestMinecart cart = cartOnRail(helper);
        fill(cart, Items.COBBLESTONE, 1);
        helper.succeedWhen(() -> {
            int inHopper = helper.getBlockEntity(hopper) instanceof Container block
                    ? block.countItem(Items.COBBLESTONE) : -1;
            helper.assertTrue(cart.isAlive(), "The minecart is removed");
            helper.assertTrue(inHopper == SIZE && cart.isEmpty(),
                    "The hopper has " + inHopper + " cobblestone, the minecart " + cart.countItem(Items.COBBLESTONE));
            cart.discard();
        });
    }

    /**
     * A destroyed minecart drops its item and its 54 stacks one time: an explosion, then a second hit after the
     * destruction. A minecart removed with /kill drops its stacks but not its item, as the vanilla chest minecart. A
     * loot table from a command does not stay on the minecart.
     */
    @GameTest(template = "bench", timeoutTicks = 100, batch = "balance_rails")
    public static void destroyedMinecartDropsItemsOneTime(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        DoubleChestMinecart broken = helper.spawn(ModEntities.DOUBLE_CHEST_MINECART.get(), new Vec3(4.5, 1.0, 2.5));
        DoubleChestMinecart killed = helper.spawn(ModEntities.DOUBLE_CHEST_MINECART.get(), new Vec3(12.5, 1.0, 2.5));
        killed.setLootTable(BuiltInLootTables.ABANDONED_MINESHAFT, 1L);
        helper.assertTrue(killed.getLootTable() == null, "The minecart keeps a loot table");
        fill(broken, Items.COBBLESTONE, 64);
        fill(killed, Items.DIRT, 64);
        broken.hurt(helper.getLevel().damageSources().explosion(null, null), 100.0F);
        broken.hurt(helper.getLevel().damageSources().explosion(null, null), 100.0F);
        killed.kill();
        helper.runAtTickTime(5, () -> {
            helper.assertTrue(broken.isRemoved() && killed.isRemoved(), "A minecart is not removed");
            helper.assertTrue(count(helper, Items.COBBLESTONE) == SIZE * 64,
                    "The destroyed minecart drops " + count(helper, Items.COBBLESTONE) + " cobblestone");
            helper.assertTrue(count(helper, Items.DIRT) == SIZE * 64,
                    "The killed minecart drops " + count(helper, Items.DIRT) + " dirt");
            helper.assertTrue(count(helper, ModItems.DOUBLE_CHEST_MINECART.get()) == 1,
                    "The minecarts drop " + count(helper, ModItems.DOUBLE_CHEST_MINECART.get()) + " items");
            helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0))
                    .forEach(ItemEntity::discard);
            helper.succeed();
        });
    }

    /** A locomotive pulls a full double chest minecart. The minecart stays linked behind it. */
    @GameTest(template = "bench", timeoutTicks = 200, batch = "balance_rails")
    public static void locomotivePullsTheMinecart(GameTestHelper helper) {
        TrainGameTests.floor(helper, 24, 8);
        for (int x = 1; x < 23; x++) {
            TrainGameTests.rail(helper, x, BENCH_Z, RailShape.EAST_WEST);
        }
        TrainGameTests.fixRails(helper);
        Locomotive locomotive = helper.spawn(ModEntities.FURNACE_LOCOMOTIVE.get(), new Vec3(4.5, CART_Y, BENCH_Z + 0.5));
        locomotive.setFuel(2000 * locomotive.stats().fuelPerBlock());
        DoubleChestMinecart wagon = helper.spawn(ModEntities.DOUBLE_CHEST_MINECART.get(),
                new Vec3(3.0, CART_Y, BENCH_Z + 0.5));
        fill(wagon, Items.COBBLESTONE, 64);
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

    /** A double chest minecart on a rail at x = 4. */
    private static DoubleChestMinecart cartOnRail(GameTestHelper helper) {
        TrainGameTests.rail(helper, 4, BENCH_Z, RailShape.EAST_WEST);
        TrainGameTests.fixRails(helper);
        return helper.spawn(ModEntities.DOUBLE_CHEST_MINECART.get(), new Vec3(4.5, CART_Y, BENCH_Z + 0.5));
    }

    private static void fill(DoubleChestMinecart cart, Item item, int count) {
        for (int slot = 0; slot < SIZE; slot++) {
            cart.setItem(slot, new ItemStack(item, count));
        }
    }

    /** The count of an item on the ground in the test area. */
    private static int count(GameTestHelper helper, Item item) {
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, helper.getBounds().inflate(2.0),
                entity -> entity.getItem().is(item)).stream().mapToInt(entity -> entity.getItem().getCount()).sum();
    }
}
