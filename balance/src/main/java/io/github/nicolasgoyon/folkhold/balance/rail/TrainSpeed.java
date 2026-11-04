package io.github.nicolasgoyon.folkhold.balance.rail;

import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;

/**
 * The speed cap of the minecarts in a train with a locomotive, on the server.
 *
 * <p>The vanilla rails move a minecart in one straight step each tick, along the rail under it, with a cap of 0.4
 * blocks per tick. Above this speed, a minecart can go past a curve or the top of a slope in one step and leave the
 * rails. Thus each tick, the locomotive examines the rails in front of each minecart of its train (see
 * {@link #isStraightAhead}). When all the rails are straight and flat, the cap of the train is the maximum speed of
 * the locomotive. Else the cap is {@code curveSpeed}: the vanilla regime.
 *
 * <p>The mixin of {@link AbstractMinecart#getMaxSpeedWithRail()} gives this cap to each minecart of the train, only on
 * the vanilla rails (blocks of the namespace {@code minecraft}). On a rail of another mod, the cap is the lower value
 * of the cap of the train and the cap of the rail and the minecart. Only vanilla rails count as straight and flat. A
 * minecart that is not in a train with a locomotive keeps the vanilla cap.
 */
public final class TrainSpeed {
    /** The cap of each minecart and the game time of the tick that set it. Only the server thread uses it. */
    private static final Map<AbstractMinecart, Cap> CAPS = new WeakHashMap<>();

    private record Cap(double speed, long time) {
    }

    private TrainSpeed() {
    }

    /** Sets the speed cap of all the minecarts of a train for this tick and the next tick. */
    static void setCap(ServerLevel level, List<AbstractMinecart> train, double speed) {
        Cap cap = new Cap(speed, level.getGameTime());
        for (AbstractMinecart cart : train) {
            CAPS.put(cart, cap);
        }
    }

    /**
     * The speed cap of a minecart on a rail.
     *
     * <ul>
     *   <li>No cap of a train in this tick or the tick before it, or no rail: the vanilla cap.</li>
     *   <li>A vanilla rail: the cap of the train, half in water as for the vanilla rails. The cap of the minecart
     *       ({@code getCurrentCartSpeedCapOnRail}) stays a limit, except for the vanilla furnace minecart: its cap of
     *       0.2 would stop the train.</li>
     *   <li>A rail of another mod: the lower value of the vanilla cap and the cap of the train.</li>
     * </ul>
     *
     * @param vanilla the vanilla cap of the minecart
     */
    public static double railCap(AbstractMinecart cart, double vanilla) {
        if (cart.level().isClientSide() || CAPS.isEmpty() || !cart.canUseRail() || !cart.isOnRails()) {
            return vanilla;
        }
        Cap cap = CAPS.get(cart);
        if (cap == null || cart.level().getGameTime() - cap.time() > 1) {
            return vanilla;
        }
        if (!isVanillaRail(cart.level().getBlockState(cart.getCurrentRailPosition()))) {
            return Math.min(vanilla, cap.speed());
        }
        double speed = cart.isInWater() ? cap.speed() / 2.0 : cap.speed();
        return cart instanceof MinecartFurnace ? speed : Math.min(speed, cart.getCurrentCartSpeedCapOnRail());
    }

    /** Tells if a block is a rail of the namespace {@code minecraft}. */
    private static boolean isVanillaRail(BlockState state) {
        return state.getBlock() instanceof BaseRailBlock
                && BuiltInRegistries.BLOCK.getKey(state.getBlock()).getNamespace().equals("minecraft");
    }

    /**
     * Tells if the rails in front of a minecart are straight and flat: the rail under the minecart and the rails in
     * the next blocks along the direction of travel are vanilla rails with the same straight shape. A curve, a slope,
     * a block with no rail, a rail of another mod and a chunk that is not loaded and complete give false. The check does not load
     * chunks.
     *
     * @param dirX   the X part of the direction of travel
     * @param dirZ   the Z part of the direction of travel
     * @param blocks the number of blocks to examine after the block of the minecart
     */
    static boolean isStraightAhead(ServerLevel level, AbstractMinecart cart, double dirX, double dirZ, int blocks) {
        if (!cart.isOnRails()) {
            return false;
        }
        boolean eastWest = Math.abs(dirX) >= Math.abs(dirZ);
        RailShape shape = eastWest ? RailShape.EAST_WEST : RailShape.NORTH_SOUTH;
        int stepX = eastWest ? (int) Math.signum(dirX) : 0;
        int stepZ = eastWest ? 0 : (int) Math.signum(dirZ);
        BlockPos start = cart.getCurrentRailPosition();
        for (int i = 0; i <= blocks; i++) {
            BlockPos pos = start.offset(stepX * i, 0, stepZ * i);
            // A chunk with a ticket can be loaded but not complete: a read in it waits for the generation.
            if (level.getChunkSource().getChunkNow(SectionPos.blockToSectionCoord(pos.getX()),
                    SectionPos.blockToSectionCoord(pos.getZ())) == null) {
                return false;
            }
            BlockState state = level.getBlockState(pos);
            if (!isVanillaRail(state) || ((BaseRailBlock) state.getBlock()).getRailDirection(state, level, pos, cart)
                    != shape) {
                return false;
            }
        }
        return true;
    }
}
