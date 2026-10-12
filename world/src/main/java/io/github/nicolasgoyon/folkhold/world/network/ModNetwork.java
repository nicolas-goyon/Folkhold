package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.client.ClientTerritory;
import io.github.nicolasgoyon.folkhold.world.client.VillageHeartScreen;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the world mod. The handlers of the client payloads run only on the client. The lambdas
 * refer to the client classes only in their bodies, so this class also loads on a dedicated server.
 */
public final class ModNetwork {
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TerritoryStatusPayload.TYPE, TerritoryStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientTerritory.set(payload));
        registrar.playToClient(VillageInfoPayload.TYPE, VillageInfoPayload.STREAM_CODEC,
                (payload, context) -> VillageHeartScreen.open(payload));
    }
}
