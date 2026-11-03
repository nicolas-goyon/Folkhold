package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.network.BalanceNetwork;
import io.github.nicolasgoyon.folkhold.balance.registry.ModAttachments;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.entity.EntityTravelToDimensionEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

/**
 * The movement of the trains, on the server.
 *
 * <p>The game ticks the entities in a random order. Thus the mod moves the trains after the entities, in
 * {@link LevelTickEvent.Post}: it finds each train one time, then corrects the speed of each minecart in order from
 * the front minecart. Each correction uses the positions of the same tick, so a long train has no delay of one tick
 * for each minecart.
 *
 * <p>A link waits when one of its minecarts is not loaded. A link breaks when a minecart is destroyed or goes through
 * a portal, or when the chain stays too long above its break distance (see {@link Coupling}).
 */
public final class Trains {
    /** The minecarts with a link in each level that are loaded. */
    private static final Map<ResourceKey<Level>, Set<AbstractMinecart>> LINKED = new HashMap<>();
    /** The break counter of each back minecart whose chain is above the break distance. */
    private static final Map<UUID, Integer> STRETCH_TICKS = new HashMap<>();
    /** A horizontal distance below this value gives no direction to the chain, in blocks. */
    private static final double MIN_HORIZONTAL_DISTANCE = 1.0E-4;
    /** The chain moves a minecart on a rail only when the cosine between the chain and the rail is at least this. */
    private static final double MIN_RAIL_ALIGNMENT = 0.2;
    /** The parts of a diagonal unit vector. */
    private static final double DIAGONAL = Math.sqrt(0.5);

    private Trains() {
    }

    static void track(AbstractMinecart cart) {
        if (!cart.level().isClientSide() && !cart.isRemoved()) {
            LINKED.computeIfAbsent(cart.level().dimension(),
                    key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(cart);
        }
    }

    static void untrack(AbstractMinecart cart) {
        Set<AbstractMinecart> carts = LINKED.get(cart.level().dimension());
        if (carts != null) {
            carts.remove(cart);
        }
        STRETCH_TICKS.remove(cart.getUUID());
    }

    /** Forgets the break counter of the link between two minecarts. */
    static void forgetStretch(AbstractMinecart cart, UUID partner) {
        STRETCH_TICKS.remove(cart.getUUID());
        STRETCH_TICKS.remove(partner);
    }

    /** The number of loaded minecarts with a link in a level. */
    public static int trackedCount(ServerLevel level) {
        Set<AbstractMinecart> carts = LINKED.get(level.dimension());
        return carts == null ? 0 : carts.size();
    }

    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        Set<AbstractMinecart> carts = LINKED.get(level.dimension());
        if (carts == null || carts.isEmpty()) {
            return;
        }
        // A minecart that unloads while its chunk is not accessible does not leave the level again.
        carts.removeIf(Entity::isRemoved);
        Coupling coupling = BalanceConfig.coupling();
        int maxCarts = BalanceConfig.TRAIN_MAX_CARTS.get();
        Set<AbstractMinecart> done = Collections.newSetFromMap(new IdentityHashMap<>());
        // A copy in a stable order: a link that breaks changes the set.
        StaleLinks stale = StaleLinks.get(level.getServer());
        for (AbstractMinecart cart : List.copyOf(new LinkedHashSet<>(carts))) {
            if (stale.has(cart.getUUID())) {
                forgetStaleLinks(level, cart);
            }
            // A minecart in a chunk that is not accessible stays in the set, but the level does not give it.
            if (!done.contains(cart) && !cart.isRemoved() && level.getEntity(cart.getUUID()) == cart
                    && isFront(level, cart)) {
                moveTrain(level, cart, coupling, maxCarts, done);
            }
        }
    }

    /**
     * Tells if a minecart is the front minecart of the loaded part of its train: it has no front link, or the
     * minecart in front is not loaded. A front link that the other minecart does not have is removed.
     */
    private static boolean isFront(ServerLevel level, AbstractMinecart cart) {
        Optional<UUID> frontId = CartLinks.get(cart).front();
        if (frontId.isEmpty()) {
            return true;
        }
        Optional<AbstractMinecart> front = CartLinks.find(level, frontId.get());
        if (front.isEmpty()) {
            return true;
        }
        if (!CartLinks.get(front.get()).back().equals(Optional.of(cart.getUUID()))) {
            CartLinks.set(cart, CartLinks.get(cart).withFront(Optional.empty()));
            CartLinks.sync(level, cart);
            return true;
        }
        return false;
    }

    private static void moveTrain(ServerLevel level, AbstractMinecart head, Coupling coupling, int maxCarts,
                                  Set<AbstractMinecart> done) {
        AbstractMinecart current = head;
        done.add(current);
        for (int i = 0; i < maxCarts; i++) {
            Optional<UUID> backId = CartLinks.get(current).back();
            if (backId.isEmpty()) {
                return;
            }
            Optional<AbstractMinecart> found = CartLinks.find(level, backId.get());
            if (found.isEmpty()) {
                // The minecart behind is not loaded: the link waits.
                return;
            }
            AbstractMinecart back = found.get();
            if (done.contains(back) || !CartLinks.get(back).front().equals(Optional.of(current.getUUID()))) {
                // A back link that the other minecart does not have.
                CartLinks.set(current, CartLinks.get(current).withBack(Optional.empty()));
                CartLinks.sync(level, current);
                return;
            }
            done.add(back);
            if (pull(level, coupling, current, back)) {
                return;
            }
            current = back;
        }
    }

    /**
     * Applies the chain between two minecarts for one tick.
     *
     * @return true if the link breaks
     */
    private static boolean pull(ServerLevel level, Coupling coupling, AbstractMinecart front, AbstractMinecart back) {
        double dx = front.getX() - back.getX();
        double dy = front.getY() - back.getY();
        double dz = front.getZ() - back.getZ();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int ticks = coupling.nextStretchTicks(STRETCH_TICKS.getOrDefault(back.getUUID(), 0), distance);
        if (coupling.breaks(ticks)) {
            CartLinks.unlink(level, back, front.getUUID(), true);
            return true;
        }
        if (ticks > 0) {
            STRETCH_TICKS.put(back.getUUID(), ticks);
        } else {
            STRETCH_TICKS.remove(back.getUUID());
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < MIN_HORIZONTAL_DISTANCE) {
            return false;
        }
        double nx = dx / horizontal;
        double nz = dz / horizontal;
        // The speeds come from the movement of this tick. The motion of a furnace minecart is not its speed.
        double frontSpeed = (front.getX() - front.xo) * nx + (front.getZ() - front.zo) * nz;
        double backSpeed = (back.getX() - back.xo) * nx + (back.getZ() - back.zo) * nz;
        double impulse = coupling.impulse(distance, frontSpeed - backSpeed);
        if (impulse != 0.0) {
            accelerate(level, back, nx, nz, impulse);
        }
        return false;
    }

    /**
     * Gives a speed change to the back minecart of a link. On a rail, the change goes along the rail, in the direction
     * of the chain. The new speed is the speed of the movement of this tick plus the change: the motion of a minecart
     * does not grow above its real speed. The motion also stays below the speed limit of the rail, so that a minecart
     * does not leave a curve.
     *
     * @param nx      the X part of the horizontal direction from the back minecart to the front minecart
     * @param nz      the Z part of the horizontal direction from the back minecart to the front minecart
     * @param impulse the speed change toward the front minecart, in blocks per tick. A negative value slows the back
     *                minecart.
     */
    private static void accelerate(ServerLevel level, AbstractMinecart cart, double nx, double nz, double impulse) {
        double dirX = nx;
        double dirZ = nz;
        double[] axis = railAxis(level, cart);
        if (axis != null) {
            double along = nx * axis[0] + nz * axis[1];
            if (Math.abs(along) < MIN_RAIL_ALIGNMENT) {
                // The chain is across the rail: it does not move the minecart.
                return;
            }
            dirX = Math.signum(along) * axis[0];
            dirZ = Math.signum(along) * axis[1];
        }
        Vec3 motion = cart.getDeltaMovement();
        double speed = (cart.getX() - cart.xo) * dirX + (cart.getZ() - cart.zo) * dirZ;
        double current = motion.x * dirX + motion.z * dirZ;
        double change = speed + impulse - current;
        // A pull does not slow the minecart, and a push does not make it faster.
        if (impulse > 0.0 ? change <= 0.0 : change >= 0.0) {
            return;
        }
        Vec3 next = motion.add(dirX * change, 0.0, dirZ * change);
        double limit = Math.max(cart.getMaxSpeedWithRail(), motion.horizontalDistance());
        double horizontal = next.horizontalDistance();
        if (horizontal > limit) {
            next = new Vec3(next.x * limit / horizontal, next.y, next.z * limit / horizontal);
        }
        cart.setDeltaMovement(next);
    }

    /**
     * The horizontal direction of the rail under a minecart, as a unit vector {x, z} with a sign that has no meaning.
     *
     * @return the direction, or null when the minecart is not on a rail
     */
    @Nullable
    private static double[] railAxis(ServerLevel level, AbstractMinecart cart) {
        if (!cart.isOnRails()) {
            return null;
        }
        BlockPos pos = cart.getCurrentRailPosition();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof BaseRailBlock rail)) {
            return null;
        }
        return switch (rail.getRailDirection(state, level, pos, cart)) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new double[] {0.0, 1.0};
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new double[] {1.0, 0.0};
            case SOUTH_EAST, NORTH_WEST -> new double[] {DIAGONAL, -DIAGONAL};
            case SOUTH_WEST, NORTH_EAST -> new double[] {DIAGONAL, DIAGONAL};
        };
    }

    /** A minecart with a link loads: it forgets the links to destroyed minecarts, then the mod moves it. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof AbstractMinecart cart
                && cart.hasData(ModAttachments.CART_LINK)) {
            forgetStaleLinks(level, cart);
            CartLinks.set(cart, CartLinks.get(cart));
        }
    }

    /**
     * Removes the links of a minecart to the minecarts that broke the link while the first minecart was not in the
     * level (see {@link StaleLinks}).
     */
    private static void forgetStaleLinks(ServerLevel level, AbstractMinecart cart) {
        CartLink link = CartLinks.get(cart);
        for (UUID gone : StaleLinks.get(level.getServer()).take(cart.getUUID())) {
            link = link.without(gone);
        }
        CartLinks.set(cart, link);
    }

    /**
     * A minecart leaves its level. A destroyed minecart breaks its links and drops the chains. A minecart that
     * unloads keeps its links.
     */
    public static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (!(event.getLevel() instanceof ServerLevel level) || !(event.getEntity() instanceof AbstractMinecart cart)) {
            return;
        }
        Entity.RemovalReason reason = cart.getRemovalReason();
        if (reason == null) {
            // The chunk of the minecart is not accessible, but the minecart stays in memory. It does not join the
            // level again when the chunk is accessible again, so it stays in the set.
            return;
        }
        untrack(cart);
        if (reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED) {
            CartLinks.unlinkAll(level, cart, true);
        }
    }

    /**
     * A minecart goes through a portal: its links break and drop the chains before the game copies the minecart to
     * the other level.
     */
    public static void onTravelToDimension(EntityTravelToDimensionEvent event) {
        if (event.getEntity() instanceof AbstractMinecart cart && cart.level() instanceof ServerLevel level) {
            CartLinks.unlinkAll(level, cart, true);
        }
    }

    /** A player starts to track a minecart with a link: the client gets the links to draw the chains. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof AbstractMinecart cart
                && cart.level() instanceof ServerLevel level && !CartLinks.get(cart).isEmpty()) {
            BalanceNetwork.send(player, CartLinks.payload(level, cart));
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LINKED.clear();
        STRETCH_TICKS.clear();
    }
}
