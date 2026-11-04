package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FurnaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/** The furnace locomotive. It burns the fuels of the furnace. */
public class FurnaceLocomotive extends Locomotive {

    public FurnaceLocomotive(EntityType<? extends FurnaceLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public LocomotiveStats stats() {
        return BalanceConfig.furnaceLocomotive();
    }

    @Override
    protected int fuelValue(ItemStack stack) {
        return stack.isEmpty() ? 0 : stack.getBurnTime(RecipeType.SMELTING);
    }

    @Override
    public Type getMinecartType() {
        return Type.FURNACE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.FURNACE_LOCOMOTIVE.get();
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.FURNACE.defaultBlockState().setValue(FurnaceBlock.FACING, Direction.NORTH)
                .setValue(FurnaceBlock.LIT, isBurning());
    }
}
