package io.github.nicolasgoyon.folkhold.blocks.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The sign stand of the building of a job: the stand of a {@link BuildingSignBlock} with an empty field on the plaque.
 * The {@link JobSignBlockEntity} keeps the job id and the icon item. The field shows the icon item on the two faces of
 * the plaque. One block serves all the jobs.
 *
 * <p>The library does not read the job id. The world mod reads it.
 */
public class JobSignBlock extends BuildingSignBlock implements EntityBlock {
    public static final MapCodec<JobSignBlock> CODEC = simpleCodec(JobSignBlock::new);

    public JobSignBlock(Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JobSignBlockEntity(pos, state);
    }
}
