package io.github.nicolasgoyon.folkhold.world.client;

import io.github.nicolasgoyon.folkhold.world.network.MapReliefPayload;
import io.github.nicolasgoyon.folkhold.world.network.MapReliefRequestPayload;
import io.github.nicolasgoyon.folkhold.world.network.RoutePayload;
import javax.annotation.Nullable;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The map of the journal on the client: the relief that the server sends (see
 * {@link io.github.nicolasgoyon.folkhold.world.journal.TerritoryReliefs}), and the chunks of the discovered
 * territories (see {@link TerritoryRaster}).
 *
 * <ul>
 *   <li>While the journal shows the map, the client asks for the relief when the territories change.</li>
 *   <li>While the server calculates the relief, the client asks again every {@link #POLL_TICKS} ticks.</li>
 *   <li>The client keeps the last relief: the map shows it until the new relief comes.</li>
 * </ul>
 */
public final class ClientJournalMap {
    /** The interval between two requests while the server calculates the relief, in ticks. */
    static final int POLL_TICKS = 100;

    @Nullable
    private static RoutePayload.Relief relief;
    private static MapReliefPayload.Status status = MapReliefPayload.Status.PENDING;
    /** The number of the view of the territories at the last request, or -1 before the first request. */
    private static int requestedVersion = -1;
    private static int pollTimer;
    private static TerritoryRaster raster = TerritoryRaster.EMPTY;

    private ClientJournalMap() {
    }

    /** Keeps the relief of a payload. Called by the network handler. */
    public static void set(MapReliefPayload payload) {
        status = payload.status();
        if (payload.relief() != null) {
            relief = payload.relief();
        } else if (status == MapReliefPayload.Status.EMPTY) {
            relief = null;
        }
    }

    /** The last relief, or null. */
    @Nullable
    static RoutePayload.Relief relief() {
        return relief;
    }

    /** True while the server calculates the relief. */
    static boolean pending() {
        return status == MapReliefPayload.Status.PENDING;
    }

    /** The chunks of the current discovered territories. */
    static TerritoryRaster raster() {
        TerritoryMapView view = ClientTerritoryMap.get();
        if (raster.version() != view.version()) {
            raster = TerritoryRaster.of(view);
        }
        return raster;
    }

    /** Asks for the relief when it is necessary. Called each tick while the journal shows the map. */
    static void tick() {
        int version = ClientTerritoryMap.get().version();
        if (version != requestedVersion) {
            requestedVersion = version;
            request();
        } else if (status == MapReliefPayload.Status.PENDING && --pollTimer <= 0) {
            request();
        }
    }

    /** Forgets the map, for example when the player leaves the server. */
    public static void clear() {
        relief = null;
        status = MapReliefPayload.Status.PENDING;
        requestedVersion = -1;
        pollTimer = 0;
        raster = TerritoryRaster.EMPTY;
    }

    private static void request() {
        pollTimer = POLL_TICKS;
        PacketDistributor.sendToServer(MapReliefRequestPayload.INSTANCE);
    }
}
