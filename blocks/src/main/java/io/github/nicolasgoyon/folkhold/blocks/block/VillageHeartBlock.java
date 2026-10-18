package io.github.nicolasgoyon.folkhold.blocks.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.EntityFeatureBlock;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Village heart: the block at the center of a faction village. It resists explosions and is hard to break, as
 * obsidian.
 *
 * <p>The {@link BlockFeature} of the block gives its block entity (the state of the village) and its actions, for
 * example the screen of the village and the destruction of the village.
 */
public class VillageHeartBlock extends EntityFeatureBlock {
    public static final MapCodec<VillageHeartBlock> CODEC = simpleCodec(VillageHeartBlock::new);

    public VillageHeartBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
