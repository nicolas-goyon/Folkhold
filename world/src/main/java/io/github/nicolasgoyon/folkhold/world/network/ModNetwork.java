package io.github.nicolasgoyon.folkhold.world.network;

import io.github.nicolasgoyon.folkhold.world.client.ClientTerritory;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the world mod. The client handlers only store the data: they do not use client classes,
 * so this class also loads on a dedicated server.
 */
public final class ModNetwork {
    private static final String VERSION = "1";

    private ModNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(TerritoryStatusPayload.TYPE, TerritoryStatusPayload.STREAM_CODEC,
                (payload, context) -> ClientTerritory.set(payload));
    }
}
