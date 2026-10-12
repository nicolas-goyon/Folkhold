package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.Item;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.DeferredSpawnEggItem;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Items of the world mod. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FolkholdWorld.MOD_ID);

    public static final DeferredItem<DeferredSpawnEggItem> GUARD_SPAWN_EGG = ITEMS.register("guard_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.GUARD, 0x3C5A9A, 0xC0C0C0, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> ARCHER_SPAWN_EGG = ITEMS.register("archer_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.ARCHER, 0x3C5A9A, 0x7A5230, new Item.Properties()));

    public static final DeferredItem<DeferredSpawnEggItem> CAPTAIN_SPAWN_EGG = ITEMS.register("captain_spawn_egg",
            () -> new DeferredSpawnEggItem(ModEntities.CAPTAIN, 0x3C5A9A, 0xE0C060, new Item.Properties()));

    public static final DeferredItem<BlockItem> VILLAGE_HEART = ITEMS.registerSimpleBlockItem(ModBlocks.VILLAGE_HEART);

    public static final DeferredItem<BlockItem> WATCH_POST = ITEMS.registerSimpleBlockItem(ModBlocks.WATCH_POST);

    public static final DeferredItem<BlockItem> BANK_CHEST = ITEMS.registerSimpleBlockItem(ModBlocks.BANK_CHEST);

    public static final DeferredItem<BlockItem> BANK_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.BANK_WALL);

    private ModItems() {
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.SPAWN_EGGS) {
            event.accept(GUARD_SPAWN_EGG.get());
            event.accept(ARCHER_SPAWN_EGG.get());
            event.accept(CAPTAIN_SPAWN_EGG.get());
        } else if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(VILLAGE_HEART.get());
            event.accept(WATCH_POST.get());
            event.accept(BANK_CHEST.get());
            event.accept(BANK_WALL.get());
        }
    }
}
