package io.github.nicolasgoyon.folkhold.world.registry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.github.nicolasgoyon.folkhold.blocks.FolkholdBlocks;
import io.github.nicolasgoyon.folkhold.blocks.block.QuestBoardBlock;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeatures;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import io.github.nicolasgoyon.folkhold.world.block.BankChestFeature;
import io.github.nicolasgoyon.folkhold.world.block.QuestBoardFeature;
import io.github.nicolasgoyon.folkhold.world.block.VillageHeartFeature;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;

class ModBlockFeaturesTest {
    /** The ids of the world mod for the blocks and the items of the library resolve to the library. */
    @Test
    void worldIdsResolveToTheLibrary() {
        for (String block : ModBlockFeatures.aliasedBlocks()) {
            ResourceLocation world = ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, block);
            ResourceLocation library = ResourceLocation.fromNamespaceAndPath(FolkholdBlocks.MOD_ID, block);

            assertTrue(BuiltInRegistries.BLOCK.containsKey(library), "No block " + library);
            assertSame(BuiltInRegistries.BLOCK.get(library), BuiltInRegistries.BLOCK.get(world), block);
            assertSame(BuiltInRegistries.ITEM.get(library), BuiltInRegistries.ITEM.get(world), block);
        }
    }

    /** A block state with the id of the world mod, as in a chunk of a saved world, reads as the library block. */
    @Test
    void stateWithTheWorldIdReadsAsTheLibraryBlock() {
        CompoundTag properties = new CompoundTag();
        properties.putString("papers", "3");
        CompoundTag tag = new CompoundTag();
        tag.putString("Name", FolkholdWorld.MOD_ID + ":quest_board");
        tag.put("Properties", properties);

        BlockState state = BlockState.CODEC.parse(NbtOps.INSTANCE, tag).getOrThrow();

        assertSame(ModBlocks.QUEST_BOARD.get(), state.getBlock());
        assertEquals(3, state.getValue(QuestBoardBlock.PAPERS));
    }

    /** The world mod gives the features of the village heart, the bank chest and the quest board. */
    @Test
    void worldModGivesTheFeatures() {
        assertInstanceOf(VillageHeartFeature.class, BlockFeatures.of(ModBlocks.VILLAGE_HEART.get()));
        assertInstanceOf(BankChestFeature.class, BlockFeatures.of(ModBlocks.BANK_CHEST.get()));
        assertInstanceOf(QuestBoardFeature.class, BlockFeatures.of(ModBlocks.QUEST_BOARD.get()));
        assertSame(BlockFeature.NONE, BlockFeatures.of(ModBlocks.WATCH_POST.get()));
    }
}
