package io.github.nicolasgoyon.folkhold.world.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import org.lwjgl.glfw.GLFW;

/** The key of the journal of the player (see {@link JournalScreen}). */
public final class JournalKeys {
    private static final String CATEGORY = "key.categories.folkhold_world";

    /** Opens the journal. */
    public static final KeyMapping OPEN = new KeyMapping("key.folkhold_world.journal", KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_K, CATEGORY);

    private JournalKeys() {
    }

    public static void register(RegisterKeyMappingsEvent event) {
        event.register(OPEN);
    }
}
