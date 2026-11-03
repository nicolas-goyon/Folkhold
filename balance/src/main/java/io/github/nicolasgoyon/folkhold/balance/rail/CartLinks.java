package io.github.nicolasgoyon.folkhold.balance.rail;

import io.github.nicolasgoyon.folkhold.balance.network.BalanceNetwork;
import io.github.nicolasgoyon.folkhold.balance.network.CartLinkPayload;
import io.github.nicolasgoyon.folkhold.balance.registry.ModAttachments;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

/**
 * The chain links between minecarts, on the server. The links are a data attachment of each minecart (see
 * {@link CartLink}). The two minecarts of a link refer to each other.
 *
 * <p>A train is a sequence of linked minecarts. The front minecart of a train has no front link. A train has no loop
 * and has a maximum number of minecarts.
 */
public final class CartLinks {

    /** The result of an attempt to link two minecarts. */
    public enum LinkResult {
        LINKED,
        SAME_CART,
        TOO_FAR,
        NO_FREE_END,
        LOOP,
        TOO_LONG,
        NOT_LOADED;

        /**
         * The message for the player.
         *
         * @param maxCarts the largest number of minecarts in a train
         */
        public Component message(int maxCarts) {
            String key = "folkhold_balance.chain." + name().toLowerCase(Locale.ROOT);
            return this == TOO_LONG ? Component.translatable(key, maxCarts) : Component.translatable(key);
        }
    }

    private CartLinks() {
    }

    /** The links of a minecart. */
    public static CartLink get(AbstractMinecart cart) {
        return cart.getData(ModAttachments.CART_LINK);
    }

    /** Sets the links of a minecart. A minecart with no link loses its attachment. */
    static void set(AbstractMinecart cart, CartLink link) {
        if (link.isEmpty()) {
            cart.removeData(ModAttachments.CART_LINK);
            Trains.untrack(cart);
        } else {
            cart.setData(ModAttachments.CART_LINK, link);
            Trains.track(cart);
        }
    }

    /** Tells if two minecarts are linked to each other. */
    public static boolean areLinked(AbstractMinecart cart, AbstractMinecart other) {
        if (!cart.hasData(ModAttachments.CART_LINK)) {
            return false;
        }
        CartLink link = get(cart);
        UUID id = other.getUUID();
        return link.front().filter(id::equals).isPresent() || link.back().filter(id::equals).isPresent();
    }

    /**
     * The minecart with a UUID, if it is loaded in the level and alive.
     *
     * @param level the level
     * @param id    the UUID of the minecart
     * @return the minecart, or empty
     */
    public static Optional<AbstractMinecart> find(ServerLevel level, UUID id) {
        if (level.getEntity(id) instanceof AbstractMinecart cart && cart.isAlive()) {
            return Optional.of(cart);
        }
        return Optional.empty();
    }

    /**
     * Links two minecarts with a chain. The first minecart goes in front when its back end and the front end of the
     * second minecart are free. Else the second minecart goes in front, if its back end and the front end of the
     * first minecart are free.
     *
     * @param level       the level of the two minecarts
     * @param first       the first minecart
     * @param second      the second minecart
     * @param maxDistance the largest distance between the two minecarts, in blocks
     * @param maxCarts    the largest number of minecarts in a train
     * @return {@link LinkResult#LINKED}, or the reason of the failure
     */
    public static LinkResult link(ServerLevel level, AbstractMinecart first, AbstractMinecart second,
                                  double maxDistance, int maxCarts) {
        if (first == second) {
            return LinkResult.SAME_CART;
        }
        if (first.level() != level || second.level() != level || first.distanceTo(second) > maxDistance) {
            return LinkResult.TOO_FAR;
        }
        CartLink firstLink = get(first);
        CartLink secondLink = get(second);
        AbstractMinecart front;
        AbstractMinecart back;
        if (firstLink.back().isEmpty() && secondLink.front().isEmpty()) {
            front = first;
            back = second;
        } else if (firstLink.front().isEmpty() && secondLink.back().isEmpty()) {
            front = second;
            back = first;
        } else {
            return LinkResult.NO_FREE_END;
        }
        // The front minecart is the last minecart of its train, the back minecart is the first minecart of its train.
        Optional<List<AbstractMinecart>> ahead = walk(level, front, true, maxCarts);
        Optional<List<AbstractMinecart>> behind = walk(level, back, false, maxCarts);
        if (ahead.isEmpty() || behind.isEmpty()) {
            return LinkResult.NOT_LOADED;
        }
        if (ahead.get().contains(back) || behind.get().contains(front)) {
            return LinkResult.LOOP;
        }
        if (ahead.get().size() + behind.get().size() > maxCarts) {
            return LinkResult.TOO_LONG;
        }
        set(front, get(front).withBack(Optional.of(back.getUUID())));
        set(back, get(back).withFront(Optional.of(front.getUUID())));
        sync(level, front);
        sync(level, back);
        return LinkResult.LINKED;
    }

    /**
     * The minecarts from a minecart to the end of its train, in one direction.
     *
     * @param level   the level
     * @param start   the first minecart of the list
     * @param forward true to follow the front links, false to follow the back links
     * @param limit   the walk stops after this number of minecarts
     * @return the minecarts, the start first, or empty if a minecart of the walk is not loaded
     */
    static Optional<List<AbstractMinecart>> walk(ServerLevel level, AbstractMinecart start, boolean forward,
                                                 int limit) {
        List<AbstractMinecart> carts = new ArrayList<>();
        carts.add(start);
        AbstractMinecart current = start;
        while (carts.size() <= limit) {
            CartLink link = get(current);
            Optional<UUID> next = forward ? link.front() : link.back();
            if (next.isEmpty()) {
                break;
            }
            Optional<AbstractMinecart> cart = find(level, next.get());
            if (cart.isEmpty()) {
                return Optional.empty();
            }
            if (carts.contains(cart.get())) {
                break;
            }
            carts.add(cart.get());
            current = cart.get();
        }
        return Optional.of(carts);
    }

    /**
     * Removes the link between a minecart and another minecart. When the other minecart is not loaded, it forgets
     * the link when it loads again (see {@link StaleLinks}).
     *
     * @param level     the level of the minecart
     * @param cart      the minecart
     * @param partner   the UUID of the other minecart
     * @param dropChain true to drop a chain at the middle of the link, or at the minecart when the other minecart is
     *                  not loaded
     */
    public static void unlink(ServerLevel level, AbstractMinecart cart, UUID partner, boolean dropChain) {
        set(cart, get(cart).without(partner));
        Trains.forgetStretch(cart, partner);
        Optional<AbstractMinecart> other = find(level, partner);
        Vec3 drop = cart.position();
        if (other.isPresent()) {
            set(other.get(), get(other.get()).without(cart.getUUID()));
            drop = drop.add(other.get().position()).scale(0.5);
            sync(level, other.get());
        } else {
            StaleLinks.get(level.getServer()).add(partner, cart.getUUID());
        }
        if (!cart.isRemoved()) {
            sync(level, cart);
        }
        if (dropChain) {
            ItemEntity item = new ItemEntity(level, drop.x, drop.y + 0.5, drop.z, new ItemStack(Items.CHAIN));
            item.setDefaultPickUpDelay();
            level.addFreshEntity(item);
        }
    }

    /**
     * Removes all links of a minecart.
     *
     * @param level     the level of the minecart
     * @param cart      the minecart
     * @param dropChain true to drop a chain for each link
     */
    public static void unlinkAll(ServerLevel level, AbstractMinecart cart, boolean dropChain) {
        CartLink link = get(cart);
        link.front().ifPresent(id -> unlink(level, cart, id, dropChain));
        link.back().ifPresent(id -> unlink(level, cart, id, dropChain));
    }

    /** The payload with the links of a minecart. */
    static CartLinkPayload payload(ServerLevel level, AbstractMinecart cart) {
        CartLink link = get(cart);
        return new CartLinkPayload(cart.getId(), entityId(level, link.front()), entityId(level, link.back()));
    }

    private static int entityId(ServerLevel level, Optional<UUID> id) {
        return id.flatMap(uuid -> find(level, uuid)).map(AbstractMinecart::getId).orElse(CartLinkPayload.NONE);
    }

    /**
     * Sends the links of a minecart to the players of its level. A player that does not track the minecart forgets
     * the link when its client does not find the minecart.
     */
    static void sync(ServerLevel level, AbstractMinecart cart) {
        CartLinkPayload payload = payload(level, cart);
        for (ServerPlayer player : level.players()) {
            BalanceNetwork.send(player, payload);
        }
    }
}
