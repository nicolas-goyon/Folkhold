package io.github.nicolasgoyon.folkhold.blocks.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.FeatureBlock;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.neoforged.neoforge.common.ItemAbilities;
import net.neoforged.neoforge.common.ItemAbility;

/**
 * Brazier: a stone bowl of embers on a square pedestal. The description {@code tools/pixvox/brazier.py} makes its
 * models with PixVox. The shape of the block follows the parts of the model.
 *
 * <ul>
 *   <li>{@link #LIT}: a lit brazier gives light (see {@link #LIGHT}). Its embers glow and its model shows flames.
 *       Small flames and smoke rise over the embers.</li>
 *   <li>A player lights and puts out a brazier as a vanilla campfire: flint and steel, fire charge or burning
 *       projectile to light it, shovel, water potion or water to put it out. A brazier that goes out gives the
 *       smoke of a campfire.</li>
 *   <li>{@link #WATERLOGGED}: a brazier in water does not burn.</li>
 *   <li>A brazier that a player places is lit, as a campfire, except in water.</li>
 *   <li>The brazier does not burn the entities, as a campfire does.</li>
 *   <li>The collision box is 1.5 blocks high, as a fence: a mob cannot jump on the brazier. The path finders do not
 *       go through it.</li>
 * </ul>
 *
 * <p>The block is not in the tag {@code minecraft:campfires}: with this tag, the path finders treat a lit brazier as
 * fire, and the mobs keep away from the blocks near it. Thus the water potions put out the braziers through
 * {@link BrazierWater}.
 *
 * <p>The {@link BlockFeature} of the block can add actions. Other mods change {@link #LIT}, for example the heart of a
 * village at night.
 */
public class BrazierBlock extends FeatureBlock implements SimpleWaterloggedBlock {
    public static final MapCodec<BrazierBlock> CODEC = simpleCodec(BrazierBlock::new);
    /** True if the embers burn. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** True if the brazier is in water. A brazier in water does not burn. */
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    /** The light level of a lit brazier. */
    public static final int LIGHT = 15;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.box(5.0, 3.0, 5.0, 11.0, 12.0, 11.0),
            Block.box(3.0, 12.0, 3.0, 13.0, 14.0, 13.0),
            Block.box(1.0, 14.0, 1.0, 15.0, 22.0, 15.0));
    private static final VoxelShape COLLISION = Block.box(1.0, 0.0, 1.0, 15.0, 24.0, 15.0);

    public BrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false).setValue(WATERLOGGED, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT, WATERLOGGED);
    }

    /** The light level of a state: {@link #LIGHT} if it is lit, else 0. */
    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? LIGHT : 0;
    }

    /**
     * True if a brazier can start to burn: it does not burn and it is not in water.
     *
     * @param state the state of the brazier
     * @return true if the brazier can start to burn
     */
    public static boolean canLight(BlockState state) {
        return !state.getValue(LIT) && !state.getValue(WATERLOGGED);
    }

    /**
     * The smoke of a brazier that goes out, as the smoke of a campfire. On the server, the players near the brazier
     * see it. The caller plays the sound of its action.
     *
     * @param level the level
     * @param pos   the position of the brazier
     */
    public static void smoke(LevelAccessor level, BlockPos pos) {
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.5, pos.getY() + 1.4,
                    pos.getZ() + 0.5, 8, 0.2, 0.1, 0.2, 0.01);
        }
    }

    /** A player places a lit brazier, as a campfire. In water, the brazier does not burn. */
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        boolean water = context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER;
        return defaultBlockState().setValue(WATERLOGGED, water).setValue(LIT, !water);
    }

    /**
     * The actions of the tools, as for a campfire. Flint and steel and fire charge light the brazier
     * ({@link ItemAbilities#FIRESTARTER_LIGHT}). Their items play the sound and use the item. A shovel puts it out
     * ({@link ItemAbilities#SHOVEL_DOUSE}): the shovel plays the sound, and the brazier gives smoke.
     */
    @Override
    public @Nullable BlockState getToolModifiedState(BlockState state, UseOnContext context, ItemAbility itemAbility,
            boolean simulate) {
        if (itemAbility == ItemAbilities.FIRESTARTER_LIGHT && canLight(state)) {
            return state.setValue(LIT, true);
        }
        if (itemAbility == ItemAbilities.SHOVEL_DOUSE && state.getValue(LIT)) {
            if (!simulate) {
                smoke(context.getLevel(), context.getClickedPos());
            }
            return state.setValue(LIT, false);
        }
        return super.getToolModifiedState(state, context, itemAbility, simulate);
    }

    /** A burning projectile lights the brazier, as a campfire. */
    @Override
    protected void onProjectileHit(Level level, BlockState state, BlockHitResult hit, Projectile projectile) {
        BlockPos pos = hit.getBlockPos();
        if (!level.isClientSide() && projectile.isOnFire() && projectile.mayInteract(level, pos) && canLight(state)) {
            level.setBlock(pos, state.setValue(LIT, true), Block.UPDATE_ALL_IMMEDIATE);
        }
    }

    /** Water puts out a lit brazier, as a campfire. A brazier in water does not burn. */
    @Override
    public boolean placeLiquid(LevelAccessor level, BlockPos pos, BlockState state, FluidState fluidState) {
        if (state.getValue(WATERLOGGED) || fluidState.getType() != Fluids.WATER) {
            return false;
        }
        if (state.getValue(LIT) && !level.isClientSide()) {
            level.playSound(null, pos, SoundEvents.GENERIC_EXTINGUISH_FIRE, SoundSource.BLOCKS, 1.0F, 1.0F);
            smoke(level, pos);
        }
        level.setBlock(pos, state.setValue(WATERLOGGED, true).setValue(LIT, false), Block.UPDATE_ALL);
        level.scheduleTick(pos, fluidState.getType(), fluidState.getType().getTickDelay(level));
        return true;
    }

    @Override
    protected FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Override
    protected BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
            LevelAccessor level, BlockPos pos, BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos,
            CollisionContext context) {
        return COLLISION;
    }

    /** Not passable: a mob cannot walk or jump through the brazier. */
    @Override
    protected boolean isPathfindable(BlockState state, PathComputationType type) {
        return false;
    }

    /** Small flames and smoke over the embers of a lit brazier, and the crackle of the embers. */
    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (!state.getValue(LIT)) {
            return;
        }
        double x = pos.getX() + 0.5;
        double y = pos.getY() + 1.25;
        double z = pos.getZ() + 0.5;
        if (random.nextInt(2) == 0) {
            level.addParticle(ParticleTypes.SMALL_FLAME, x + (random.nextDouble() - 0.5) * 0.5, y,
                    z + (random.nextDouble() - 0.5) * 0.5, 0.0, 0.01, 0.0);
        }
        if (random.nextInt(4) == 0) {
            level.addParticle(ParticleTypes.SMOKE, x + (random.nextDouble() - 0.5) * 0.4, y + 0.3,
                    z + (random.nextDouble() - 0.5) * 0.4, 0.0, 0.03, 0.0);
        }
        if (random.nextInt(12) == 0) {
            level.playLocalSound(x, y, z, SoundEvents.CAMPFIRE_CRACKLE, SoundSource.BLOCKS,
                    0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
    }
}
