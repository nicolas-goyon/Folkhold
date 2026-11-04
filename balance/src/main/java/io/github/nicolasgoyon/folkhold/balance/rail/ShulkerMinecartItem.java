package io.github.nicolasgoyon.folkhold.balance.rail;

import java.util.function.Supplier;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The item of the shulker box minecart. It keeps the items ({@link DataComponents#CONTAINER}) and the color
 * ({@link DataComponents#BASE_COLOR}) of the minecart. As a shulker box item, it cannot go into a shulker box or a
 * bundle, and a destroyed item entity drops its items.
 */
public class ShulkerMinecartItem extends CartItem {
    public ShulkerMinecartItem(Supplier<? extends EntityType<ShulkerMinecart>> type, Properties properties) {
        super(type, properties);
    }

    @Override
    protected void configure(AbstractMinecart cart, ItemStack stack) {
        if (cart instanceof ShulkerMinecart shulker) {
            shulker.loadFromItem(stack);
        }
    }

    @Override
    public boolean canFitInsideContainerItems(ItemStack stack) {
        return false;
    }

    @Override
    public void onDestroyed(ItemEntity entity, DamageSource source) {
        ItemContainerContents contents = entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != null) {
            ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemsCopy());
        }
    }
}
