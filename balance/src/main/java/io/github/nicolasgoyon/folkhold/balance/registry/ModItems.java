package io.github.nicolasgoyon.folkhold.balance.registry;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.CartItem;
import io.github.nicolasgoyon.folkhold.balance.rail.ShulkerMinecartItem;
import io.github.nicolasgoyon.folkhold.balance.rail.ShulkerMinecartRecipe;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.SimpleCraftingRecipeSerializer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of the balance mod. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FolkholdBalance.MOD_ID);

    public static final DeferredItem<CartItem> FURNACE_LOCOMOTIVE = ITEMS.register("furnace_locomotive",
            () -> new CartItem(ModEntities.FURNACE_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CartItem> HEAVY_LOCOMOTIVE = ITEMS.register("heavy_locomotive",
            () -> new CartItem(ModEntities.HEAVY_LOCOMOTIVE, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CartItem> DOUBLE_CHEST_MINECART = ITEMS.register("double_chest_minecart",
            () -> new CartItem(ModEntities.DOUBLE_CHEST_MINECART, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<CartItem> ENDER_CHEST_MINECART = ITEMS.register("ender_chest_minecart",
            () -> new CartItem(ModEntities.ENDER_CHEST_MINECART, new Item.Properties().stacksTo(1)));
    public static final DeferredItem<ShulkerMinecartItem> SHULKER_BOX_MINECART = ITEMS.register("shulker_box_minecart",
            () -> new ShulkerMinecartItem(ModEntities.SHULKER_BOX_MINECART, new Item.Properties().stacksTo(1)));

    private static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(Registries.RECIPE_SERIALIZER, FolkholdBalance.MOD_ID);

    /** The special recipe of the shulker box minecart: it keeps the items and the color of the shulker box. */
    public static final DeferredHolder<RecipeSerializer<?>, SimpleCraftingRecipeSerializer<ShulkerMinecartRecipe>>
            SHULKER_BOX_MINECART_RECIPE = RECIPE_SERIALIZERS.register("shulker_box_minecart",
                    () -> new SimpleCraftingRecipeSerializer<>(ShulkerMinecartRecipe::new));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        RECIPE_SERIALIZERS.register(modEventBus);
        modEventBus.addListener(ModItems::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FURNACE_LOCOMOTIVE);
            event.accept(HEAVY_LOCOMOTIVE);
            event.accept(DOUBLE_CHEST_MINECART);
            event.accept(SHULKER_BOX_MINECART);
            for (DyeColor color : DyeColor.values()) {
                ItemStack stack = new ItemStack(SHULKER_BOX_MINECART.get());
                stack.set(DataComponents.BASE_COLOR, color);
                event.accept(stack);
            }
            event.accept(ENDER_CHEST_MINECART);
        }
    }
}
