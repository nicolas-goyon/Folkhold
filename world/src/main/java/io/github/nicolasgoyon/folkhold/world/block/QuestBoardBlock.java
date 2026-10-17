package io.github.nicolasgoyon.folkhold.world.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The quest board of a faction village: a large notice board with two posts, a gable roof, papers and maps. A click
 * on any part opens the quests of the village near the board (see {@link QuestBoards}).
 *
 * <ul>
 *   <li>The board is 3 blocks wide and 3 blocks high: one block for each {@link #COLUMN} and {@link #ROW}. The
 *       board faces the {@link #FACING} direction. The columns go in the clockwise direction of {@link #FACING}:
 *       column 0 is on the right of a player who looks at the front of the board.</li>
 *   <li>The part in the middle (column 1, row 1) renders the complete model. The other parts give the collision and
 *       the click.</li>
 *   <li>The model shows one paper for each offer of the village of the board, up to {@link #MAX_PAPERS}
 *       ({@link #PAPERS} on the middle part). The middle part checks the offers every
 *       {@link #UPDATE_INTERVAL} ticks with a scheduled tick. The heart of the village starts the ticks of a board
 *       from the village structure (see {@link #startUpdates}).</li>
 *   <li>The item places the board with the clicked position as the bottom middle part (column 1, row 0), only if the
 *       9 positions are free.</li>
 *   <li>The removal of one part removes the complete board. The board drops one item.</li>
 *   <li>The path finders do not go through the parts.</li>
 * </ul>
 */
public class QuestBoardBlock extends HorizontalDirectionalBlock {
    public static final MapCodec<QuestBoardBlock> CODEC = simpleCodec(QuestBoardBlock::new);
    /** The column of the part, in the clockwise direction of {@link #FACING}. */
    public static final IntegerProperty COLUMN = IntegerProperty.create("column", 0, 2);
    /** The row of the part, from the bottom to the top. */
    public static final IntegerProperty ROW = IntegerProperty.create("row", 0, 2);
    /** The number of columns and of rows. */
    public static final int SIZE = 3;
    /** The maximum number of papers on the model. */
    public static final int MAX_PAPERS = 7;
    /** The number of papers on the model: the number of offers of the village, up to {@link #MAX_PAPERS}. */
    public static final IntegerProperty PAPERS = IntegerProperty.create("papers", 0, MAX_PAPERS);
    /** The time between two checks of the offers, in ticks. */
    public static final int UPDATE_INTERVAL = 40;

    private static final VoxelShape BOARD_NORTH_SOUTH = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 10.0);
    private static final VoxelShape BOARD_EAST_WEST = Block.box(6.0, 0.0, 0.0, 10.0, 16.0, 16.0);
    private static final VoxelShape ROOF_NORTH_SOUTH = Shapes.or(Block.box(0.0, 0.0, 6.0, 16.0, 3.0, 10.0),
            Block.box(0.0, 3.0, 1.0, 16.0, 9.0, 15.0));
    private static final VoxelShape ROOF_EAST_WEST = Shapes.or(Block.box(6.0, 0.0, 0.0, 10.0, 3.0, 16.0),
            Block.box(1.0, 3.0, 0.0, 15.0, 9.0, 16.0));

    public QuestBoardBlock(Properties properties) {
        super(properties);
        // A board with no part values renders the complete model.
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(COLUMN, 1)
                .setValue(ROW, 1).setValue(PAPERS, 0));
    }

    @Override
    protected MapCodec<? extends HorizontalDirectionalBlock> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, COLUMN, ROW, PAPERS);
    }

    /**
     * The position of a part.
     *
     * @param base   the position of the bottom middle part (column 1, row 0)
     * @param facing the direction of the board
     * @param column the column of the part
     * @param row    the row of the part
     * @return the position of the part
     */
    public static BlockPos partPos(BlockPos base, Direction facing, int column, int row) {
        return base.relative(facing.getClockWise(), column - 1).above(row);
    }

    /**
     * The position of the bottom middle part of the board of a part.
     *
     * @param pos   the position of the part
     * @param state the state of the part
     * @return the position of the bottom middle part (column 1, row 0)
     */
    public static BlockPos basePos(BlockPos pos, BlockState state) {
        return pos.relative(state.getValue(FACING).getClockWise(), 1 - state.getValue(COLUMN))
                .below(state.getValue(ROW));
    }

    /**
     * True if a board can go at a position: the 9 positions are free and in the height limits.
     *
     * @param level  the level
     * @param base   the position of the bottom middle part
     * @param facing the direction of the board
     * @return true if the board can go there
     */
    public static boolean canPlaceAt(Level level, BlockPos base, Direction facing) {
        for (int column = 0; column < SIZE; column++) {
            for (int row = 0; row < SIZE; row++) {
                BlockPos pos = partPos(base, facing, column, row);
                if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Places the 9 parts of a board. The method does not check the positions (see {@link #canPlaceAt}).
     *
     * @param level  the level
     * @param base   the position of the bottom middle part
     * @param facing the direction of the board
     * @param flags  the update flags of {@link Level#setBlock(BlockPos, BlockState, int)}
     */
    public void place(LevelAccessor level, BlockPos base, Direction facing, int flags) {
        for (int column = 0; column < SIZE; column++) {
            for (int row = 0; row < SIZE; row++) {
                level.setBlock(partPos(base, facing, column, row), part(facing, column, row), flags);
            }
        }
    }

    /** The state of one part, with no paper. */
    public BlockState part(Direction facing, int column, int row) {
        return defaultBlockState().setValue(FACING, facing).setValue(COLUMN, column).setValue(ROW, row);
    }

    /** True if the state is the part of a board at this column and row, with any number of papers. */
    public boolean isPart(BlockState state, Direction facing, int column, int row) {
        return state.is(this) && state.getValue(FACING) == facing && state.getValue(COLUMN) == column
                && state.getValue(ROW) == row;
    }

    /** True if the state is the part in the middle, which renders the complete model. */
    public static boolean isRenderPart(BlockState state) {
        return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 1;
    }

    /** True if the state is the bottom middle part, which drops the item. */
    public static boolean isBasePart(BlockState state) {
        return state.getValue(COLUMN) == 1 && state.getValue(ROW) == 0;
    }

    /** The board faces the player who places it. Its 9 positions must be free. */
    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockPos base = context.getClickedPos();
        Level level = context.getLevel();
        for (int column = 0; column < SIZE; column++) {
            for (int row = 0; row < SIZE; row++) {
                BlockPos pos = partPos(base, facing, column, row);
                if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced(context)) {
                    return null;
                }
            }
        }
        return part(facing, 1, 0);
    }

    /** The other 8 parts go into the level after the bottom middle part. */
    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer,
            ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide()) {
            place(level, basePos(pos, state), state.getValue(FACING), Block.UPDATE_ALL);
        }
    }

    /** A player breaks a part that is not the bottom middle part: the board drops its item there. */
    @Override
    public BlockState playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        if (!level.isClientSide() && !player.isCreative() && !isBasePart(state)) {
            Block.popResource(level, pos, new ItemStack(this));
        }
        return super.playerWillDestroy(level, pos, state, player);
    }

    /** When a part goes away, the other parts go away too, with no drops. */
    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean movedByPiston) {
        if (!level.isClientSide() && !state.is(newState.getBlock())) {
            Direction facing = state.getValue(FACING);
            BlockPos base = basePos(pos, state);
            for (int column = 0; column < SIZE; column++) {
                for (int row = 0; row < SIZE; row++) {
                    BlockPos other = partPos(base, facing, column, row);
                    if (!other.equals(pos) && isPart(level.getBlockState(other), facing, column, row)) {
                        level.setBlock(other, Blocks.AIR.defaultBlockState(),
                                Block.UPDATE_ALL | Block.UPDATE_SUPPRESS_DROPS);
                    }
                }
            }
        }
        super.onRemove(state, level, pos, newState, movedByPiston);
    }

    /** Not passable: the collision boxes are not full blocks, but a mob cannot walk or jump through them. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** The middle part of a new board starts its updates. */
    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!level.isClientSide() && isRenderPart(state) && !oldState.is(this)) {
            level.scheduleTick(pos, this, 1);
        }
    }

    /**
     * Starts the updates of the papers of a board, if its middle part has no scheduled tick. The heart calls it for
     * the boards of the village structure: a structure does not call {@link #onPlace}.
     *
     * @param level the level
     * @param pos   a position of a part of the board
     */
    public static void startUpdates(ServerLevel level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof QuestBoardBlock block)) {
            return;
        }
        BlockPos middle = partPos(basePos(pos, state), state.getValue(FACING), 1, 1);
        if (isRenderPartAt(level, middle) && !level.getBlockTicks().hasScheduledTick(middle, block)) {
            level.scheduleTick(middle, block, 1);
        }
    }

    private static boolean isRenderPartAt(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof QuestBoardBlock && isRenderPart(state);
    }

    /** The middle part shows the offers of the village of the board, then checks them again later. */
    @Override
    protected void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!isRenderPart(state)) {
            return;
        }
        int papers = papers(level, pos);
        if (state.getValue(PAPERS) != papers) {
            level.setBlock(pos, state.setValue(PAPERS, papers), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, this, UPDATE_INTERVAL);
    }

    /**
     * The number of papers of a board: the number of offers of the village near the board, up to
     * {@link #MAX_PAPERS}. A board out of an active village has no paper.
     *
     * @param level the level
     * @param pos   the position of the board
     * @return the number of papers
     */
    public static int papers(ServerLevel level, BlockPos pos) {
        return QuestBoards.villageAt(level, pos)
                .map(settlement -> QuestData.get(level.getServer()).quests().offers(settlement.id()).size())
                .map(count -> Math.min(MAX_PAPERS, count))
                .orElse(0);
    }

    @Override
    protected RenderShape getRenderShape(BlockState state) {
        return isRenderPart(state) ? RenderShape.MODEL : RenderShape.INVISIBLE;
    }

    /** A mirror changes the left and the right of the board. */
    @Override
    protected BlockState mirror(BlockState state, Mirror mirror) {
        if (mirror == Mirror.NONE) {
            return state;
        }
        return rotate(state, mirror.getRotation(state.getValue(FACING)))
                .setValue(COLUMN, SIZE - 1 - state.getValue(COLUMN));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        boolean northSouth = state.getValue(FACING).getAxis() == Direction.Axis.Z;
        if (state.getValue(ROW) == SIZE - 1) {
            return northSouth ? ROOF_NORTH_SOUTH : ROOF_EAST_WEST;
        }
        return northSouth ? BOARD_NORTH_SOUTH : BOARD_EAST_WEST;
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
