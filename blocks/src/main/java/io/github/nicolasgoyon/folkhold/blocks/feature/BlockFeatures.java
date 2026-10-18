package io.github.nicolasgoyon.folkhold.blocks.feature;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

/**
 * The registry of the features of the blocks of the library (see {@link BlockFeature}).
 *
 * <ul>
 *   <li>A mod registers the features in its constructor: the mods load in parallel, so the registry accepts calls
 *       from several threads.</li>
 *   <li>A block has one feature at most. A second registration for the same block is an error.</li>
 * </ul>
 */
public final class BlockFeatures {
    private static final Map<ResourceLocation, BlockFeature> FEATURES = new ConcurrentHashMap<>();

    private BlockFeatures() {
    }

    /**
     * Registers the feature of a block.
     *
     * @param blockId the id of the block, for example {@code folkhold_blocks:village_heart}
     * @param feature the feature
     * @throws IllegalStateException if the block has a feature
     */
    public static void register(ResourceLocation blockId, BlockFeature feature) {
        Objects.requireNonNull(feature, "feature");
        BlockFeature existing = FEATURES.putIfAbsent(blockId, feature);
        if (existing != null) {
            throw new IllegalStateException("The block " + blockId + " has a feature");
        }
    }

    /**
     * The feature of a block.
     *
     * @param block the block
     * @return the feature, or {@link BlockFeature#NONE} if no mod registered a feature for the block
     */
    public static BlockFeature of(Block block) {
        return FEATURES.getOrDefault(BuiltInRegistries.BLOCK.getKey(block), BlockFeature.NONE);
    }
}
