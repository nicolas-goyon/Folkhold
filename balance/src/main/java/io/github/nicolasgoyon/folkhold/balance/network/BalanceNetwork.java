package io.github.nicolasgoyon.folkhold.balance.network;

import io.github.nicolasgoyon.folkhold.balance.client.ClientCartLinks;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network payloads of the balance mod. The handlers of the client payloads run only on the client.
 * {@link ClientCartLinks} has no client types, so this class also loads on a dedicated server.
 */
public final class BalanceNetwork {
    private static final String VERSION = "1";

    private BalanceNetwork() {
    }

    /**
     * Sends a payload to a player. A connection with no channel for the payload (a mock player of the GameTests)
     * gets nothing: the payload is dropped.
     *
     * @param player  the player
     * @param payload the payload
     * @return true if the payload is sent, false if the connection has no channel for it
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
        registrar.playToClient(CartLinkPayload.TYPE, CartLinkPayload.STREAM_CODEC,
                (payload, context) -> ClientCartLinks.set(payload.cartId(), payload.frontId(), payload.backId()));
    }
}
