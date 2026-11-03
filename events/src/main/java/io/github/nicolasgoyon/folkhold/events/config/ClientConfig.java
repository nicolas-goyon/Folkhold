package io.github.nicolasgoyon.folkhold.events.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Client configuration of the events mod: display options of each player. */
public final class ClientConfig {
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.DoubleValue CAMERA_SHAKE;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        CAMERA_SHAKE = builder
                .comment("Strength of the camera shake during an earthquake. 1 is the full shake. 0 turns off the",
                        "shake.")
                .translation(EventsConfig.KEY_PREFIX + "cameraShake")
                .defineInRange("cameraShake", 1.0, 0.0, 1.0);
        SPEC = builder.build();
    }

    private ClientConfig() {
    }
}
