package io.github.nicolasgoyon.folkhold.world.registry;

import io.github.nicolasgoyon.folkhold.blocks.FolkholdBlocks;
import io.github.nicolasgoyon.folkhold.blocks.block.BuildingSignKind;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeatures;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestFeature;
import io.github.nicolasgoyon.folkhold.world.block.QuestBoardFeature;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartFeature;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The world mod and the blocks of the library {@code folkhold_blocks}.
 *
 * <ul>
 *   <li>The features of the village heart, the bank chest and the quest board.</li>
 *   <li>Aliases: the ids {@code folkhold_world:<block>} of these blocks and of their items resolve to the blocks and
 *       the items of the library. Saved worlds and structure files can contain these ids.</li>
 * </ul>
 */
public final class ModBlockFeatures {
    private static final DeferredRegister.Blocks BLOCK_ALIASES = DeferredRegister.createBlocks(FolkholdWorld.MOD_ID);
    private static final DeferredRegister.Items ITEM_ALIASES = DeferredRegister.createItems(FolkholdWorld.MOD_ID);

    private ModBlockFeatures() {
    }

    /** The block ids of the library that have an alias in the namespace of the world mod. */
    static List<String> aliasedBlocks() {
        List<String> blocks = new ArrayList<>(List.of("village_heart", "watch_post", "bank_chest", "bank_wall",
                "quest_board"));
        for (BuildingSignKind kind : BuildingSignKind.values()) {
            blocks.add(kind.blockId());
        }
        return blocks;
    }

    public static void register(IEventBus modEventBus) {
        BlockFeatures.register(ModBlocks.VILLAGE_HEART.getId(), new VillageHeartFeature());
        BlockFeatures.register(ModBlocks.BANK_CHEST.getId(), new BankChestFeature());
        BlockFeatures.register(ModBlocks.QUEST_BOARD.getId(), new QuestBoardFeature());
        for (String block : aliasedBlocks()) {
            ResourceLocation from = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, block);
            ResourceLocation to = ResourceLocation.fromNamespaceAndPath(FolkholdBlocks.MOD_ID, block);
            BLOCK_ALIASES.addAlias(from, to);
            ITEM_ALIASES.addAlias(from, to);
        }
        BLOCK_ALIASES.register(modEventBus);
        ITEM_ALIASES.register(modEventBus);
    }
}
