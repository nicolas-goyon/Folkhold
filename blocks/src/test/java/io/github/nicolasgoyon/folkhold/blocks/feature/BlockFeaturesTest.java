package io.github.nicolasgoyon.folkhold.blocks.feature;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.github.nicolasgoyon.folkhold.blocks.block.VillageHeartBlock;
import io.github.nicolasgoyon.folkhold.blocks.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

class BlockFeaturesTest {
    /** With no feature, a block of the library has no block entity and no ticker. */
    @Test
    void blockWithNoFeatureHasNoBlockEntity() {
        VillageHeartBlock heart = ModBlocks.VILLAGE_HEART.get();

        assertSame(BlockFeature.NONE, BlockFeatures.of(heart));
        assertNull(heart.newBlockEntity(BlockPos.ZERO, heart.defaultBlockState()));
    }

    /** A registered feature is the feature of the block. A second feature for the same block is an error. */
    @Test
    void registeredFeatureIsTheFeatureOfTheBlock() {
        BlockFeature feature = new BlockFeature() {
        };

        BlockFeatures.register(ModBlocks.BANK_WALL.getId(), feature);

        assertSame(feature, BlockFeatures.of(ModBlocks.BANK_WALL.get()));
        assertThrows(IllegalStateException.class, () -> BlockFeatures.register(ModBlocks.BANK_WALL.getId(),
                new BlockFeature() {
                }));
    }
}
