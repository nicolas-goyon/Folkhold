package io.github.nicolasgoyon.folkhold.world.village.structure;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The blocks of a palisade (see {@link PalisadePiece}).
 *
 * @param log        the logs of a column
 * @param base       the block under the logs of a column
 * @param point      the block on top of every second column
 * @param foundation the blocks under the base, down to the terrain
 */
public record PalisadeBlocks(BlockState log, BlockState base, BlockState point, BlockState foundation) {
    public static final Codec<PalisadeBlocks> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            BlockState.CODEC.fieldOf("log").forGetter(PalisadeBlocks::log),
            BlockState.CODEC.fieldOf("base").forGetter(PalisadeBlocks::base),
            BlockState.CODEC.fieldOf("point").forGetter(PalisadeBlocks::point),
            BlockState.CODEC.fieldOf("foundation").forGetter(PalisadeBlocks::foundation))
            .apply(instance, PalisadeBlocks::new));

    /** The blocks as NBT data, for the save of a structure piece. */
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("Log", NbtUtils.writeBlockState(log));
        tag.put("Base", NbtUtils.writeBlockState(base));
        tag.put("Point", NbtUtils.writeBlockState(point));
        tag.put("Foundation", NbtUtils.writeBlockState(foundation));
        return tag;
    }

    /** The blocks from NBT data (see {@link #save()}). */
    public static PalisadeBlocks load(HolderGetter<Block> blocks, CompoundTag tag) {
        return new PalisadeBlocks(NbtUtils.readBlockState(blocks, tag.getCompound("Log")),
                NbtUtils.readBlockState(blocks, tag.getCompound("Base")),
                NbtUtils.readBlockState(blocks, tag.getCompound("Point")),
                NbtUtils.readBlockState(blocks, tag.getCompound("Foundation")));
    }
}
