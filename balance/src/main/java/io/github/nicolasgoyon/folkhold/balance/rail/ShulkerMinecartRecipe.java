package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.registry.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingBookCategory;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CustomRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.ShulkerBoxBlock;

/**
 * The recipe of the shulker box minecart: a minecart and a shulker box, with no shape. The result keeps the items and
 * the color of the shulker box. A shulker box with a loot table or a lock does not match: the minecart cannot keep
 * the loot or the lock.
 */
public class ShulkerMinecartRecipe extends CustomRecipe {
    public ShulkerMinecartRecipe(CraftingBookCategory category) {
        super(category);
    }

    @Override
    public boolean matches(CraftingInput input, Level level) {
        int minecarts = 0;
        int boxes = 0;
        for (ItemStack stack : input.items()) {
            if (stack.is(Items.MINECART)) {
                minecarts++;
            } else if (isBox(stack)) {
                boxes++;
            } else if (!stack.isEmpty()) {
                return false;
            }
        }
        return minecarts == 1 && boxes == 1;
    }

    @Override
    public ItemStack assemble(CraftingInput input, HolderLookup.Provider registries) {
        ItemStack result = new ItemStack(ModItems.SHULKER_BOX_MINECART.get());
        for (ItemStack stack : input.items()) {
            if (isBox(stack)) {
                result.set(DataComponents.BASE_COLOR, ShulkerBoxBlock.getColorFromItem(stack.getItem()));
                result.set(DataComponents.CONTAINER, stack.get(DataComponents.CONTAINER));
            }
        }
        return result;
    }

    private static boolean isBox(ItemStack stack) {
        return Block.byItem(stack.getItem()) instanceof ShulkerBoxBlock
                && !stack.has(DataComponents.CONTAINER_LOOT) && !stack.has(DataComponents.LOCK);
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return width * height >= 2;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModItems.SHULKER_BOX_MINECART_RECIPE.get();
    }
}
