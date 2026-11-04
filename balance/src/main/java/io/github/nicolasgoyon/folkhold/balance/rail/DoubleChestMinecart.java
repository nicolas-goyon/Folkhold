package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * A minecart with a double chest: 54 slots. See {@link StorageMinecart} for the rules of all the storage minecarts.
 */
public class DoubleChestMinecart extends StorageMinecart {
    /** The number of slots: 6 rows of 9 slots. */
    public static final int SIZE = 54;

    public DoubleChestMinecart(EntityType<? extends DoubleChestMinecart> type, Level level) {
        super(type, level);
    }

    @Override
    public int getContainerSize() {
        return SIZE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.DOUBLE_CHEST_MINECART.get();
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, Direction.NORTH);
    }

    /** The client draws a second chest above the bounding box: the culling box is 1 block taller. */
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().expandTowards(0.0, 1.0, 0.0);
    }

    @Override
    protected AbstractContainerMenu createMenu(int id, Inventory inventory) {
        return ChestMenu.sixRows(id, inventory, this);
    }

}
