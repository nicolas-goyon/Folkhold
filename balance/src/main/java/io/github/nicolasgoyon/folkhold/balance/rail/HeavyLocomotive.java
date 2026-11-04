package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BlastFurnaceBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The heavy locomotive. It burns blocks of coal. It has more pulling force and more speed than the furnace locomotive.
 */
public class HeavyLocomotive extends Locomotive {

    public HeavyLocomotive(EntityType<? extends HeavyLocomotive> type, Level level) {
        super(type, level);
    }

    @Override
    public LocomotiveStats stats() {
        return BalanceConfig.heavyLocomotive();
    }

    @Override
    protected int fuelValue(ItemStack stack) {
        return stack.is(Items.COAL_BLOCK) ? stack.getBurnTime(RecipeType.SMELTING) : 0;
    }

    @Override
    protected Component fuelName() {
        return Component.translatable("folkhold_balance.locomotive.heavy_fuel");
    }

    @Override
    public Type getMinecartType() {
        return Type.FURNACE;
    }

    @Override
    protected Item getDropItem() {
        return ModItems.HEAVY_LOCOMOTIVE.get();
    }

    @Override
    public BlockState getDefaultDisplayBlockState() {
        return Blocks.BLAST_FURNACE.defaultBlockState().setValue(BlastFurnaceBlock.FACING, Direction.NORTH)
                .setValue(BlastFurnaceBlock.LIT, isBurning());
    }
}
