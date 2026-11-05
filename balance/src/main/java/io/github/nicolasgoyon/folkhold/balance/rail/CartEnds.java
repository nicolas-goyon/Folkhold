package io.github.nicolasgoyon.folkhold.balance.rail;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.RailShape;
import net.minecraft.world.phys.Vec3;

/**
 * The ends of a minecart, where a chain attaches, and the gap between two linked minecarts.
 *
 * <p>The gap of a link (see {@link Coupling}) is the distance between the positions of the two minecarts minus the
 * length of one minecart: on a straight rail, the distance between the two ends that face each other. The server uses
 * this gap for the rules of the chain. It changes smoothly in a curve and on a slope, so the chain does not jolt the
 * minecarts when one of them goes onto a new rail.
 *
 * <p>The client draws the chain between the ends of the two minecarts (see {@link #end}). The vanilla renderer turns a
 * minecart along the rail under it: along the diagonal of a curve, and up 45 degrees on a slope. An end is at
 * {@link #HALF_LENGTH} from the position of the minecart along this direction, on the side of the other minecart of
 * the link. A minecart that is not on a rail turns to the other minecart. On a curve rail, the minecart turns 45
 * degrees, so the distance between the ends is more than the gap.
 */
public final class CartEnds {
    /** Half the length of the vanilla minecart model (20 pixels), in blocks. */
    public static final double HALF_LENGTH = 0.625;
    /** A horizontal distance below this value gives no direction, in blocks. */
    private static final double MIN_HORIZONTAL_DISTANCE = 1.0E-4;
    private static final double DIAGONAL = Math.sqrt(0.5);

    private CartEnds() {
    }

    /**
     * The gap between two minecarts: the distance between their positions minus the length of one minecart. The gap
     * is negative when the two minecarts go into each other on a straight rail.
     */
    public static double gap(AbstractMinecart first, AbstractMinecart second) {
        return first.position().distanceTo(second.position()) - 2.0 * HALF_LENGTH;
    }

    /**
     * The end of a minecart on the side of a point.
     *
     * @param cart    the minecart
     * @param center  the position of the minecart
     * @param toward  the point, for example the position of the other minecart of a link
     * @return the end of the minecart, at the height of its position
     */
    public static Vec3 end(AbstractMinecart cart, Vec3 center, Vec3 toward) {
        Vec3 offset = toward.subtract(center);
        Vec3 axis = axis(railShape(cart, center));
        if (axis == null) {
            double horizontal = Math.sqrt(offset.x * offset.x + offset.z * offset.z);
            if (horizontal < MIN_HORIZONTAL_DISTANCE) {
                return center;
            }
            axis = new Vec3(offset.x / horizontal, 0.0, offset.z / horizontal);
        } else if (axis.x * offset.x + axis.z * offset.z < 0.0) {
            axis = axis.scale(-1.0);
        }
        return center.add(axis.scale(HALF_LENGTH));
    }

    /**
     * The shape of the rail at a position of a minecart, with the vanilla search: the block at the position, or the
     * block below it.
     *
     * @return the shape, or null when there is no rail
     */
    @Nullable
    static RailShape railShape(AbstractMinecart cart, Vec3 center) {
        Level level = cart.level();
        BlockPos pos = BlockPos.containing(center);
        if (level.getBlockState(pos.below()).is(BlockTags.RAILS)) {
            pos = pos.below();
        }
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return null;
        }
        return rail.getRailDirection(state, level, pos, cart);
    }

    /**
     * The direction of a rail as a unit vector, with a sign that has no meaning. A slope rises one block for each
     * block.
     *
     * @return the direction, or null for no rail
     */
    @Nullable
    private static Vec3 axis(@Nullable RailShape shape) {
        if (shape == null) {
            return null;
        }
        return switch (shape) {
            case NORTH_SOUTH -> new Vec3(0.0, 0.0, 1.0);
            case EAST_WEST -> new Vec3(1.0, 0.0, 0.0);
            case ASCENDING_EAST -> new Vec3(DIAGONAL, DIAGONAL, 0.0);
            case ASCENDING_WEST -> new Vec3(DIAGONAL, -DIAGONAL, 0.0);
            case ASCENDING_NORTH -> new Vec3(0.0, -DIAGONAL, DIAGONAL);
            case ASCENDING_SOUTH -> new Vec3(0.0, DIAGONAL, DIAGONAL);
            case SOUTH_EAST, NORTH_WEST -> new Vec3(DIAGONAL, 0.0, -DIAGONAL);
            case SOUTH_WEST, NORTH_EAST -> new Vec3(DIAGONAL, 0.0, DIAGONAL);
        };
    }
}
