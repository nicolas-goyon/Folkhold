package io.github.nicolasgoyon.folkhold.world.block;

import io.github.nicolasgoyon.folkhold.blocks.block.QuestBoardBlock;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.world.quest.QuestBoards;
import io.github.nicolasgoyon.folkhold.world.quest.QuestData;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * The features of the quest board block of the library (see {@link BlockFeature}).
 *
 * <ul>
 *   <li>A click on any part opens the quests of the village near the board (see {@link QuestBoards}).</li>
 *   <li>The model shows one paper for each offer of the village of the board, up to
 *       {@link QuestBoardBlock#MAX_PAPERS}. The middle part checks the offers every {@link #UPDATE_INTERVAL} ticks
 *       with a scheduled tick. A new board starts these ticks. The heart of the village starts the ticks of a board
 *       from the village structure (see {@link #startUpdates}).</li>
 * </ul>
 */
public final class QuestBoardFeature implements BlockFeature {
    /** The time between two checks of the offers, in ticks. */
    public static final int UPDATE_INTERVAL = 40;

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player,
            BlockHitResult hitResult) {
        if (player instanceof ServerPlayer serverPlayer) {
            QuestBoards.openAt(serverPlayer, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    /** The middle part of a new board starts its updates. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        if (!level.isClientSide() && QuestBoardBlock.isRenderPart(state) && !oldState.is(state.getBlock())) {
            level.scheduleTick(pos, state.getBlock(), 1);
        }
    }

    /** The middle part shows the offers of the village of the board, then checks them again later. */
    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        if (!QuestBoardBlock.isRenderPart(state)) {
            return;
        }
        int papers = papers(level, pos);
        if (state.getValue(QuestBoardBlock.PAPERS) != papers) {
            level.setBlock(pos, state.setValue(QuestBoardBlock.PAPERS, papers), Block.UPDATE_CLIENTS);
        }
        level.scheduleTick(pos, state.getBlock(), UPDATE_INTERVAL);
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
        BlockPos middle = QuestBoardBlock.partPos(QuestBoardBlock.basePos(pos, state),
                state.getValue(QuestBoardBlock.FACING), 1, 1);
        BlockState middleState = level.getBlockState(middle);
        if (middleState.is(block) && QuestBoardBlock.isRenderPart(middleState)
                && !level.getBlockTicks().hasScheduledTick(middle, block)) {
            level.scheduleTick(middle, block, 1);
        }
    }

    /**
     * The number of papers of a board: the number of offers of the village near the board, up to
     * {@link QuestBoardBlock#MAX_PAPERS}. A board out of an active village has no paper.
     *
     * @param level the level
     * @param pos   the position of the board
     * @return the number of papers
     */
    public static int papers(ServerLevel level, BlockPos pos) {
        return QuestBoards.villageAt(level, pos)
                .map(settlement -> QuestData.get(level.getServer()).quests().offers(settlement.id()).size())
                .map(count -> Math.min(QuestBoardBlock.MAX_PAPERS, count))
                .orElse(0);
    }
}
