package io.github.nicolasgoyon.folkhold.balance.rail;

import java.util.List;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * The item of the shulker box minecart. It keeps the items ({@link DataComponents#CONTAINER}) and the color
 * ({@link DataComponents#BASE_COLOR}) of the minecart. As a shulker box item, it cannot go into a shulker box or a
 * bundle, and a destroyed item entity drops its items. The tooltip shows the items, as the shulker box item.
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

    /** The same value as {@link #canFitInsideContainerItems(ItemStack)}, for mods that call this method. */
    @Override
    @SuppressWarnings("deprecation")
    public boolean canFitInsideContainerItems() {
        return false;
    }

    /** The first 5 stacks of the items, then the number of the other stacks, as the shulker box item. */
    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, tooltip, flag);
        int shown = 0;
        int total = 0;
        for (ItemStack content : stack.getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY)
                .nonEmptyItems()) {
            total++;
            if (shown < 5) {
                shown++;
                tooltip.add(Component.translatable("container.shulkerBox.itemCount", content.getHoverName(),
                        content.getCount()));
            }
        }
        if (total > shown) {
            tooltip.add(Component.translatable("container.shulkerBox.more", total - shown)
                    .withStyle(ChatFormatting.ITALIC));
        }
    }

    @Override
    public void onDestroyed(ItemEntity entity, DamageSource source) {
        ItemContainerContents contents = entity.getItem().set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        if (contents != null) {
            ItemUtils.onContainerDestroyed(entity, contents.nonEmptyItemsCopy());
        }
    }
}
