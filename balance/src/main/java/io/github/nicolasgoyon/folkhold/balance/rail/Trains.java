package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.config.BalanceConfig;
import io.github.nicolasgoyon.folkhold.balance.network.BalanceNetwork;
import io.github.nicolasgoyon.folkhold.balance.registry.ModAttachments;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.SectionPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.MinecartFurnace;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.properties.RailShape;
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
 * {@link LevelTickEvent.Post}: it finds each train one time, then corrects the motion of each minecart in order from
 * the driver of the train (see {@link #driver}) to the two ends of the train. The correction of a minecart uses the
 * corrected motion of the next minecart toward the driver, so a long train has no delay of one tick for each
 * minecart.
 *
 * <p>A train stops as one unit when one of its minecarts is not in a chunk that ticks its entities, or is not loaded.
 * Its links wait, and the break counters do not increase. A link breaks when a minecart is destroyed or goes through
 * a portal, or when the chain stays too long above its break gap (see {@link Coupling}).
 */
public final class Trains {
    /** The minecarts with a link in each level that are loaded. */
    private static final Map<ResourceKey<Level>, Set<AbstractMinecart>> LINKED = new HashMap<>();
    /**
     * The break counter of each link whose chain is above the break gap (see {@link #pull}). The key is the UUID of
     * the back minecart of the link: it does not change when the driver of the train changes.
     */
    private static final Map<UUID, Integer> STRETCH_TICKS = new HashMap<>();
    /** The motion of each linked minecart after the correction of the chains in the last tick ({@link TrainPush}). */
    private static final Map<UUID, Vec3> LAST_MOTION = new HashMap<>();
    /** The minecarts that drove their trains in the last tick. */
    private static final Set<UUID> LAST_DRIVERS = new HashSet<>();
    /** The links that the last check found dead: the UUID of a minecart to the UUIDs of its missing partners. */
    private static final Map<UUID, Set<UUID>> DEAD_LINKS = new HashMap<>();
    /** The players that track each minecart. */
    private static final Map<AbstractMinecart, Set<ServerPlayer>> TRACKERS = new HashMap<>();
    /** A horizontal distance below this value gives no direction to the chain, in blocks. */
    private static final double MIN_HORIZONTAL_DISTANCE = 1.0E-4;
    /** The chain moves a minecart on a rail only when the cosine between the chain and the rail is at least this. */
    private static final double MIN_RAIL_ALIGNMENT = 0.2;
    /** The parts of a diagonal unit vector. */
    private static final double DIAGONAL = Math.sqrt(0.5);
    /** A vanilla furnace minecart with a push of this squared length or less does not drive (vanilla value). */
    private static final double MIN_FURNACE_PUSH_SQR = 1.0E-7;
    /** The interval of the check of the dead links, in ticks. */
    private static final int DEAD_LINK_CHECK_TICKS = 20;
    /** The margin around the largest distance of two linked minecarts for the check of the dead links, in blocks. */
    private static final double DEAD_LINK_MARGIN = 2.0;

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
        LAST_MOTION.remove(cart.getUUID());
        LAST_DRIVERS.remove(cart.getUUID());
    }

    /**
     * Forgets the break counter of a link.
     *
     * @param back the UUID of the back minecart of the link
     */
    static void forgetStretch(UUID back) {
        STRETCH_TICKS.remove(back);
    }

    /** The players that track a minecart. */
    static Set<ServerPlayer> trackers(AbstractMinecart cart) {
        return TRACKERS.getOrDefault(cart, Set.of());
    }

    /**
     * Tells if the level ticks a minecart: it is in the level and in a chunk that ticks its entities. The level also
     * gives the minecarts of the chunks at the edge of the simulation distance, and these minecarts do not move.
     */
    static boolean isActive(ServerLevel level, AbstractMinecart cart) {
        return !cart.isRemoved() && level.getEntity(cart.getUUID()) == cart
                && level.isPositionEntityTicking(cart.blockPosition());
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
        if (level.getGameTime() % DEAD_LINK_CHECK_TICKS == 0) {
            checkDeadLinks(level, carts, coupling.breakGap() + 2.0 * CartEnds.HALF_LENGTH + DEAD_LINK_MARGIN);
        }
        int maxCarts = BalanceConfig.TRAIN_MAX_CARTS.get();
        Set<AbstractMinecart> done = Collections.newSetFromMap(new IdentityHashMap<>());
        // A copy in a stable order: a link that breaks changes the set.
        for (AbstractMinecart cart : List.copyOf(new LinkedHashSet<>(carts))) {
            // A minecart in a chunk that is not accessible stays in the set, but the level does not give it.
            if (!done.contains(cart) && !cart.isRemoved() && level.getEntity(cart.getUUID()) == cart
                    && isFront(level, cart)) {
                moveTrain(level, cart, coupling, maxCarts, done);
            }
        }
    }

    /**
     * Tells if a minecart is the front minecart of the part of its train that the level gives: it has no front link,
     * or the level does not give the minecart in front. A front link that the other minecart does not have is
     * removed.
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
            removeOneSided(level, cart, frontId.get());
            return true;
        }
        return false;
    }

    /**
     * Collects the train from its front minecart, then moves it. The train stops when a link waits for a minecart.
     */
    private static void moveTrain(ServerLevel level, AbstractMinecart head, Coupling coupling, int maxCarts,
                                  Set<AbstractMinecart> done) {
        List<AbstractMinecart> train = new ArrayList<>();
        train.add(head);
        done.add(head);
        // After isFront, a front link that stays is a link to a minecart that the level does not give.
        boolean waiting = CartLinks.get(head).front().isPresent();
        AbstractMinecart current = head;
        while (true) {
            Optional<UUID> backId = CartLinks.get(current).back();
            if (backId.isEmpty()) {
                break;
            }
            if (train.size() >= maxCarts) {
                // The maximum number of minecarts is less than the size of the train.
                CartLinks.unlink(level, current, backId.get(), true);
                break;
            }
            Optional<AbstractMinecart> found = CartLinks.find(level, backId.get());
            if (found.isEmpty()) {
                waiting = true;
                break;
            }
            AbstractMinecart back = found.get();
            if (done.contains(back) || !CartLinks.get(back).front().equals(Optional.of(current.getUUID()))) {
                removeOneSided(level, current, backId.get());
                break;
            }
            train.add(back);
            done.add(back);
            current = back;
        }
        if (waiting || !train.stream().allMatch(cart -> isActive(level, cart))) {
            stop(level, train);
        } else {
            int driver = driver(train);
            train.forEach(cart -> LAST_DRIVERS.remove(cart.getUUID()));
            LAST_DRIVERS.add(train.get(driver).getUUID());
            // A broken link makes new trains: they move in the next tick. The part on the side of the driver stays
            // valid. The key of the counter of a link is its back minecart.
            for (int i = driver; i > 0; i--) {
                if (pull(level, coupling, train.get(i), train.get(i - 1), train.get(i).getUUID())) {
                    break;
                }
            }
            for (int i = driver; i + 1 < train.size(); i++) {
                if (pull(level, coupling, train.get(i), train.get(i + 1), train.get(i + 1).getUUID())) {
                    break;
                }
            }
        }
        for (AbstractMinecart cart : train) {
            LAST_MOTION.put(cart.getUUID(), cart.getDeltaMovement());
        }
    }

    /**
     * The index of the driver of a train: the minecart that the chains do not move. The chains move the other
     * minecarts toward the driver or away from it.
     *
     * <ul>
     *   <li>A locomotive at the front, or a vanilla furnace minecart with a push at the front: the front
     *       minecart.</li>
     *   <li>If not, the first vanilla furnace minecart with a push. A vanilla furnace minecart moves in the direction
     *       of its push and has no front end: the player cannot see if it is in front of its wagon or behind it.</li>
     *   <li>If not, the minecart that gets a push in this tick (see {@link TrainPush}): a player push, a powered rail,
     *       a slope or a rider.</li>
     * </ul>
     */
    private static int driver(List<AbstractMinecart> train) {
        AbstractMinecart head = train.get(0);
        if (head instanceof Locomotive || isPushed(head)) {
            return 0;
        }
        for (int i = 1; i < train.size(); i++) {
            if (isPushed(train.get(i))) {
                return i;
            }
        }
        double[] gains = new double[train.size()];
        int lastDriver = -1;
        for (int i = 0; i < train.size(); i++) {
            AbstractMinecart cart = train.get(i);
            Vec3 last = LAST_MOTION.getOrDefault(cart.getUUID(), Vec3.ZERO);
            gains[i] = cart.getDeltaMovement().horizontalDistance() - last.horizontalDistance();
            if (lastDriver < 0 && LAST_DRIVERS.contains(cart.getUUID())) {
                lastDriver = i;
            }
        }
        return TrainPush.driver(gains, lastDriver);
    }

    /** Tells if a minecart is a vanilla furnace minecart with a push: it has fuel and moves by itself. */
    private static boolean isPushed(AbstractMinecart cart) {
        return cart instanceof MinecartFurnace furnace
                && furnace.xPush * furnace.xPush + furnace.zPush * furnace.zPush > MIN_FURNACE_PUSH_SQR;
    }

    /**
     * Removes a link that the other minecart does not have. No chain drops: each link drops its chain when it breaks,
     * so a link that only one minecart has owns no chain.
     */
    private static void removeOneSided(ServerLevel level, AbstractMinecart cart, UUID partner) {
        CartLinks.set(cart, CartLinks.get(cart).without(partner));
        CartLinks.sync(level, cart);
    }

    /**
     * Removes the dead links: links to a minecart that does not exist any more. Two linked minecarts are never much
     * farther apart than the break gap: a train that waits does not move. Thus a link is dead when its minecart
     * ticks, all the chunks in this radius around it have their entities loaded and tick them, and the level does not
     * give the other minecart. The entities of a chunk load after the chunk: a link must be dead in 2 checks in a
     * row. No chain drops.
     *
     * @param radius the radius of the area to check, in blocks
     */
    private static void checkDeadLinks(ServerLevel level, Set<AbstractMinecart> carts, double radius) {
        Map<UUID, Set<UUID>> found = new HashMap<>();
        for (AbstractMinecart cart : List.copyOf(carts)) {
            if (!isActive(level, cart)) {
                continue;
            }
            CartLink link = CartLinks.get(cart);
            for (UUID partner : List.of(link.front(), link.back()).stream().flatMap(Optional::stream).toList()) {
                if (level.getEntity(partner) != null || !isAreaLoaded(level, cart, radius)) {
                    continue;
                }
                if (DEAD_LINKS.getOrDefault(cart.getUUID(), Set.of()).contains(partner)) {
                    removeOneSided(level, cart, partner);
                } else {
                    found.computeIfAbsent(cart.getUUID(), id -> new HashSet<>()).add(partner);
                }
            }
        }
        DEAD_LINKS.clear();
        DEAD_LINKS.putAll(found);
    }

    /** Tells if all the chunks in a radius around a minecart have their entities loaded and tick them. */
    private static boolean isAreaLoaded(ServerLevel level, AbstractMinecart cart, double radius) {
        int minX = SectionPos.blockToSectionCoord(cart.getX() - radius);
        int maxX = SectionPos.blockToSectionCoord(cart.getX() + radius);
        int minZ = SectionPos.blockToSectionCoord(cart.getZ() - radius);
        int maxZ = SectionPos.blockToSectionCoord(cart.getZ() + radius);
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z))
                        || !level.isPositionEntityTicking(new ChunkPos(x, z).getWorldPosition())) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Stops a train that waits. Each minecart that ticks goes back to its position before this tick, and its motion is
     * zero: no slope, powered rail or rider moves it. The break counters do not increase. A furnace minecart keeps its
     * push.
     */
    private static void stop(ServerLevel level, List<AbstractMinecart> train) {
        for (AbstractMinecart cart : train) {
            STRETCH_TICKS.remove(cart.getUUID());
            if (isActive(level, cart)) {
                cart.setPos(cart.xo, cart.yo, cart.zo);
                cart.setDeltaMovement(Vec3.ZERO);
            }
        }
    }

    /**
     * Applies the chain between two linked minecarts for one tick. The chain changes only the motion of the follower.
     * The leader is the minecart nearer to the driver of the train, and it has its motion for the next tick.
     *
     * @param linkKey the key of the break counter of the link: the UUID of the back minecart of the link
     * @return true if the link breaks
     */
    private static boolean pull(ServerLevel level, Coupling coupling, AbstractMinecart leader,
                                AbstractMinecart follower, UUID linkKey) {
        double dx = leader.getX() - follower.getX();
        double dz = leader.getZ() - follower.getZ();
        double gap = CartEnds.gap(follower, leader);
        int ticks = coupling.nextStretchTicks(STRETCH_TICKS.getOrDefault(linkKey, 0), gap);
        if (coupling.breaks(ticks)) {
            CartLinks.unlink(level, follower, leader.getUUID(), true);
            return true;
        }
        if (ticks > 0) {
            STRETCH_TICKS.put(linkKey, ticks);
        } else {
            STRETCH_TICKS.remove(linkKey);
        }
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (horizontal < MIN_HORIZONTAL_DISTANCE) {
            return false;
        }
        double nx = dx / horizontal;
        double nz = dz / horizontal;
        double impulse = coupling.impulse(gap, nextSpeed(leader, nx, nz) - currentSpeed(follower, nx, nz));
        if (impulse != 0.0) {
            accelerate(follower, nx, nz, impulse);
        }
        return false;
    }

    /**
     * The speed of a minecart in the next tick along a direction: its motion, limited to the speed limit of its rail.
     * The motion of a furnace minecart is much more than its speed, so the limit is necessary.
     */
    private static double nextSpeed(AbstractMinecart cart, double nx, double nz) {
        Vec3 motion = cart.getDeltaMovement();
        double limit = cart.getMaxSpeedWithRail();
        return Math.clamp(motion.x * nx + motion.z * nz, -limit, limit);
    }

    /** The speed of the movement of a minecart in this tick along a direction. */
    private static double currentSpeed(AbstractMinecart cart, double nx, double nz) {
        return (cart.getX() - cart.xo) * nx + (cart.getZ() - cart.zo) * nz;
    }

    /**
     * Gives a speed change to the follower of a link. On a rail, the change goes along the rail, in the direction
     * of the chain. The new speed is the speed of the movement of this tick plus the change: the motion of a minecart
     * does not grow above its real speed. The motion also stays below the speed limit of the rail, so that a minecart
     * does not leave a curve.
     *
     * @param nx      the X part of the horizontal direction from the follower to the leader
     * @param nz      the Z part of the horizontal direction from the follower to the leader
     * @param impulse the speed change toward the leader, in blocks per tick. A negative value moves the follower
     *                away from the leader.
     */
    private static void accelerate(AbstractMinecart cart, double nx, double nz, double impulse) {
        double dirX = nx;
        double dirZ = nz;
        double[] axis = railAxis(cart);
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
        double speed = currentSpeed(cart, dirX, dirZ);
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
    private static double[] railAxis(AbstractMinecart cart) {
        RailShape shape = cart.isOnRails() ? CartEnds.railShape(cart, cart.position()) : null;
        if (shape == null) {
            return null;
        }
        return switch (shape) {
            case NORTH_SOUTH, ASCENDING_NORTH, ASCENDING_SOUTH -> new double[] {0.0, 1.0};
            case EAST_WEST, ASCENDING_EAST, ASCENDING_WEST -> new double[] {1.0, 0.0};
            case SOUTH_EAST, NORTH_WEST -> new double[] {DIAGONAL, -DIAGONAL};
            case SOUTH_WEST, NORTH_EAST -> new double[] {DIAGONAL, DIAGONAL};
        };
    }

    /** A minecart with a link loads: the mod moves it. */
    public static void onEntityJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel && event.getEntity() instanceof AbstractMinecart cart
                && cart.hasData(ModAttachments.CART_LINK)) {
            track(cart);
        }
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

    /** A player starts to track a minecart. If the minecart has a link, the client gets it to draw the chain. */
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getTarget() instanceof AbstractMinecart cart
                && cart.level() instanceof ServerLevel level) {
            TRACKERS.computeIfAbsent(cart, key -> Collections.newSetFromMap(new IdentityHashMap<>())).add(player);
            if (!CartLinks.get(cart).isEmpty()) {
                BalanceNetwork.send(player, CartLinks.payload(level, cart));
            }
        }
    }

    /** A player stops to track a minecart. The game also sends this event when the player or the minecart leaves. */
    public static void onStopTracking(PlayerEvent.StopTracking event) {
        if (event.getTarget() instanceof AbstractMinecart cart) {
            Set<ServerPlayer> players = TRACKERS.get(cart);
            if (players != null) {
                players.remove(event.getEntity());
                if (players.isEmpty()) {
                    TRACKERS.remove(cart);
                }
            }
        }
    }

    public static void onServerStopped(ServerStoppedEvent event) {
        LINKED.clear();
        STRETCH_TICKS.clear();
        LAST_MOTION.clear();
        LAST_DRIVERS.clear();
        DEAD_LINKS.clear();
        TRACKERS.clear();
    }
}
