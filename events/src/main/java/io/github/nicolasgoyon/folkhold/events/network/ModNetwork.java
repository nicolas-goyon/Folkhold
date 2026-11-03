package io.github.nicolasgoyon.folkhold.events.network;

import io.github.nicolasgoyon.folkhold.events.client.CameraShake;
import io.github.nicolasgoyon.folkhold.events.client.WindGust;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the events mod. The handlers of the client payloads run only on the client. The lambdas refer
 * to the client classes only in their bodies, so this class also loads on a dedicated server.
 */
public final class ModNetwork {
    private static final String VERSION = "2";

    private ModNetwork() {
    }

    /**
     * Sends a payload to a player. A connection with no channel for the payload (a mock player of the GameTests)
     * gets nothing.
     *
     * @param player  the player
     * @param payload the payload
     * @return true if the payload is sent
     */
    public static boolean send(ServerPlayer player, CustomPacketPayload payload) {
        if (player.connection == null || !player.connection.hasChannel(payload)) {
            return false;
        }
        PacketDistributor.sendToPlayer(player, payload);
        return true;
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(VERSION);
        registrar.playToClient(EarthquakeShakePayload.TYPE, EarthquakeShakePayload.STREAM_CODEC,
                (payload, context) -> CameraShake.start(payload.amplitude(), payload.ticks()));
        registrar.playToClient(WindGustPayload.TYPE, WindGustPayload.STREAM_CODEC,
                (payload, context) -> WindGust.push(payload.x(), payload.y(), payload.z()));
    }
}
