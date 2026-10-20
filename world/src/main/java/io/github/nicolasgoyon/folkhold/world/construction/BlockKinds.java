package io.github.nicolasgoyon.folkhold.world.construction;

import io.github.nicolasgoyon.folkhold.core.sim.construction.BlockKind;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildBlock;
import io.github.nicolasgoyon.folkhold.core.sim.construction.BuildPos;
import io.github.nicolasgoyon.folkhold.core.sim.construction.Side;
import io.github.nicolasgoyon.folkhold.world.FolkholdWorld;
import java.util.List;
import java.util.Optional;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.AmethystClusterBlock;
import net.minecraft.world.level.block.BannerBlock;
import net.minecraft.world.level.block.BaseCoralPlantTypeBlock;
import net.minecraft.world.level.block.BaseCoralWallFanBlock;
import net.minecraft.world.level.block.BasePressurePlateBlock;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.BaseTorchBlock;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BubbleColumnBlock;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CakeBlock;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CandleCakeBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.CeilingHangingSignBlock;
import net.minecraft.world.level.block.ChainBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CrossCollisionBlock;
import net.minecraft.world.level.block.DiodeBlock;
import net.minecraft.world.level.block.FaceAttachedHorizontalDirectionalBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.FenceGateBlock;
import net.minecraft.world.level.block.FlowerPotBlock;
import net.minecraft.world.level.block.GrindstoneBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.RedStoneWireBlock;
import net.minecraft.world.level.block.RedstoneWallTorchBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.TripWireBlock;
import net.minecraft.world.level.block.TripWireHookBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.WallBlock;
import net.minecraft.world.level.block.WallHangingSignBlock;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.WallTorchBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BellAttachType;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.material.FluidState;

/**
 * Describes the blocks of the level for the construction plans. The description gives the kind of each block, its
 * support and its other part. It also tells if a builder can stand on the block or in it.
 *
 * <p>Rules:
 * <ul>
 *   <li>A door, a tall plant or a bed has two parts. The lower half or the foot is the primary part. The lower half
 *       needs the block below it.</li>
 *   <li>A block that falls (sand, gravel, an anvil) is a falling block. A pointed dripstone needs the block on the
 *       other side of its tip.</li>
 *   <li>An attached block needs the block on the side of its support: a torch, a lantern, a ladder, a sign, a plant,
 *       a carpet, a button. A trapdoor, a chain, a flower pot, a grindstone and a wall hanging sign stay in place
 *       alone. They come after the block on their side.</li>
 *   <li>A fence, a wall, a pane, stairs, a chest, a fence gate and a tripwire get their shape from their
 *       neighbors.</li>
 *   <li>A source of water or lava is a fluid. A flowing fluid is air: it comes back from its source. A waterlogged
 *       block gets its water at the end of the construction.</li>
 *   <li>A builder stands on a block with a full top face, and in a block with no collision and no fluid.</li>
 * </ul>
 *
 * <p>The tags {@code folkhold_world:construction/support_below} and {@code folkhold_world:construction/support_above}
 * add the other blocks that need the block below or above them (sugar cane, cave vines).
 */
public final class BlockKinds {
    /** Blocks that need the block below them, and that the rules above do not find. */
    public static final TagKey<Block> SUPPORT_BELOW = tag("construction/support_below");
    /** Blocks that hang from the block above them, and that the rules above do not find. */
    public static final TagKey<Block> SUPPORT_ABOVE = tag("construction/support_above");
    /**
     * Blocks with block entity data that a construction keeps: the text of a sign, the patterns of a banner. The
     * construction does not keep the data of the other blocks, for example the items of a chest.
     */
    public static final TagKey<Block> KEEP_DATA = tag("construction/keep_data");
    /** The faces of a vine, in the order of the search of its support. */
    private static final List<Direction> VINE_FACES = List.of(Direction.UP, Direction.NORTH, Direction.SOUTH,
            Direction.WEST, Direction.EAST);

    private BlockKinds() {
    }

    private static TagKey<Block> tag(String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(FolkholdWorld.MOD_ID, path));
    }

    /**
     * The description of a block for a construction plan.
     *
     * @param pos   the position of the block: it gives the position of the other part of a two-part block
     * @param state the state of the block
     * @param data  the data of the block entity, for a block of {@link #KEEP_DATA}
     * @return the block for a plan
     */
    public static BuildBlock describe(BlockPos pos, BlockState state, Optional<String> data) {
        if (state.isAir()) {
            return BuildBlock.AIR;
        }
        Block block = state.getBlock();
        if (block instanceof LiquidBlock || block instanceof BubbleColumnBlock) {
            if (!state.getFluidState().isSource()) {
                return new BuildBlock(BuildBlock.AIR_STATE, BlockKind.AIR, Optional.empty(), false, Optional.empty(),
                        true, Optional.empty(), Optional.empty(), false, false);
            }
            return build(state, BlockKind.FLUID, Optional.empty(), false, Optional.empty(), true, data);
        }
        BuildBlock described = describeSolid(pos, state, data);
        if (state.hasProperty(BlockStateProperties.WATERLOGGED) && state.getValue(BlockStateProperties.WATERLOGGED)) {
            return described.withFluid(serialize(state.setValue(BlockStateProperties.WATERLOGGED, false)));
        }
        return described;
    }

    private static BuildBlock describeSolid(BlockPos pos, BlockState state, Optional<String> data) {
        Block block = state.getBlock();
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            boolean lower = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER;
            BlockPos partner = lower ? pos.above() : pos.below();
            Optional<Side> floor = lower ? Optional.of(Side.DOWN) : Optional.empty();
            return build(state, BlockKind.TWO_PART, floor, floor.isPresent(), Optional.of(toBuildPos(partner)), lower,
                    data);
        }
        if (state.hasProperty(BlockStateProperties.BED_PART)
                && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            boolean foot = state.getValue(BlockStateProperties.BED_PART) == BedPart.FOOT;
            Direction facing = state.getValue(BlockStateProperties.HORIZONTAL_FACING);
            BlockPos partner = pos.relative(foot ? facing : facing.getOpposite());
            return build(state, BlockKind.TWO_PART, Optional.empty(), false, Optional.of(toBuildPos(partner)), foot,
                    data);
        }
        if (block instanceof PointedDripstoneBlock) {
            Direction tip = state.getValue(PointedDripstoneBlock.TIP_DIRECTION);
            return build(state, BlockKind.ATTACHED, Optional.of(side(tip.getOpposite())), true, Optional.empty(), true,
                    data);
        }
        if (block instanceof Fallable) {
            return build(state, BlockKind.FALLING, Optional.of(Side.DOWN), true, Optional.empty(), true, data);
        }
        Optional<Side> optional = optionalSupport(state);
        if (optional.isPresent()) {
            return build(state, BlockKind.ATTACHED, optional, false, Optional.empty(), true, data);
        }
        Optional<Side> required = requiredSupport(state);
        if (required.isPresent()) {
            return build(state, BlockKind.ATTACHED, required, true, Optional.empty(), true, data);
        }
        if (block instanceof CrossCollisionBlock || block instanceof WallBlock || block instanceof StairBlock
                || block instanceof ChestBlock || block instanceof FenceGateBlock || block instanceof TripWireBlock) {
            return build(state, BlockKind.CONNECTED, Optional.empty(), false, Optional.empty(), true, data);
        }
        return build(state, BlockKind.SOLID, Optional.empty(), false, Optional.empty(), true, data);
    }

    /** The side of the block that a block comes after, for a block that stays in place alone. */
    private static Optional<Side> optionalSupport(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof GrindstoneBlock) {
            return Optional.of(faceSupport(state));
        }
        if (block instanceof TrapDoorBlock) {
            return Optional.of(side(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()));
        }
        if (block instanceof WallHangingSignBlock) {
            return Optional.of(side(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getClockWise()));
        }
        if (block instanceof ChainBlock) {
            return state.getValue(BlockStateProperties.AXIS) == Direction.Axis.Y ? Optional.of(Side.UP)
                    : Optional.empty();
        }
        if (block instanceof FlowerPotBlock) {
            return Optional.of(Side.DOWN);
        }
        if (block instanceof BellBlock && state.getValue(BellBlock.ATTACHMENT) == BellAttachType.DOUBLE_WALL) {
            return Optional.of(side(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        return Optional.empty();
    }

    /** The side of the support of a block that breaks with no support. */
    private static Optional<Side> requiredSupport(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof FaceAttachedHorizontalDirectionalBlock) {
            return Optional.of(faceSupport(state));
        }
        if (block instanceof WallTorchBlock || block instanceof RedstoneWallTorchBlock || block instanceof LadderBlock
                || block instanceof WallSignBlock || block instanceof WallBannerBlock
                || block instanceof TripWireHookBlock || block instanceof BaseCoralWallFanBlock) {
            return Optional.of(side(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite()));
        }
        if (block instanceof AmethystClusterBlock) {
            return Optional.of(side(state.getValue(BlockStateProperties.FACING).getOpposite()));
        }
        if (block instanceof CocoaBlock) {
            return Optional.of(side(state.getValue(BlockStateProperties.HORIZONTAL_FACING)));
        }
        if (block instanceof LanternBlock) {
            return Optional.of(state.getValue(LanternBlock.HANGING) ? Side.UP : Side.DOWN);
        }
        if (block instanceof BellBlock) {
            return Optional.of(switch (state.getValue(BellBlock.ATTACHMENT)) {
                case FLOOR -> Side.DOWN;
                case CEILING -> Side.UP;
                case SINGLE_WALL, DOUBLE_WALL -> side(state.getValue(BlockStateProperties.HORIZONTAL_FACING));
            });
        }
        if (block instanceof CeilingHangingSignBlock || state.is(SUPPORT_ABOVE)) {
            return Optional.of(Side.UP);
        }
        if (block instanceof MultifaceBlock) {
            for (Direction direction : Direction.values()) {
                if (MultifaceBlock.hasFace(state, direction)) {
                    return Optional.of(side(direction));
                }
            }
            return Optional.empty();
        }
        if (block instanceof VineBlock) {
            for (Direction direction : VINE_FACES) {
                if (state.getValue(VineBlock.getPropertyForFace(direction))) {
                    return Optional.of(side(direction));
                }
            }
            return Optional.empty();
        }
        if (block instanceof BaseTorchBlock || block instanceof BushBlock || block instanceof BaseRailBlock
                || block instanceof BasePressurePlateBlock || block instanceof CarpetBlock
                || block instanceof RedStoneWireBlock || block instanceof DiodeBlock
                || block instanceof StandingSignBlock || block instanceof BannerBlock || block instanceof CandleBlock
                || block instanceof CandleCakeBlock || block instanceof SnowLayerBlock || block instanceof CakeBlock
                || block instanceof BaseCoralPlantTypeBlock || state.is(SUPPORT_BELOW)) {
            return Optional.of(Side.DOWN);
        }
        return Optional.empty();
    }

    /** The support of a block on a floor, a ceiling or a wall (a button, a lever, a grindstone). */
    private static Side faceSupport(BlockState state) {
        return switch (state.getValue(BlockStateProperties.ATTACH_FACE)) {
            case FLOOR -> Side.DOWN;
            case CEILING -> Side.UP;
            case WALL -> side(state.getValue(BlockStateProperties.HORIZONTAL_FACING).getOpposite());
        };
    }

    private static BuildBlock build(BlockState state, BlockKind kind, Optional<Side> support, boolean supportRequired,
            Optional<BuildPos> partner, boolean primary, Optional<String> data) {
        FluidState fluid = state.getFluidState();
        boolean floor = state.isFaceSturdy(EmptyBlockGetter.INSTANCE, BlockPos.ZERO, Direction.UP);
        boolean passable = fluid.isEmpty()
                && state.getCollisionShape(EmptyBlockGetter.INSTANCE, BlockPos.ZERO).isEmpty();
        return new BuildBlock(serialize(state), kind, support, supportRequired, partner, primary, Optional.empty(),
                data, floor, passable);
    }

    /** The state as text, with all its properties (see {@link ConstructionRun} for the reverse). */
    public static String serialize(BlockState state) {
        return BlockStateParser.serialize(state);
    }

    /** The side of a direction. */
    public static Side side(Direction direction) {
        return switch (direction) {
            case DOWN -> Side.DOWN;
            case UP -> Side.UP;
            case NORTH -> Side.NORTH;
            case SOUTH -> Side.SOUTH;
            case WEST -> Side.WEST;
            case EAST -> Side.EAST;
        };
    }

    public static BuildPos toBuildPos(BlockPos pos) {
        return new BuildPos(pos.getX(), pos.getY(), pos.getZ());
    }

    public static BlockPos toBlockPos(BuildPos pos) {
        return new BlockPos(pos.x(), pos.y(), pos.z());
    }
}
