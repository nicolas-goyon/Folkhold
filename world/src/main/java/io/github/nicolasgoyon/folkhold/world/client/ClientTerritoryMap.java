package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;

/**
 * The last discovered territories that the server sent to this client (see {@link TerritoryMapPayload}). The maps
 * show them: the maps of Xaero's mods (see {@link io.github.nicolasgoyon.folkhold.world.client.map.XaeroMaps}).
 */
public final class ClientTerritoryMap {
    private static volatile TerritoryMapView view = TerritoryMapView.EMPTY;

    private ClientTerritoryMap() {
    }

    /** The current view of the territories. */
    public static TerritoryMapView get() {
        return view;
    }

    /** Keeps the territories of a payload. The view gets a new number. */
    public static void set(TerritoryMapPayload payload) {
        view = new TerritoryMapView(payload, view.version() + 1);
    }

    /** Forgets the territories, for example when the player leaves the server. */
    public static void clear() {
        view = new TerritoryMapView(TerritoryMapPayload.EMPTY, view.version() + 1);
    }
}
