package io.github.nicolasgoyon.folkhold.balance.client;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * The chain links that the client knows, from the payloads of the server: for each minecart with a back link, the
 * entity id of the minecart behind it. This class has no client types: the network handler refers to it on the two
 * sides.
 */
public final class ClientCartLinks {
    /** The entity id of the front minecart to the entity id of the back minecart. */
    private static final Map<Integer, Integer> BACK_OF = new HashMap<>();

    private ClientCartLinks() {
    }

    /**
     * Sets the links of one minecart.
     *
     * @param cartId  the entity id of the minecart
     * @param frontId the entity id of the minecart in front, or -1
     * @param backId  the entity id of the minecart behind, or -1
     */
    public static void set(int cartId, int frontId, int backId) {
        BACK_OF.remove(cartId);
        if (backId >= 0) {
            BACK_OF.put(cartId, backId);
        }
        BACK_OF.values().removeIf(id -> id == cartId);
        if (frontId >= 0) {
            BACK_OF.put(frontId, cartId);
        }
    }

    /**
     * Calls an action for each link, and removes the links for which the action returns false.
     *
     * @param action gets the entity id of the front minecart and of the back minecart. It returns false when the
     *               client does not have one of the two minecarts.
     */
    public static void forEach(BiPredicate<Integer, Integer> action) {
        BACK_OF.entrySet().removeIf(entry -> !action.test(entry.getKey(), entry.getValue()));
    }

    /** Forgets all links. */
    public static void clear() {
        BACK_OF.clear();
    }
}
