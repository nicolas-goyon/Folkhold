package io.github.nicolasgoyon.folkhold.balance.rail;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.dispenser.BlockSource;
import net.minecraft.core.dispenser.DefaultDispenseItemBehavior;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.Vec3;

/**
 * The item of a locomotive. The item and the dispenser place the locomotive on a rail with the same logic as the
 * vanilla minecart item.
 */
public class LocomotiveItem extends Item {
    private final Supplier<? extends EntityType<? extends Locomotive>> type;

    public LocomotiveItem(Supplier<? extends EntityType<? extends Locomotive>> type, Properties properties) {
        super(properties);
        this.type = type;
        DispenserBlock.registerBehavior(this, new Dispense());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!state.is(BlockTags.RAILS)) {
            return InteractionResult.FAIL;
        }
        ItemStack stack = context.getItemInHand();
        if (level instanceof ServerLevel server) {
            double offset = ascending(server, pos, state) ? 0.5 : 0.0;
            if (!place(server, new Vec3(pos.getX() + 0.5, pos.getY() + 0.0625 + offset, pos.getZ() + 0.5), stack,
                    context.getPlayer())) {
                return InteractionResult.FAIL;
            }
            server.gameEvent(GameEvent.ENTITY_PLACE, pos,
                    GameEvent.Context.of(context.getPlayer(), server.getBlockState(pos.below())));
        }
        stack.shrink(1);
        return InteractionResult.sidedSuccess(level.isClientSide());
    }

    private static boolean ascending(ServerLevel level, BlockPos pos, BlockState state) {
        return state.getBlock() instanceof BaseRailBlock rail
                && rail.getRailDirection(state, level, pos, null).isAscending();
    }

    /** Places a new locomotive. */
    private boolean place(ServerLevel level, Vec3 pos, ItemStack stack, @Nullable Player player) {
        Locomotive cart = type.get().create(level);
        if (cart == null) {
            return false;
        }
        cart.moveTo(pos.x, pos.y, pos.z);
        EntityType.<Locomotive>createDefaultStackConfig(level, stack, player).accept(cart);
        level.addFreshEntity(cart);
        return true;
    }

    /** The dispenser places the locomotive on the rail in front of it, as for a vanilla minecart. */
    private final class Dispense extends DefaultDispenseItemBehavior {
        @Override
        protected ItemStack execute(BlockSource source, ItemStack stack) {
            Direction facing = source.state().getValue(DispenserBlock.FACING);
            ServerLevel level = source.level();
            Vec3 center = source.center();
            BlockPos pos = source.pos().relative(facing);
            BlockState state = level.getBlockState(pos);
            double offset;
            if (state.is(BlockTags.RAILS)) {
                offset = ascending(level, pos, state) ? 0.6 : 0.1;
            } else if (state.isAir() && level.getBlockState(pos.below()).is(BlockTags.RAILS)) {
                boolean below = facing != Direction.DOWN && ascending(level, pos.below(), level.getBlockState(pos.below()));
                offset = below ? -0.4 : -0.9;
            } else {
                return super.execute(source, stack);
            }
            Vec3 at = new Vec3(center.x + facing.getStepX() * 1.125, Math.floor(center.y) + facing.getStepY() + offset,
                    center.z + facing.getStepZ() * 1.125);
            if (place(level, at, stack, null)) {
                stack.shrink(1);
            }
            return stack;
        }

        @Override
        protected void playSound(BlockSource source) {
            source.level().levelEvent(1000, source.pos(), 0);
        }
    }
}
