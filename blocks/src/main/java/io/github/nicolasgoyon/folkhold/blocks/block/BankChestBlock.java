package io.github.nicolasgoyon.folkhold.blocks.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.EntityFeatureBlock;
import net.minecraft.world.level.block.BaseEntityBlock;

/**
 * Bank chest: a chest of the village bank. It resists explosions, as obsidian.
 *
 * <p>The {@link BlockFeature} of the block gives its block entity (the items of the chest) and its actions, for
 * example the pillage.
 */
public class BankChestBlock extends EntityFeatureBlock {
    public static final MapCodec<BankChestBlock> CODEC = simpleCodec(BankChestBlock::new);

    public BankChestBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
