package io.github.nicolasgoyon.folkhold.blocks.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.HorizontalFeatureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A sign stand in front of a building of the village: two posts, a cross bar and a hanging plaque with the icon of
 * the building (see {@link BuildingSignKind}). The plaque faces the {@link #FACING} direction. The stand is
 * 1.5 blocks high, as a fence.
 *
 * <p>The path finders do not go through the stand. A mob cannot jump over it. The {@link BlockFeature} of the
 * block can add actions.
 */
public class BuildingSignBlock extends HorizontalFeatureBlock {
    public static final MapCodec<BuildingSignBlock> CODEC = simpleCodec(BuildingSignBlock::new);

    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(0.0, 0.0, 6.0, 16.0, 24.0, 10.0);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(6.0, 0.0, 0.0, 10.0, 24.0, 16.0);

    public BuildingSignBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    /** The plaque faces the player who places the sign. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    /**
     * Not passable. The collision box is not a full block, so the vanilla value lets the path go through the stand,
     * and a mob jumps against it again and again.
     */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }
}
