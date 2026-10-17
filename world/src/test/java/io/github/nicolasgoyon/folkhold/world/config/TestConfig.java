package io.github.nicolasgoyon.folkhold.world.config;

import net.neoforged.fml.config.ConfigTracker;

/**
 * Loads the server config of the mods in a JUnit test. The game loads the server config of a mod when a world
 * starts. A test with no world loads the default values with the config tracker of the loader, as a client does
 * when it joins a server that does not send its config. The test then reads the values with {@code get()} and
 * changes them with {@code set(...)}.
 */
public final class TestConfig {
    private TestConfig() {
    }

    /** Loads the world config with its default values, one time. */
    public static void loadWorldConfig() {
        if (!WorldConfig.SPEC.isLoaded()) {
            ConfigTracker.INSTANCE.loadDefaultServerConfigs();
        }
    }
}
