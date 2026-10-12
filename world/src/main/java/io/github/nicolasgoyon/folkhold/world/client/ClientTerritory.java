package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.TerritoryStatusPayload;

/**
 * The last territory status that the server sent to this client. The territory HUD shows it.
 *
 * <p>This class does not use client classes: the network registration refers to it on both sides.
 */
public final class ClientTerritory {
    private static TerritoryStatusPayload status = TerritoryStatusPayload.NONE;

    private ClientTerritory() {
    }

    public static TerritoryStatusPayload get() {
        return status;
    }

    public static void set(TerritoryStatusPayload payload) {
        status = payload;
    }

    /** Clears the status, for example when the player leaves the server. */
    public static void clear() {
        status = TerritoryStatusPayload.NONE;
    }
}
