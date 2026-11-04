package io.github.nicolasgoyon.folkhold.balance.registry;

import io.github.nicolasgoyon.folkhold.balance.FolkholdBalance;
import io.github.nicolasgoyon.folkhold.balance.rail.LocomotiveItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of the balance mod. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FolkholdBalance.MOD_ID);

    public static final DeferredItem<LocomotiveItem> FURNACE_LOCOMOTIVE = ITEMS.register("furnace_locomotive",
            () -> new LocomotiveItem(ModEntities.FURNACE_LOCOMOTIVE, new Item.Properties().stacksTo(1)));

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::onBuildCreativeTab);
    }

    private static void onBuildCreativeTab(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.TOOLS_AND_UTILITIES) {
            event.accept(FURNACE_LOCOMOTIVE);
        }
    }
}
