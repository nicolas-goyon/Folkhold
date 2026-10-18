package io.github.nicolasgoyon.folkhold.blocks.block;

import com.mojang.serialization.MapCodec;
import io.github.nicolasgoyon.folkhold.blocks.feature.BlockFeature;
import io.github.nicolasgoyon.folkhold.blocks.feature.FeatureBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Brazier: a stone bowl of embers on a square pedestal. The script {@code tools/models/brazier.py} makes its models.
 *
 * <ul>
 *   <li>{@link #LIT}: a lit brazier gives light (see {@link #LIGHT}) and shows small flames and smoke over the
 *       embers.</li>
 *   <li>The brazier does not burn the entities, as a campfire does.</li>
 *   <li>The collision box is 1.5 blocks high, as a fence: a mob cannot jump on the brazier. The path finders do not
 *       go through it.</li>
 * </ul>
 *
 * <p>The {@link BlockFeature} of the block can add actions. Other mods change {@link #LIT}, for example the heart of a
 * village at night.
 */
public class BrazierBlock extends FeatureBlock {
    public static final MapCodec<BrazierBlock> CODEC = simpleCodec(BrazierBlock::new);
    /** True if the embers burn. */
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** The light level of a lit brazier. */
    public static final int LIGHT = 15;

    private static final VoxelShape SHAPE = Shapes.or(
            Block.box(2.0, 0.0, 2.0, 14.0, 3.0, 14.0),
            Block.box(4.5, 3.0, 4.5, 11.5, 12.0, 11.5),
            Block.box(3.0, 12.0, 3.0, 13.0, 14.0, 13.0),
            Block.box(1.0, 14.0, 1.0, 15.0, 22.0, 15.0));
    private static final VoxelShape COLLISION = Block.box(1.0, 0.0, 1.0, 15.0, 24.0, 15.0);

    public BrazierBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(LIT, false));
    }

    @Override
    protected MapCodec<? extends Block> codec() {
        return CODEC;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(LIT);
    }

    /** The light level of a state: {@link #LIGHT} if it is lit, else 0. */
    public static int lightLevel(BlockState state) {
        return state.getValue(LIT) ? LIGHT : 0;
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
