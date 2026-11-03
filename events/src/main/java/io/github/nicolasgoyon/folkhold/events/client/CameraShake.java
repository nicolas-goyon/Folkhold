package io.github.nicolasgoyon.folkhold.events.client;

import io.github.nicolasgoyon.folkhold.events.config.ClientConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * The camera shake of an earthquake on the client. The server starts it (see
 * {@link io.github.nicolasgoyon.folkhold.events.network.EarthquakeShakePayload}). The camera turns by small angles
 * around the three axes. The shake fades out in its last second. The client config
 * {@link ClientConfig#CAMERA_SHAKE} scales it, and 0 turns it off.
 */
public final class CameraShake {
    /** The shake fades out in this number of ticks at its end. */
    private static final float FADE_TICKS = 20.0F;

    private static float amplitude;
    private static int remaining;
    private static int age;

    private CameraShake() {
    }

    /**
     * Starts a shake. A shake that is stronger or longer than the current shake replaces its values.
     *
     * @param newAmplitude the strength of the shake, in degrees
     * @param ticks        the duration of the shake, in ticks
     */
    public static void start(float newAmplitude, int ticks) {
        amplitude = remaining > 0 ? Math.max(amplitude, newAmplitude) : newAmplitude;
        remaining = Math.max(remaining, ticks);
    }

    /** Stops the shake. */
    public static void clear() {
        amplitude = 0.0F;
        remaining = 0;
        age = 0;
    }

    static void onClientTick(ClientTickEvent.Post event) {
        if (remaining > 0 && !Minecraft.getInstance().isPaused()) {
            remaining--;
            age++;
        }
    }

    static void onCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        if (remaining <= 0) {
            return;
        }
        float scale = (float) (double) ClientConfig.CAMERA_SHAKE.get();
        float strength = amplitude * scale * Math.min(1.0F, remaining / FADE_TICKS);
        if (strength <= 0.0F) {
            return;
        }
        float time = age + (float) event.getPartialTick();
        // Three waves of different frequencies on each axis: the motion does not look regular.
        event.setYaw(event.getYaw() + strength * 0.5F * (Mth.sin(time * 1.7F) + 0.5F * Mth.sin(time * 4.1F)));
        event.setPitch(event.getPitch() + strength * 0.7F * (Mth.sin(time * 2.3F + 1.0F)
                + 0.5F * Mth.sin(time * 5.3F)));
        event.setRoll(event.getRoll() + strength * 0.4F * Mth.sin(time * 1.3F + 2.0F));
    }
}
