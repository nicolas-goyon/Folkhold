package io.github.nicolasgoyon.folkhold.blocks.registry;

import io.github.nicolasgoyon.folkhold.blocks.FolkholdBlocks;
import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTabs;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.event.BuildCreativeModeTabContentsEvent;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/** The items of the blocks of the library. The creative tab "Functional Blocks" shows them. */
public final class ModItems {
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FolkholdBlocks.MOD_ID);

    public static final DeferredItem<BlockItem> VILLAGE_HEART = ITEMS.registerSimpleBlockItem(ModBlocks.VILLAGE_HEART);

    public static final DeferredItem<BlockItem> WATCH_POST = ITEMS.registerSimpleBlockItem(ModBlocks.WATCH_POST);

    public static final DeferredItem<BlockItem> BANK_CHEST = ITEMS.registerSimpleBlockItem(ModBlocks.BANK_CHEST);

    public static final DeferredItem<BlockItem> BANK_WALL = ITEMS.registerSimpleBlockItem(ModBlocks.BANK_WALL);

    public static final DeferredItem<BlockItem> QUEST_BOARD = ITEMS.registerSimpleBlockItem(ModBlocks.QUEST_BOARD);

    /** The items of the building signs, one for each kind. */
    public static final Map<BuildingSignKind, DeferredItem<BlockItem>> BUILDING_SIGNS = buildingSigns();

    private ModItems() {
    }

    private static Map<BuildingSignKind, DeferredItem<BlockItem>> buildingSigns() {
        Map<BuildingSignKind, DeferredItem<BlockItem>> items = new EnumMap<>(BuildingSignKind.class);
        ModBlocks.BUILDING_SIGNS.forEach((kind, block) -> items.put(kind, ITEMS.registerSimpleBlockItem(block)));
        return Collections.unmodifiableMap(items);
    }

    public static void register(IEventBus modEventBus) {
        ITEMS.register(modEventBus);
        modEventBus.addListener(ModItems::onBuildCreativeTabs);
    }

    private static void onBuildCreativeTabs(BuildCreativeModeTabContentsEvent event) {
        if (event.getTabKey() == CreativeModeTabs.FUNCTIONAL_BLOCKS) {
            event.accept(VILLAGE_HEART.get());
            event.accept(WATCH_POST.get());
            event.accept(BANK_CHEST.get());
            event.accept(BANK_WALL.get());
            event.accept(QUEST_BOARD.get());
            BUILDING_SIGNS.values().forEach(item -> event.accept(item.get()));
        }
    }
}
