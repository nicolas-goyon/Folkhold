package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.MapTerritoryPayload;
import io.github.nicolasgoyon.folkhold.world.network.TerritoryMapPayload;
import net.minecraft.client.Minecraft;

/**
 * The territories in the areas of the vanilla maps near the player, that the server sent to this client (see
 * {@link MapTerritoryPayload}). The vanilla maps show them (see
 * {@link io.github.nicolasgoyon.folkhold.world.client.map.VanillaMapOverlay}). A new payload draws the textures of the
 * maps again.
 */
public final class ClientMapTerritories {
    private static volatile TerritoryMapView view = TerritoryMapView.EMPTY;

    private ClientMapTerritories() {
    }

    /** The current territories. */
    public static TerritoryMapView get() {
        return view;
    }

    /** Keeps the territories of a payload, and draws the maps again. */
    public static void set(MapTerritoryPayload payload) {
        view = new TerritoryMapView(payload.territories(), view.version() + 1);
        Minecraft.getInstance().gameRenderer.getMapRenderer().resetData();
    }

    /** Forgets the territories, for example when the player leaves the server. */
    public static void clear() {
        view = new TerritoryMapView(TerritoryMapPayload.EMPTY, view.version() + 1);
    }
}
