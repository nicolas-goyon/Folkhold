package io.github.nicolasgoyon.folkhold.world.construction;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Plan;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Step;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.TagParser;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Clearable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ScaffoldingBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;

/**
 * Applies the steps of a construction plan to the level, a number of steps for each tick. For the builders of a site,
 * the run applies one step at a time (see {@link #applyNext} and {@link BuilderSite}). The run puts the blocks in
 * place directly, as a command does: it uses no items, and it drops no items. It does not use the positions to stand
 * of the steps.
 *
 * <p>A removal empties the containers first. For a block of two parts, the other part goes first with no update to its
 * neighbors. Else the update breaks the primary part, and the primary part drops its item.
 */
public final class ConstructionRun {
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();

    private final ServerLevel level;
    private final List<Step> steps;
    private final int stepsPerTick;
    private final Map<String, Optional<BlockState>> states = new HashMap<>();
    private int next;

    /**
     * A run that applies the steps of a plan.
     *
     * @param level        the level of the construction
     * @param plan         the plan
     * @param stepsPerTick the number of steps for each tick, 1 or more
     */
    public ConstructionRun(ServerLevel level, Plan plan, int stepsPerTick) {
        if (stepsPerTick < 1) {
            throw new IllegalArgumentException("stepsPerTick must be 1 or more: " + stepsPerTick);
        }
        this.level = level;
        this.steps = plan.steps();
        this.stepsPerTick = stepsPerTick;
    }

    public ServerLevel level() {
        return level;
    }

    /** The number of applied steps. */
    public int applied() {
        return next;
    }

    /** The number of steps of the plan. */
    public int total() {
        return steps.size();
    }

    /** True when the run applied all the steps. */
    public boolean finished() {
        return next >= steps.size();
    }

    /** Applies the next steps: {@code stepsPerTick} steps at most. */
    public void tick() {
        for (int i = 0; i < stepsPerTick && !finished(); i++) {
            apply(steps.get(next++));
        }
    }

    /** Goes to the step after the next step, with no change to the level: the run does not apply the next step. */
    public void skipNext() {
        if (!finished()) {
            next++;
        }
    }

    /** Applies now all the steps that the run did not apply. */
    public void finish() {
        while (!finished()) {
            apply(steps.get(next++));
        }
    }

    /** Applies the next step, if the run did not apply all its steps. */
    public void applyNext() {
        if (!finished()) {
            apply(steps.get(next++));
        }
    }

    private void apply(Step step) {
        BlockPos pos = BlockKinds.toBlockPos(step.pos());
        switch (step.type()) {
            case REMOVE -> remove(pos, step.partner().map(BlockKinds::toBlockPos));
            case PLACE -> place(pos, step);
            case PLACE_SCAFFOLDING -> placeScaffolding(pos);
            case REMOVE_SCAFFOLDING -> {
                if (level.getBlockState(pos).is(Blocks.SCAFFOLDING)) {
                    level.setBlock(pos, AIR, Block.UPDATE_ALL);
                }
            }
            case ADD_FLUID -> state(step.state()).ifPresent(state -> addFluid(pos, state));
        }
    }

    private void remove(BlockPos pos, Optional<BlockPos> otherPart) {
        // The position of the other part can hold another block, for example next to a door with no upper half.
        Block block = level.getBlockState(pos).getBlock();
        Optional<BlockPos> partner = otherPart.filter(other -> level.getBlockState(other).is(block));
        Optional<BlockState> partnerState = partner.map(level::getBlockState);
        partner.ifPresent(other -> {
            Clearable.tryClear(level.getBlockEntity(other));
            level.setBlock(other, AIR, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        });
        Clearable.tryClear(level.getBlockEntity(pos));
        level.setBlock(pos, AIR, Block.UPDATE_ALL);
        if (partner.isPresent() && partnerState.isPresent()) {
            // The neighbors of the other part get their updates now.
            level.blockUpdated(partner.get(), partnerState.get().getBlock());
            AIR.updateNeighbourShapes(level, partner.get(), Block.UPDATE_ALL);
        }
    }

    private void place(BlockPos pos, Step step) {
        Optional<BlockState> state = state(step.state());
        if (state.isEmpty()) {
            return;
        }
        Clearable.tryClear(level.getBlockEntity(pos));
        level.setBlock(pos, state.get(), Block.UPDATE_ALL);
        if (step.partner().isPresent() && step.partnerState().isPresent()) {
            BlockPos other = BlockKinds.toBlockPos(step.partner().get());
            state(step.partnerState().get()).ifPresent(partnerState -> {
                Clearable.tryClear(level.getBlockEntity(other));
                level.setBlock(other, partnerState, Block.UPDATE_ALL);
            });
        }
        step.data().ifPresent(data -> loadData(pos, state.get(), data));
    }

    private void loadData(BlockPos pos, BlockState state, String data) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (blockEntity == null) {
            return;
        }
        try {
            blockEntity.loadWithComponents(TagParser.parseTag(data), level.registryAccess());
        } catch (CommandSyntaxException e) {
            FolkholdWorld.LOGGER.warn("Construction: data that is not valid at {}: {}", pos, data);
            return;
        }
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_ALL);
    }

    /** A scaffolding block with its distance to a support: a column on a floor has the distance 0. */
    private void placeScaffolding(BlockPos pos) {
        int distance = ScaffoldingBlock.getDistance(level, pos);
        boolean bottom = distance > 0 && !level.getBlockState(pos.below()).is(Blocks.SCAFFOLDING);
        level.setBlock(pos, Blocks.SCAFFOLDING.defaultBlockState().setValue(ScaffoldingBlock.DISTANCE, distance)
                .setValue(ScaffoldingBlock.BOTTOM, bottom), Block.UPDATE_ALL);
    }

    /**
     * Adds the fluid of a block. A waterlogged block keeps its current shape (its neighbors can change it), and gets
     * its water.
     */
    private void addFluid(BlockPos pos, BlockState target) {
        BlockState current = level.getBlockState(pos);
        BlockState state = current.is(target.getBlock()) && current.hasProperty(BlockStateProperties.WATERLOGGED)
                && target.hasProperty(BlockStateProperties.WATERLOGGED)
                ? current.setValue(BlockStateProperties.WATERLOGGED, target.getValue(BlockStateProperties.WATERLOGGED))
                : target;
        level.setBlock(pos, state, Block.UPDATE_ALL);
        FluidState fluid = state.getFluidState();
        if (!fluid.isEmpty()) {
            level.scheduleTick(pos, fluid.getType(), fluid.getType().getTickDelay(level));
        }
    }

    /** The block state of a text, or empty for a text that is not valid (the run skips the step). */
    Optional<BlockState> state(String text) {
        return states.computeIfAbsent(text, key -> {
            try {
                return Optional.of(BlockStateParser.parseForBlock(level.holderLookup(Registries.BLOCK), key, false)
                        .blockState());
            } catch (CommandSyntaxException e) {
                FolkholdWorld.LOGGER.warn("Construction: block state that is not valid: {}", key);
                return Optional.empty();
            }
        });
    }
}
