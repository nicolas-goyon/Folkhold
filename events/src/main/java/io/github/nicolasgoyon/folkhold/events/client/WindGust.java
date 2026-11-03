package io.github.nicolasgoyon.folkhold.events.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

/**
 * The push of a gust of a storm on the player of the client. The server sends it (see
 * {@link io.github.nicolasgoyon.folkhold.events.network.WindGustPayload}).
 */
public final class WindGust {
    private WindGust() {
    }

    /**
     * Adds a push to the motion of the player.
     *
     * @param x the push along X, in blocks per tick
     * @param y the push along Y, in blocks per tick
     * @param z the push along Z, in blocks per tick
     */
    public static void push(float x, float y, float z) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && !player.isSpectator() && !player.getAbilities().flying) {
            player.push(x, y, z);
        }
    }
}
