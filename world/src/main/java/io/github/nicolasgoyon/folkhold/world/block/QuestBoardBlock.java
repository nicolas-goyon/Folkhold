package io.github.nicolasgoyon.folkhold.world.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The quest board of a faction village: two posts, a small roof and a board with papers. A click opens the quests
 * of the village near the board (see {@link QuestBoards}). The board faces the {@link #FACING} direction.
 */
public class QuestBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<QuestBoardBlock> CODEC = simpleCodec(QuestBoardBlock::new);

    private static final VoxelShape SHAPE_NORTH_SOUTH = Block.box(0.0, 0.0, 6.0, 16.0, 22.0, 10.0);
    private static final VoxelShape SHAPE_EAST_WEST = Block.box(6.0, 0.0, 0.0, 10.0, 22.0, 16.0);

    public QuestBoardBlock(Properties properties) {
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

    /** The board faces the player who places it. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NORTH_SOUTH : SHAPE_EAST_WEST;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestBoards.openAt(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
