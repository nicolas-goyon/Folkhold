package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks of a pier (see {@link PierPiece}).
 *
 * @param deck the blocks of the deck
 * @param post the blocks of the posts under the deck, down to the floor of the water
 * @param rail the blocks of the rails on the two sides of the deck
 */
public record PierBlocks(BlockState deck, BlockState post, BlockState rail) {
    public static final Codec<PierBlocks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("deck").forGetter(PierBlocks::deck),
            BlockState.CODEC.fieldOf("post").forGetter(PierBlocks::post),
            BlockState.CODEC.fieldOf("rail").forGetter(PierBlocks::rail))
            .apply(instance, PierBlocks::new));

    /** The blocks as NBT data, for the save of a structure piece. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Deck", NbtUtils.writeBlockState(deck));
        tag.put("Post", NbtUtils.writeBlockState(post));
        tag.put("Rail", NbtUtils.writeBlockState(rail));
        return tag;
    }

    /** The blocks from NBT data (see {@link #save()}). */
    public static PierBlocks load(HolderGetter<Block> blocks, CompoundTag tag) {
        return new PierBlocks(NbtUtils.readBlockState(blocks, tag.getCompound("Deck")),
                NbtUtils.readBlockState(blocks, tag.getCompound("Post")),
                NbtUtils.readBlockState(blocks, tag.getCompound("Rail")));
    }
}
